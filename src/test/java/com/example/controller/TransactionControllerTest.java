package com.example.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.HashSet;
import java.util.Set;

import org.hibernate.SessionFactory;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import com.example.domain.TransactionType;
import com.example.dto.CategoryCreateRequest;
import com.example.dto.LoginRequest;
import com.example.dto.SignupRequest;
import com.example.dto.TransactionCreateRequest;
import com.example.dto.TransactionUpdateRequest;

import jakarta.persistence.EntityManagerFactory;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class TransactionControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private EntityManagerFactory entityManagerFactory;

    @Test
    void 거래_생성_후_목록_조회에_카테고리와_함께_내려온다() throws Exception {
        String token = signupAndLogin("txn-list@example.com");
        Long categoryId = categoryId(token, "식비");
        createTransaction(token, categoryId, TransactionType.EXPENSE, "12500", "2026-01-15", "스타벅스", "팀 미팅");

        mockMvc.perform(get("/api/v1/transactions").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].category.name").value("식비"))
                .andExpect(jsonPath("$.data.content[0].category.color").value("#EF4444"))
                .andExpect(jsonPath("$.data.content[0].txnDate").value("2026-01-15"))
                .andExpect(jsonPath("$.data.content[0].amount").value(12500.00))
                .andExpect(jsonPath("$.data.page").value(0))
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.first").value(true))
                .andExpect(jsonPath("$.data.last").value(true));
    }

    @Test
    void Soft_Delete_후_목록에서_제외되고_단건조회는_404가_된다() throws Exception {
        String token = signupAndLogin("txn-delete@example.com");
        Long categoryId = categoryId(token, "식비");
        Long txnId = createTransaction(token, categoryId, TransactionType.EXPENSE, "5000", "2026-01-01", null, null);

        mockMvc.perform(delete("/api/v1/transactions/" + txnId).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/transactions").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(0));

        mockMvc.perform(get("/api/v1/transactions/" + txnId).header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("TRANSACTION_NOT_FOUND"));
    }

    @Test
    void 타_사용자의_거래에_접근하면_404() throws Exception {
        String ownerToken = signupAndLogin("txn-owner@example.com");
        Long categoryId = categoryId(ownerToken, "식비");
        Long txnId = createTransaction(ownerToken, categoryId, TransactionType.EXPENSE, "1000", "2026-01-01", null, null);

        String otherToken = signupAndLogin("txn-other@example.com");

        mockMvc.perform(get("/api/v1/transactions/" + txnId).header("Authorization", "Bearer " + otherToken))
                .andExpect(status().isNotFound());

        mockMvc.perform(put("/api/v1/transactions/" + txnId)
                        .header("Authorization", "Bearer " + otherToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new TransactionUpdateRequest(
                                TransactionType.EXPENSE, new java.math.BigDecimal("999"),
                                java.time.LocalDate.of(2026, 1, 1), categoryId, null, null, null, null))))
                .andExpect(status().isNotFound());

        mockMvc.perform(delete("/api/v1/transactions/" + txnId).header("Authorization", "Bearer " + otherToken))
                .andExpect(status().isNotFound());
    }

    @Test
    void 지출_카테고리에_수입_거래_생성_시_400_CATEGORY_TYPE_MISMATCH() throws Exception {
        String token = signupAndLogin("txn-mismatch@example.com");
        Long expenseCategoryId = categoryId(token, "식비");

        mockMvc.perform(post("/api/v1/transactions")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new TransactionCreateRequest(
                                TransactionType.INCOME, new java.math.BigDecimal("10000"),
                                java.time.LocalDate.of(2026, 1, 1), expenseCategoryId, null, null, null, null))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("CATEGORY_TYPE_MISMATCH"));
    }

    @Test
    void amount가_0_또는_음수면_400을_반환한다() throws Exception {
        String token = signupAndLogin("txn-amount@example.com");
        Long categoryId = categoryId(token, "식비");

        for (String amount : new String[] {"0", "-1000"}) {
            mockMvc.perform(post("/api/v1/transactions")
                            .header("Authorization", "Bearer " + token)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"type\":\"EXPENSE\",\"amount\":" + amount
                                    + ",\"txnDate\":\"2026-01-01\",\"categoryId\":" + categoryId + "}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error.code").value("INVALID_INPUT"));
        }
    }

    @Test
    void 삭제된_카테고리를_쓰던_거래는_유지되고_카테고리_선택목록에서는_빠진다() throws Exception {
        String token = signupAndLogin("txn-cat-delete@example.com");
        Long categoryId = createCategory(token, "임시카테고리", TransactionType.EXPENSE, "#A855F7", 30);
        createTransaction(token, categoryId, TransactionType.EXPENSE, "8000", "2026-01-01", null, null);

        mockMvc.perform(delete("/api/v1/categories/" + categoryId).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/transactions").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].category.id").value(categoryId))
                .andExpect(jsonPath("$.data.content[0].category.deleted").value(true));

        mockMvc.perform(get("/api/v1/categories").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@.id == " + categoryId + ")]").doesNotExist());
    }

    @Test
    void 같은_날짜_거래_30건이_페이지_경계에서_중복_누락_없이_조회된다() throws Exception {
        String token = signupAndLogin("txn-page@example.com");
        Long categoryId = categoryId(token, "식비");

        for (int i = 0; i < 30; i++) {
            createTransaction(token, categoryId, TransactionType.EXPENSE,
                    String.valueOf(1000 + i), "2026-01-01", "가게" + i, null);
        }

        Set<Long> ids = new HashSet<>();
        for (int page = 0; page <= 1; page++) {
            MvcResult result = mockMvc.perform(get("/api/v1/transactions?page=" + page + "&size=20")
                            .header("Authorization", "Bearer " + token))
                    .andExpect(status().isOk())
                    .andReturn();

            JsonNode content = objectMapper.readTree(result.getResponse().getContentAsString())
                    .get("data").get("content");
            for (JsonNode item : content) {
                boolean added = ids.add(item.get("id").asLong());
                assertThat(added).as("id %s가 중복 조회됨", item.get("id").asLong()).isTrue();
            }
        }

        assertThat(ids).hasSize(30);
    }

    @Test
    void 정렬_화이트리스트_밖_값도_500이_아니다() throws Exception {
        String token = signupAndLogin("txn-sort@example.com");

        mockMvc.perform(get("/api/v1/transactions?sort=foo,bar").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
    }

    @Test
    void 키워드_검색은_대소문자를_무시하고_메모에서도_찾는다() throws Exception {
        String token = signupAndLogin("txn-keyword@example.com");
        Long categoryId = categoryId(token, "식비");
        createTransaction(token, categoryId, TransactionType.EXPENSE, "3000", "2026-01-01", "Starbucks", null);
        createTransaction(token, categoryId, TransactionType.EXPENSE, "4000", "2026-01-02", "무명가게", "커피 회의");

        mockMvc.perform(get("/api/v1/transactions?keyword=STARBUCKS").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.content[0].merchant").value("Starbucks"));

        mockMvc.perform(get("/api/v1/transactions?keyword=회의").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.content[0].memo").value("커피 회의"));
    }

    @Test
    void from_to_type_categoryId_필터를_함께_걸면_교집합이_나온다() throws Exception {
        String token = signupAndLogin("txn-filter@example.com");
        Long foodId = categoryId(token, "식비");
        Long transportId = categoryId(token, "교통");

        createTransaction(token, foodId, TransactionType.EXPENSE, "1000", "2026-01-05", null, null);
        createTransaction(token, transportId, TransactionType.EXPENSE, "2000", "2026-01-10", null, null);
        createTransaction(token, foodId, TransactionType.EXPENSE, "3000", "2026-02-05", null, null);
        createTransaction(token, categoryId(token, "급여"), TransactionType.INCOME, "3000000", "2026-01-25", null, null);

        mockMvc.perform(get("/api/v1/transactions?from=2026-01-01&to=2026-01-31")
                        .header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.data.totalElements").value(3));

        // from만 준 경우 (to 없이)
        mockMvc.perform(get("/api/v1/transactions?from=2026-02-01")
                        .header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.data.totalElements").value(1));

        mockMvc.perform(get("/api/v1/transactions?categoryId=" + foodId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.data.totalElements").value(2));

        mockMvc.perform(get("/api/v1/transactions?type=INCOME")
                        .header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.data.totalElements").value(1));

        mockMvc.perform(get("/api/v1/transactions?from=2026-01-01&to=2026-01-31&categoryId=" + foodId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.data.totalElements").value(1));
    }

    @Test
    void 목록_조회_시_카테고리_조회_쿼리가_건수에_비례해_늘지_않는다() throws Exception {
        String token = signupAndLogin("txn-n1@example.com");
        Long categoryId = categoryId(token, "식비");

        for (int i = 0; i < 3; i++) {
            createTransaction(token, categoryId, TransactionType.EXPENSE, "1000", "2026-01-0" + (i + 1), null, null);
        }

        SessionFactory sessionFactory = entityManagerFactory.unwrap(SessionFactory.class);
        sessionFactory.getStatistics().clear();
        mockMvc.perform(get("/api/v1/transactions?size=10").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
        long queryCountFor3 = sessionFactory.getStatistics().getQueryExecutionCount();

        for (int i = 0; i < 3; i++) {
            createTransaction(token, categoryId, TransactionType.EXPENSE, "1000", "2026-01-1" + (i + 1), null, null);
        }

        sessionFactory.getStatistics().clear();
        mockMvc.perform(get("/api/v1/transactions?size=10").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
        long queryCountFor6 = sessionFactory.getStatistics().getQueryExecutionCount();

        assertThat(queryCountFor6).isEqualTo(queryCountFor3);
    }

    private void signup(String email) throws Exception {
        mockMvc.perform(post("/api/v1/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new SignupRequest(email, "password1", "테스터"))))
                .andExpect(status().isOk());
    }

    private String signupAndLogin(String email) throws Exception {
        signup(email);
        MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginRequest(email, "password1"))))
                .andExpect(status().isOk())
                .andReturn();

        return objectMapper.readTree(result.getResponse().getContentAsString())
                .get("data").get("token").asString();
    }

    private Long categoryId(String token, String name) throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/categories").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode categories = objectMapper.readTree(result.getResponse().getContentAsString()).get("data");
        for (JsonNode category : categories) {
            if (category.get("name").asString().equals(name)) {
                return category.get("id").asLong();
            }
        }
        throw new IllegalStateException("카테고리를 찾을 수 없습니다: " + name);
    }

    private Long createCategory(String token, String name, TransactionType type, String color, int sortOrder)
            throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/categories")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new CategoryCreateRequest(name, type, color, sortOrder))))
                .andExpect(status().isOk())
                .andReturn();

        return objectMapper.readTree(result.getResponse().getContentAsString())
                .get("data").get("id").asLong();
    }

    private Long createTransaction(String token, Long categoryId, TransactionType type, String amount,
                                    String txnDate, String merchant, String memo) throws Exception {
        String body = "{\"type\":\"" + type + "\",\"amount\":" + amount
                + ",\"txnDate\":\"" + txnDate + "\",\"categoryId\":" + categoryId
                + ",\"merchant\":" + (merchant == null ? "null" : "\"" + merchant + "\"")
                + ",\"memo\":" + (memo == null ? "null" : "\"" + memo + "\"") + "}";

        MvcResult result = mockMvc.perform(post("/api/v1/transactions")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andReturn();

        return objectMapper.readTree(result.getResponse().getContentAsString())
                .get("data").get("id").asLong();
    }
}
