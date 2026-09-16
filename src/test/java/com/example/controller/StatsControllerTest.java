package com.example.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.LocalDate;

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
import com.example.dto.LoginRequest;
import com.example.dto.SignupRequest;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class StatsControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void 거래가_없는_달은_500이_아니라_모두_0이다() throws Exception {
        String token = signupAndLogin("stats-empty@example.com");

        MvcResult result = mockMvc.perform(get("/api/v1/stats/monthly?yearMonth=2026-01&asOf=2026-01-15")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.summary.income").value(0.00))
                .andExpect(jsonPath("$.data.summary.expense").value(0.00))
                .andExpect(jsonPath("$.data.summary.net").value(0.00))
                .andExpect(jsonPath("$.data.forecast").isEmpty())
                .andExpect(jsonPath("$.data.anomalies").isArray())
                .andExpect(jsonPath("$.data.anomalies.length()").value(0))
                .andReturn();

        JsonNode daily = objectMapper.readTree(result.getResponse().getContentAsString()).get("data").get("daily");
        assertThat(daily).hasSize(31); // 1월
        for (JsonNode day : daily) {
            assertThat(day.get("expense").decimalValue()).isEqualByComparingTo(BigDecimal.ZERO);
            assertThat(day.get("income").decimalValue()).isEqualByComparingTo(BigDecimal.ZERO);
        }
    }

    @Test
    void 직전_3개월_데이터가_없으면_forecast가_null이다() throws Exception {
        String token = signupAndLogin("stats-noforecast@example.com");
        Long categoryId = categoryId(token, "식비");
        // 대상월(2026-06) 자체 지출은 있지만 직전 3개월(2026-03~05)에는 아무 데이터도 없다.
        createTransaction(token, categoryId, "10000", "2026-06-10", null, null);

        mockMvc.perform(get("/api/v1/stats/monthly?yearMonth=2026-06&asOf=2026-06-15")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.forecast").isEmpty())
                .andExpect(jsonPath("$.data.summary.expense").value(10000.00));
    }

    @Test
    void 런레이트가_확정액과_일평균곱한_남은일수의_합과_소수둘째자리까지_일치한다() throws Exception {
        String token = signupAndLogin("stats-runrate@example.com");
        Long categoryId = categoryId(token, "식비");

        // 기준선: 2026-04 한 달(30일)에 30000원 -> 일평균 1000.00원
        createTransaction(token, categoryId, "30000", "2026-04-15", null, null);
        // 대상월 2026-05, asOf 2026-05-10 (경과 10일), 확정 지출 5000원
        createTransaction(token, categoryId, "5000", "2026-05-01", null, null);

        MvcResult result = mockMvc.perform(get("/api/v1/stats/monthly?yearMonth=2026-05&asOf=2026-05-10")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode forecast = objectMapper.readTree(result.getResponse().getContentAsString())
                .get("data").get("forecast");
        assertThat(forecast.get("basisMonths").asInt()).isEqualTo(1);
        assertThat(forecast.get("baselineDailyAvg").decimalValue()).isEqualByComparingTo("1000.00");
        assertThat(forecast.get("daysElapsed").asInt()).isEqualTo(10);
        assertThat(forecast.get("daysInMonth").asInt()).isEqualTo(31);
        // 5000 + 1000 * (31-10) = 5000 + 21000 = 26000.00
        assertThat(forecast.get("projectedExpense").decimalValue()).isEqualByComparingTo("26000.00");
    }

    @Test
    void asOf가_대상월_이후면_예측이_확정액과_같다() throws Exception {
        String token = signupAndLogin("stats-pastmonth@example.com");
        Long categoryId = categoryId(token, "식비");
        createTransaction(token, categoryId, "30000", "2026-04-15", null, null);
        createTransaction(token, categoryId, "5000", "2026-05-01", null, null);

        MvcResult result = mockMvc.perform(get("/api/v1/stats/monthly?yearMonth=2026-05&asOf=2026-06-01")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode data = objectMapper.readTree(result.getResponse().getContentAsString()).get("data");
        BigDecimal confirmedExpense = data.get("forecast").get("confirmedExpense").decimalValue();
        BigDecimal projectedExpense = data.get("forecast").get("projectedExpense").decimalValue();
        assertThat(projectedExpense).isEqualByComparingTo(confirmedExpense);
    }

    @Test
    void daysElapsed가_7미만이면_anomalies가_빈배열이다() throws Exception {
        String token = signupAndLogin("stats-earlymonth@example.com");
        Long categoryId = categoryId(token, "식비");
        createTransaction(token, categoryId, "30000", "2026-04-01", null, null);
        createTransaction(token, categoryId, "100000", "2026-05-01", null, null); // 폭증

        mockMvc.perform(get("/api/v1/stats/monthly?yearMonth=2026-05&asOf=2026-05-05")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.anomalies.length()").value(0));
    }

    @Test
    void 고정지출은_3개월_연속일_때만_감지되고_2개월은_제외된다() throws Exception {
        String token = signupAndLogin("stats-recurring@example.com");
        Long categoryId = categoryId(token, "문화/여가");

        createTransaction(token, categoryId, "17000", "2026-07-05", "넷플릭스", null);
        createTransaction(token, categoryId, "17000", "2026-08-05", "넷플릭스", null);
        createTransaction(token, categoryId, "17000", "2026-09-05", "넷플릭스", null);
        // 왓챠는 2개월만 있어 제외되어야 한다.
        createTransaction(token, categoryId, "12000", "2026-08-10", "왓챠", null);
        createTransaction(token, categoryId, "12000", "2026-09-10", "왓챠", null);

        MvcResult result = mockMvc.perform(get("/api/v1/stats/recurring?asOf=2026-09-16")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode data = objectMapper.readTree(result.getResponse().getContentAsString()).get("data");
        assertThat(data).hasSize(1);
        assertThat(data.get(0).get("merchant").asString()).isEqualTo("넷플릭스");
        assertThat(data.get(0).get("monthsSeen").asInt()).isEqualTo(3);
    }

    @Test
    void asOf를_바꾸면_결과가_바뀐다() throws Exception {
        String token = signupAndLogin("stats-asof@example.com");
        Long categoryId = categoryId(token, "식비");
        createTransaction(token, categoryId, "30000", "2026-04-15", null, null); // 기준선(forecast가 null이 되지 않게)
        createTransaction(token, categoryId, "10000", "2026-05-01", null, null);
        createTransaction(token, categoryId, "10000", "2026-05-20", null, null);

        MvcResult early = mockMvc.perform(get("/api/v1/stats/monthly?yearMonth=2026-05&asOf=2026-05-10")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();
        MvcResult late = mockMvc.perform(get("/api/v1/stats/monthly?yearMonth=2026-05&asOf=2026-05-25")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();

        BigDecimal earlyExpense = objectMapper.readTree(early.getResponse().getContentAsString())
                .get("data").get("summary").get("expense").decimalValue();
        BigDecimal lateExpense = objectMapper.readTree(late.getResponse().getContentAsString())
                .get("data").get("summary").get("expense").decimalValue();

        // summary는 asOf와 무관하게 같아야 하고(확정 지출), 대신 daysElapsed가 달라야 한다.
        assertThat(earlyExpense).isEqualByComparingTo(lateExpense);
        int earlyDaysElapsed = objectMapper.readTree(early.getResponse().getContentAsString())
                .get("data").get("forecast").get("daysElapsed").asInt();
        int lateDaysElapsed = objectMapper.readTree(late.getResponse().getContentAsString())
                .get("data").get("forecast").get("daysElapsed").asInt();
        assertThat(earlyDaysElapsed).isNotEqualTo(lateDaysElapsed);
    }

    @Test
    void 카테고리_비율의_합은_1에_가깝다() throws Exception {
        String token = signupAndLogin("stats-ratio@example.com");
        createTransaction(token, categoryId(token, "식비"), "30000", "2026-05-01", null, null);
        createTransaction(token, categoryId(token, "교통"), "20000", "2026-05-02", null, null);
        createTransaction(token, categoryId(token, "문화/여가"), "50000", "2026-05-03", null, null);

        MvcResult result = mockMvc.perform(get("/api/v1/stats/monthly?yearMonth=2026-05&asOf=2026-05-15")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode byCategory = objectMapper.readTree(result.getResponse().getContentAsString())
                .get("data").get("byCategory");
        BigDecimal totalRatio = BigDecimal.ZERO;
        for (JsonNode c : byCategory) {
            totalRatio = totalRatio.add(c.get("ratio").decimalValue());
        }
        assertThat(totalRatio.doubleValue()).isCloseTo(1.0, org.assertj.core.data.Offset.offset(0.01));
    }

    @Test
    void 삭제된_카테고리의_과거_지출은_집계에서_빠지지_않는다() throws Exception {
        String token = signupAndLogin("stats-deletedcat@example.com");
        Long categoryId = createCategory(token, "임시취미", TransactionType.EXPENSE, "#A855F7", 15);
        createTransaction(token, categoryId, "9000", "2026-05-05", null, null);

        deleteCategory(token, categoryId);

        MvcResult result = mockMvc.perform(get("/api/v1/stats/monthly?yearMonth=2026-05&asOf=2026-05-15")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode byCategory = objectMapper.readTree(result.getResponse().getContentAsString())
                .get("data").get("byCategory");
        boolean found = false;
        for (JsonNode c : byCategory) {
            if (c.get("categoryId").asLong() == categoryId) {
                found = true;
                assertThat(c.get("deleted").asBoolean()).isTrue();
                assertThat(c.get("amount").decimalValue()).isEqualByComparingTo("9000.00");
            }
        }
        assertThat(found).as("삭제된 카테고리의 과거 지출이 byCategory에 남아있어야 한다").isTrue();
    }

    @Test
    void 예산이_없는_카테고리의_소진율은_0이고_Infinity나_NaN이_아니다() throws Exception {
        String token = signupAndLogin("stats-nobudget@example.com");
        Long categoryId = categoryId(token, "식비");
        createTransaction(token, categoryId, "12000", "2026-05-05", null, null);

        MvcResult result = mockMvc.perform(get("/api/v1/stats/monthly?yearMonth=2026-05&asOf=2026-05-15")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode budgets = objectMapper.readTree(result.getResponse().getContentAsString())
                .get("data").get("budgets");
        boolean found = false;
        for (JsonNode b : budgets) {
            if (b.get("categoryId").asLong() == categoryId) {
                found = true;
                assertThat(b.get("budget").decimalValue()).isEqualByComparingTo(BigDecimal.ZERO);
                assertThat(b.get("usageRatio").decimalValue()).isEqualByComparingTo(BigDecimal.ZERO);
                assertThat(b.get("exceeded").asBoolean()).isFalse();
            }
        }
        assertThat(found).isTrue();
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
        String body = "{\"name\":\"" + name + "\",\"type\":\"" + type + "\",\"color\":\"" + color
                + "\",\"sortOrder\":" + sortOrder + "}";
        MvcResult result = mockMvc.perform(post("/api/v1/categories")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andReturn();

        return objectMapper.readTree(result.getResponse().getContentAsString())
                .get("data").get("id").asLong();
    }

    private void deleteCategory(String token, Long categoryId) throws Exception {
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .delete("/api/v1/categories/" + categoryId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
    }

    private Long createTransaction(String token, Long categoryId, String amount, String txnDate,
                                    String merchant, String memo) throws Exception {
        String body = "{\"type\":\"EXPENSE\",\"amount\":" + amount
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
