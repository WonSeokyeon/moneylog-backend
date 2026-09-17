package com.example.controller;

import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.example.domain.User;
import com.example.dto.ApiResponse;
import com.example.dto.ReceiptParseResponse;
import com.example.service.ReceiptParseService;

@RestController
@RequestMapping("/api/v1/receipts")
public class ReceiptController {

    private final ReceiptParseService receiptParseService;

    public ReceiptController(ReceiptParseService receiptParseService) {
        this.receiptParseService = receiptParseService;
    }

    // consumes를 명시해야 Swagger UI가 파일 업로드 위젯으로 그린다(DataController.importCsv와 동일한 이유).
    @PostMapping(value = "/parse", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<ReceiptParseResponse> parse(@AuthenticationPrincipal User user,
                                                     @RequestParam("file") MultipartFile file) {
        return ApiResponse.success(receiptParseService.parse(user, file));
    }
}
