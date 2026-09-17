package com.example.service;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.multipart.MultipartFile;

import com.example.domain.Category;
import com.example.domain.CategoryRepository;
import com.example.domain.TransactionType;
import com.example.domain.User;
import com.example.dto.ReceiptParseResponse;
import com.example.exception.BusinessException;
import com.example.exception.ErrorCode;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

@Service
public class ReceiptParseService {

    private static final long MAX_FILE_SIZE = 5L * 1024 * 1024;
    private static final List<String> ALLOWED_CONTENT_TYPES = List.of("image/jpeg", "image/png", "image/heic");
    private static final String ANTHROPIC_MESSAGES_URL = "https://api.anthropic.com/v1/messages";
    private static final String ANTHROPIC_VERSION = "2023-06-01";

    private final CategoryRepository categoryRepository;
    // Spring Boot 4의 자동 구성 ObjectMapper 빈은 Jackson 3(tools.jackson) 타입이라 주입받을 수 없다.
    // jjwt-jackson이 compile scope로 끌어온 Jackson 2(com.fasterxml.jackson)만 classpath에 있어 직접 생성한다(CLAUDE.md 3장).
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final RestClient restClient;
    private final String apiKey;
    private final String model;

    public ReceiptParseService(CategoryRepository categoryRepository,
                                @Value("${receipt.vision.api-key}") String apiKey,
                                @Value("${receipt.vision.model}") String model) {
        this.categoryRepository = categoryRepository;
        this.restClient = RestClient.create();
        this.apiKey = apiKey;
        this.model = model;
    }

    @Transactional(readOnly = true)
    public ReceiptParseResponse parse(User user, MultipartFile file) {
        validateFile(file);

        // 영수증 지출을 분류하는 것이므로 지출 카테고리만 후보로 준다 — 수입 카테고리는 애초에 맞을 수 없다.
        List<Category> expenseCategories = categoryRepository
                .findByUserAndTypeAndDeletedAtIsNullOrderBySortOrderAscIdAsc(user, TransactionType.EXPENSE);

        JsonNode result = callVisionApi(file, expenseCategories);

        String categoryName = textOrNull(result, "categoryName");
        Long categoryId = expenseCategories.stream()
                .filter(category -> category.getName().equals(categoryName))
                .map(Category::getId)
                .findFirst()
                .orElse(null);

        return new ReceiptParseResponse(
                parseDateOrNull(textOrNull(result, "txnDate")),
                categoryId,
                categoryId != null ? categoryName : null,
                textOrNull(result, "merchant"),
                parseAmountOrNull(result.get("amount")));
    }

    private void validateFile(MultipartFile file) {
        if (file.isEmpty()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "첨부된 이미지가 없습니다.");
        }
        if (file.getSize() > MAX_FILE_SIZE) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "파일 용량이 상한(5MB)을 초과했습니다.");
        }
        if (!ALLOWED_CONTENT_TYPES.contains(file.getContentType())) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "jpg, png, heic 이미지만 업로드할 수 있습니다.");
        }
    }

    private JsonNode callVisionApi(MultipartFile file, List<Category> expenseCategories) {
        String base64Image;
        try {
            base64Image = Base64.getEncoder().encodeToString(file.getBytes());
        } catch (IOException e) {
            throw new BusinessException(ErrorCode.RECEIPT_PARSE_FAILED, "이미지를 읽을 수 없습니다.");
        }

        String categoryNames = expenseCategories.stream().map(Category::getName).collect(Collectors.joining(", "));
        String prompt = """
                이 영수증 이미지에서 정보를 추출해 아래 JSON 형식으로만 응답하세요. 다른 설명 문장 없이 JSON 객체 하나만 출력합니다.
                {"txnDate": "yyyy-MM-dd 형식의 결제 날짜 (모르면 null)", "categoryName": "다음 중 가장 알맞은 하나 [%s] (모르면 null)", "merchant": "가맹점 이름 (모르면 null)", "amount": 총 결제 금액을 숫자만(콤마 없이, 모르면 null)}
                """.formatted(categoryNames);

        Map<String, Object> requestBody = Map.of(
                "model", model,
                "max_tokens", 1024,
                "messages", List.of(Map.of(
                        "role", "user",
                        "content", List.of(
                                Map.of("type", "image", "source", Map.of(
                                        "type", "base64",
                                        "media_type", file.getContentType(),
                                        "data", base64Image)),
                                Map.of("type", "text", "text", prompt)))));

        String responseBody;
        try {
            responseBody = restClient.post()
                    .uri(ANTHROPIC_MESSAGES_URL)
                    .header("x-api-key", apiKey)
                    .header("anthropic-version", ANTHROPIC_VERSION)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(requestBody)
                    .retrieve()
                    .body(String.class);
        } catch (RestClientException e) {
            // 네트워크 오류·타임아웃뿐 아니라 인증 실패(401) 등 API의 4xx/5xx 응답도 RestClientException으로 온다.
            throw new BusinessException(ErrorCode.RECEIPT_PARSE_FAILED, "영수증 인식에 실패했습니다.");
        }

        try {
            JsonNode root = objectMapper.readTree(responseBody);
            String text = root.path("content").get(0).path("text").asText();
            return objectMapper.readTree(extractJsonObject(text));
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.RECEIPT_PARSE_FAILED, "영수증 내용을 인식하지 못했습니다.");
        }
    }

    // 모델이 답을 ```json ... ``` 코드블록으로 감싸는 경우를 대비해 중괄호 구간만 잘라낸다.
    private String extractJsonObject(String text) {
        int start = text.indexOf('{');
        int end = text.lastIndexOf('}');
        if (start < 0 || end < start) {
            throw new IllegalStateException("JSON 형식이 아닙니다: " + text);
        }
        return text.substring(start, end + 1);
    }

    private String textOrNull(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return (value == null || value.isNull()) ? null : value.asText();
    }

    private LocalDate parseDateOrNull(String raw) {
        if (raw == null) {
            return null;
        }
        try {
            return LocalDate.parse(raw);
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    private BigDecimal parseAmountOrNull(JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }
        try {
            String digitsOnly = node.asText().replaceAll("[^0-9.]", "");
            return digitsOnly.isEmpty() ? null : new BigDecimal(digitsOnly);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
