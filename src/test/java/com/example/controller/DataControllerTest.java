package com.example.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import com.example.dto.LoginRequest;
import com.example.dto.SignupRequest;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class DataControllerTest {

    private static final byte[] UTF8_BOM = {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF};

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    // CLAUDE.md 12장 Phase6 23번: 내보낸 CSV의 첫 3바이트가 BOM(EF BB BF)이다.
    @Test
    void 내보낸_CSV의_첫_3바이트는_BOM이다() throws Exception {
        String token = signupAndLogin("csv-bom@example.com");

        byte[] body = export(token, "2026-06-01", "2026-06-30");

        assertThat(body).startsWith(UTF8_BOM[0], UTF8_BOM[1], UTF8_BOM[2]);
    }

    // CLAUDE.md 12장 Phase6 22번: 내보낸 CSV를 그대로 다시 가져오면 건수가 일치한다.
    // ⚠️ 같은 파일 재업로드 시 중복 등록은 의도된 동작이다(CLAUDE.md 5장 CSV 절) — 이 테스트는 "몇 건이 파싱되어
    // 성공 처리되는가"만 검증하고, 등록 후 총 거래 수가 2배가 되는 것은 버그가 아니다.
    @Test
    void 내보낸_CSV를_그대로_다시_가져오면_건수가_일치한다() throws Exception {
        String token = signupAndLogin("csv-roundtrip@example.com");
        Long foodId = categoryId(token, "식비");
        createTransaction(token, foodId, "12500", "2026-06-01", "스타벅스", null);
        createTransaction(token, foodId, "3000", "2026-06-02", "편의점", null);

        byte[] exported = export(token, "2026-06-01", "2026-06-30");
        JsonNode result = importCsv(token, exported);

        assertThat(result.get("imported").asInt()).isEqualTo(2);
        assertThat(result.get("failed").asInt()).isEqualTo(0);
    }

    // CLAUDE.md 12장 Phase6 24번: 메모에 콤마·따옴표·줄바꿈이 포함된 거래가 내보내기·가져오기를 왕복해도 그대로 유지된다.
    @Test
    void 메모의_콤마_따옴표_줄바꿈이_왕복_후에도_유지된다() throws Exception {
        String token = signupAndLogin("csv-memo@example.com");
        Long foodId = categoryId(token, "식비");
        String originalMemo = "팀 회의, \"긴급\" 결제\n다음날 정산";
        createTransaction(token, foodId, "9900", "2026-06-03", "카페", originalMemo);

        byte[] exported = export(token, "2026-06-01", "2026-06-30");
        importCsv(token, exported);

        MvcResult result = mockMvc.perform(get("/api/v1/transactions?from=2026-06-03&to=2026-06-03")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode content = objectMapper.readTree(result.getResponse().getContentAsString())
                .get("data").get("content");
        boolean matched = false;
        for (JsonNode item : content) {
            if (originalMemo.equals(item.get("memo").asString())) {
                matched = true;
            }
        }
        assertThat(matched).as("왕복 후 원본 메모와 정확히 일치하는 거래가 있어야 한다").isTrue();
    }

    // CLAUDE.md 12장 Phase6 25번: 없는 카테고리 이름이 섞인 CSV는 해당 행만 실패하고 나머지는 성공한다.
    @Test
    void 없는_카테고리_이름이_섞인_CSV는_해당_행만_실패한다() throws Exception {
        String token = signupAndLogin("csv-badcategory@example.com");
        String csv = "날짜,구분,카테고리,금액,거래처,메모\r\n"
                + "2026-06-01,지출,식비,10000,가게1,\r\n"
                + "2026-06-02,지출,없는카테고리,20000,가게2,\r\n"
                + "2026-06-03,지출,교통,3000,가게3,\r\n";

        JsonNode result = importCsv(token, csv.getBytes(StandardCharsets.UTF_8));

        assertThat(result.get("imported").asInt()).isEqualTo(2);
        assertThat(result.get("failed").asInt()).isEqualTo(1);
        assertThat(result.get("errors").get(0).get("line").asInt()).isEqualTo(3);
        assertThat(result.get("errors").get(0).get("reason").asString()).contains("없는카테고리");
    }

    // CLAUDE.md 12장 Phase6 26번: MS949로 인코딩된 CSV의 한글이 깨지지 않고, 콤마 섞인 금액과 점 구분 날짜도 정상 파싱된다.
    // ⚠️ 픽스처를 UTF-8로 만들면 이 테스트는 아무것도 검증하지 못한다 — 반드시 MS949 바이트로 직접 만든다.
    @Test
    void MS949로_인코딩된_CSV가_깨지지_않고_가져와진다() throws Exception {
        String token = signupAndLogin("csv-ms949@example.com");
        String csv = "날짜,구분,카테고리,금액,거래처,메모\r\n"
                + "2026.06.10,지출,생활용품,\"12,500\",다이소,엑셀에서편집함\r\n";
        byte[] ms949Bytes = csv.getBytes(Charset.forName("MS949"));

        JsonNode result = importCsv(token, ms949Bytes);

        assertThat(result.get("imported").asInt()).isEqualTo(1);
        assertThat(result.get("failed").asInt()).isEqualTo(0);

        MvcResult listResult = mockMvc.perform(get("/api/v1/transactions?from=2026-06-10&to=2026-06-10")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode content = objectMapper.readTree(listResult.getResponse().getContentAsString())
                .get("data").get("content");

        assertThat(content.get(0).get("merchant").asString()).isEqualTo("다이소");
        assertThat(content.get(0).get("memo").asString()).isEqualTo("엑셀에서편집함");
        assertThat(content.get(0).get("amount").asDouble()).isEqualTo(12500.00);
    }

    private byte[] export(String token, String from, String to) throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/data/export?from=" + from + "&to=" + to)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();
        return result.getResponse().getContentAsByteArray();
    }

    private JsonNode importCsv(String token, byte[] csvBytes) throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "import.csv", "text/csv", csvBytes);

        MvcResult result = mockMvc.perform(multipart("/api/v1/data/import")
                        .file(file)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();

        return objectMapper.readTree(result.getResponse().getContentAsString()).get("data");
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

    private void createTransaction(String token, Long categoryId, String amount, String txnDate,
                                    String merchant, String memo) throws Exception {
        String body = "{\"type\":\"EXPENSE\",\"amount\":" + amount
                + ",\"txnDate\":\"" + txnDate + "\",\"categoryId\":" + categoryId
                + ",\"merchant\":" + (merchant == null ? "null" : objectMapper.writeValueAsString(merchant))
                + ",\"memo\":" + (memo == null ? "null" : objectMapper.writeValueAsString(memo)) + "}";

        mockMvc.perform(post("/api/v1/transactions")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk());
    }
}
