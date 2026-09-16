package com.example.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
import com.example.dto.CategoryUpdateRequest;
import com.example.dto.LoginRequest;
import com.example.dto.SignupRequest;

import tools.jackson.databind.ObjectMapper;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class CategoryControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void 같은_이름_카테고리는_중복_생성할_수_없고_삭제_후_재생성은_성공한다() throws Exception {
        String token = signupAndLogin("cat-dup@example.com");

        // 기본 카테고리 "식비"(EXPENSE)와 중복
        mockMvc.perform(post("/api/v1/categories")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new CategoryCreateRequest("식비", TransactionType.EXPENSE, "#EF4444", 0))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("CATEGORY_DUPLICATED"));

        Long newId = createCategory(token, "취미", TransactionType.EXPENSE, "#8B5CF6", 10);

        mockMvc.perform(delete("/api/v1/categories/" + newId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/categories")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new CategoryCreateRequest("취미", TransactionType.EXPENSE, "#8B5CF6", 10))))
                .andExpect(status().isOk());
    }

    @Test
    void 삭제된_카테고리는_목록에서_빠진다() throws Exception {
        String token = signupAndLogin("cat-list@example.com");
        Long id = createCategory(token, "부업", TransactionType.INCOME, "#22C55E", 5);

        mockMvc.perform(delete("/api/v1/categories/" + id)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/categories")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@.id == " + id + ")]").doesNotExist());
    }

    @Test
    void type_필터가_정확히_동작한다() throws Exception {
        String token = signupAndLogin("cat-filter@example.com");

        mockMvc.perform(get("/api/v1/categories?type=INCOME")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0].type").value("INCOME"));
    }

    @Test
    void 타_사용자의_카테고리에_접근하면_404() throws Exception {
        String ownerToken = signupAndLogin("cat-owner@example.com");
        Long categoryId = createCategory(ownerToken, "혼자만의카테고리", TransactionType.EXPENSE, "#F97316", 20);

        String otherToken = signupAndLogin("cat-other@example.com");

        mockMvc.perform(put("/api/v1/categories/" + categoryId)
                        .header("Authorization", "Bearer " + otherToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new CategoryUpdateRequest("가로채기", "#000000", 0))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("CATEGORY_NOT_FOUND"));

        mockMvc.perform(delete("/api/v1/categories/" + categoryId)
                        .header("Authorization", "Bearer " + otherToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("CATEGORY_NOT_FOUND"));
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
}
