package com.example.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
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
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void 회원가입이_성공한다() throws Exception {
        mockMvc.perform(post("/api/v1/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new SignupRequest("signup1@example.com", "password1", "테스터"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    void 이메일이_중복되면_409를_반환한다() throws Exception {
        signup("dup@example.com", "password1", "테스터1");

        mockMvc.perform(post("/api/v1/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new SignupRequest("dup@example.com", "password2", "테스터2"))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("EMAIL_DUPLICATED"));
    }

    @Test
    void 회원가입_시_기본_카테고리_9개가_생성된다() throws Exception {
        signup("cat9@example.com", "password1", "테스터");
        String token = login("cat9@example.com", "password1");

        mockMvc.perform(get("/api/v1/categories")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(9));
    }

    @Test
    void 한글_25자_비밀번호는_500이_아니라_400을_반환한다() throws Exception {
        String password75Bytes = "가".repeat(25);

        mockMvc.perform(post("/api/v1/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new SignupRequest("longpw@example.com", password75Bytes, "테스터"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_INPUT"));
    }

    @Test
    void 로그인_성공_시_JWT가_스펙대로_발급된다() throws Exception {
        signup("login1@example.com", "password1", "테스터");
        String token = login("login1@example.com", "password1");

        JsonNode header = decodeJwtSegment(token, 0);
        JsonNode payload = decodeJwtSegment(token, 1);

        assertThat(header.get("alg").asString()).isEqualTo("HS256");

        long iat = payload.get("iat").asLong();
        long exp = payload.get("exp").asLong();
        assertThat(exp - iat).isEqualTo(86400L);
    }

    @Test
    void 비밀번호_오류와_미가입_이메일의_401_메시지가_동일하다() throws Exception {
        signup("exists@example.com", "password1", "테스터");

        MvcResult wrongPassword = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new LoginRequest("exists@example.com", "wrong-password"))))
                .andExpect(status().isUnauthorized())
                .andReturn();

        MvcResult noSuchEmail = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new LoginRequest("no-such-email@example.com", "whatever"))))
                .andExpect(status().isUnauthorized())
                .andReturn();

        String wrongPasswordMessage = objectMapper
                .readTree(wrongPassword.getResponse().getContentAsString())
                .get("error").get("message").asString();
        String noSuchEmailMessage = objectMapper
                .readTree(noSuchEmail.getResponse().getContentAsString())
                .get("error").get("message").asString();

        assertThat(wrongPasswordMessage).isEqualTo(noSuchEmailMessage);
    }

    @Test
    void 토큰_없이_보호된_엔드포인트_호출시_401_ApiResponse_포맷() throws Exception {
        mockMvc.perform(get("/api/v1/auth/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));
    }

    @Test
    void me_응답에_email과_nickname이_포함된다() throws Exception {
        signup("me1@example.com", "password1", "내이름");
        String token = login("me1@example.com", "password1");

        mockMvc.perform(get("/api/v1/auth/me")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.email").value("me1@example.com"))
                .andExpect(jsonPath("$.data.nickname").value("내이름"));
    }

    @Test
    void swagger_UI가_여전히_401_없이_열린다() throws Exception {
        mockMvc.perform(get("/swagger-ui/index.html"))
                .andExpect(status().isOk());
    }

    private void signup(String email, String password, String nickname) throws Exception {
        mockMvc.perform(post("/api/v1/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new SignupRequest(email, password, nickname))))
                .andExpect(status().isOk());
    }

    private String login(String email, String password) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginRequest(email, password))))
                .andExpect(status().isOk())
                .andReturn();

        return objectMapper.readTree(result.getResponse().getContentAsString())
                .get("data").get("token").asString();
    }

    private JsonNode decodeJwtSegment(String token, int index) {
        String segment = token.split("\\.")[index];
        byte[] decoded = Base64.getUrlDecoder().decode(segment);
        return objectMapper.readTree(new String(decoded, StandardCharsets.UTF_8));
    }
}
