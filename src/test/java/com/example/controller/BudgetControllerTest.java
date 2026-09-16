package com.example.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import com.example.dto.BudgetItemRequest;
import com.example.dto.BudgetUpsertRequest;
import com.example.dto.LoginRequest;
import com.example.dto.SignupRequest;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class BudgetControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    // CLAUDE.md 12장 Phase6 20번: 예산 upsert가 기존 행을 갱신하고, amount=0인 항목은 행을 제거한다.
    @Test
    void 예산_upsert가_기존_행을_갱신하고_amount0인_항목은_삭제한다() throws Exception {
        String token = signupAndLogin("budget-upsert@example.com");
        Long foodId = categoryId(token, "식비");
        Long transportId = categoryId(token, "교통");

        putBudgets(token, "2026-06", List.of(
                new BudgetItemRequest(foodId, new BigDecimal("300000")),
                new BudgetItemRequest(transportId, new BigDecimal("100000"))));

        JsonNode budgets = getBudgets(token, "2026-06");
        assertThat(amountOf(budgets, foodId)).isEqualByComparingTo("300000.00");
        assertThat(amountOf(budgets, transportId)).isEqualByComparingTo("100000.00");

        // 같은 카테고리에 다시 PUT하면 새 행이 아니라 기존 행이 갱신된다.
        putBudgets(token, "2026-06", List.of(new BudgetItemRequest(foodId, new BigDecimal("400000"))));
        budgets = getBudgets(token, "2026-06");
        assertThat(amountOf(budgets, foodId)).isEqualByComparingTo("400000.00");
        assertThat(amountOf(budgets, transportId)).isEqualByComparingTo("100000.00"); // 건드리지 않은 항목은 유지

        // amount=0으로 PUT하면 행이 물리 삭제되어 다음 조회에서 null로 돌아온다.
        putBudgets(token, "2026-06", List.of(new BudgetItemRequest(transportId, BigDecimal.ZERO)));
        budgets = getBudgets(token, "2026-06");
        assertThat(findEntry(budgets, transportId).get("amount").isNull()).isTrue();
    }

    @Test
    void 지출이_아닌_카테고리에_예산_설정을_시도하면_400을_반환한다() throws Exception {
        String token = signupAndLogin("budget-income@example.com");
        Long salaryId = categoryId(token, "급여");

        mockMvc.perform(put("/api/v1/budgets")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new BudgetUpsertRequest(
                                "2026-06", List.of(new BudgetItemRequest(salaryId, new BigDecimal("1000")))))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_INPUT"));
    }

    // CLAUDE.md 12장 Phase6 21번: 예산 0(미설정)일 때 소진율이 Infinity/NaN이 아니다.
    @Test
    void 예산이_미설정인_카테고리의_소진율이_비정상값이_아니다() throws Exception {
        String token = signupAndLogin("budget-noset@example.com");
        Long foodId = categoryId(token, "식비");
        createTransaction(token, foodId, "12000", "2026-06-05");

        MvcResult result = mockMvc.perform(get("/api/v1/stats/monthly?yearMonth=2026-06&asOf=2026-06-10")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode budgets = objectMapper.readTree(result.getResponse().getContentAsString())
                .get("data").get("budgets");
        JsonNode foodBudget = findEntry(budgets, foodId);

        assertThat(foodBudget).isNotNull();
        assertThat(new BigDecimal(foodBudget.get("budget").asString())).isEqualByComparingTo("0.00");
        assertThat(new BigDecimal(foodBudget.get("usageRatio").asString())).isEqualByComparingTo("0.00");
        assertThat(foodBudget.get("exceeded").asBoolean()).isFalse();
    }

    private void putBudgets(String token, String yearMonth, List<BudgetItemRequest> items) throws Exception {
        mockMvc.perform(put("/api/v1/budgets")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new BudgetUpsertRequest(yearMonth, items))))
                .andExpect(status().isOk());
    }

    private JsonNode getBudgets(String token, String yearMonth) throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/budgets?yearMonth=" + yearMonth)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("data");
    }

    private JsonNode findEntry(JsonNode array, Long categoryId) {
        for (JsonNode entry : array) {
            if (entry.get("categoryId").asLong() == categoryId) {
                return entry;
            }
        }
        throw new IllegalStateException("categoryId를 찾을 수 없습니다: " + categoryId);
    }

    private BigDecimal amountOf(JsonNode budgets, Long categoryId) {
        return new BigDecimal(findEntry(budgets, categoryId).get("amount").asString());
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

    private Long createTransaction(String token, Long categoryId, String amount, String txnDate) throws Exception {
        String body = "{\"type\":\"EXPENSE\",\"amount\":" + amount
                + ",\"txnDate\":\"" + txnDate + "\",\"categoryId\":" + categoryId + "}";

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
