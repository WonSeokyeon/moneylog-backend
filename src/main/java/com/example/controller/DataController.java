package com.example.controller;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.example.domain.User;
import com.example.dto.ApiResponse;
import com.example.dto.CsvImportResult;
import com.example.service.DataService;

@RestController
@RequestMapping("/api/v1/data")
public class DataController {

    private static final byte[] UTF8_BOM = {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF};
    private static final DateTimeFormatter FILENAME_MONTH = DateTimeFormatter.ofPattern("yyyy-MM");

    private final DataService dataService;

    public DataController(DataService dataService) {
        this.dataService = dataService;
    }

    // 이 엔드포인트만 ApiResponse 봉투를 쓰지 않는다(CLAUDE.md 5장 유일한 예외) — CSV 바이트를 그대로 반환한다.
    @GetMapping("/export")
    public ResponseEntity<byte[]> export(@AuthenticationPrincipal User user,
                                          @RequestParam LocalDate from,
                                          @RequestParam LocalDate to) {
        String csv = dataService.exportCsv(user, from, to);

        // BOM이 없으면 Excel이 시스템 기본 인코딩(CP949)으로 열어 한글이 깨진다(CLAUDE.md 5장).
        ByteArrayOutputStream body = new ByteArrayOutputStream();
        body.writeBytes(UTF8_BOM);
        body.writeBytes(csv.getBytes(StandardCharsets.UTF_8));

        String filename = "moneylog_" + FILENAME_MONTH.format(from) + ".csv";

        return ResponseEntity.ok()
                .contentType(MediaType.valueOf("text/csv;charset=UTF-8"))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .body(body.toByteArray());
    }

    // consumes를 명시해야 SpringDoc이 MultipartFile을 파일 업로드 위젯으로 그린다 —
    // 없으면 Spring MVC 자체는 정상 동작하지만 Swagger UI가 JSON 텍스트박스로 잘못 그린다.
    @PostMapping(value = "/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<CsvImportResult> importCsv(@AuthenticationPrincipal User user,
                                                    @RequestParam("file") MultipartFile file) {
        return ApiResponse.success(dataService.importCsv(user, file));
    }
}
