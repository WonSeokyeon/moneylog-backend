package com.example.service;

import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.example.domain.Category;
import com.example.domain.CategoryRepository;
import com.example.domain.Transaction;
import com.example.domain.TransactionRepository;
import com.example.domain.TransactionType;
import com.example.domain.User;
import com.example.dto.CsvImportResult;
import com.example.dto.CsvImportResult.CsvImportError;
import com.example.exception.BusinessException;
import com.example.exception.ErrorCode;

@Service
public class DataService {

    private static final String CSV_HEADER = "날짜,구분,카테고리,금액,거래처,메모";
    private static final List<String> CSV_HEADER_FIELDS = List.of("날짜", "구분", "카테고리", "금액", "거래처", "메모");
    private static final int MAX_IMPORT_ROWS = 5000;
    private static final DateTimeFormatter[] DATE_FORMATS = {
            DateTimeFormatter.ofPattern("yyyy-MM-dd"),
            DateTimeFormatter.ofPattern("yyyy.MM.dd"),
            DateTimeFormatter.ofPattern("yyyy/MM/dd"),
    };

    private final TransactionRepository transactionRepository;
    private final CategoryRepository categoryRepository;

    public DataService(TransactionRepository transactionRepository, CategoryRepository categoryRepository) {
        this.transactionRepository = transactionRepository;
        this.categoryRepository = categoryRepository;
    }

    // findForMonth는 이름과 달리 임의 기간(from~to)을 JOIN FETCH로 조회하므로 신규 쿼리 없이 그대로 재사용한다.
    // 카테고리 조인에 deletedAt 조건이 없어 삭제된 카테고리를 쓰던 거래도 이름이 그대로 내려온다(CLAUDE.md 4장).
    @Transactional(readOnly = true)
    public String exportCsv(User user, LocalDate from, LocalDate to) {
        List<Transaction> transactions = transactionRepository.findForMonth(user, from, to);

        StringBuilder csv = new StringBuilder(CSV_HEADER);
        for (Transaction transaction : transactions) {
            csv.append("\r\n").append(toCsvRow(transaction));
        }
        return csv.toString();
    }

    // 부분 성공을 허용한다 — 행 하나가 실패해도 예외를 던지지 않고 실패 목록에 담아 다음 행을 계속 처리한다.
    // 예외를 던지면 @Transactional이 메서드 전체를 롤백해 이미 성공한 행까지 함께 사라진다.
    @Transactional
    public CsvImportResult importCsv(User user, MultipartFile file) {
        String content = removeBom(decode(readBytes(file)));
        List<String> rows = CsvParser.splitRows(content).stream().filter(row -> !row.isBlank()).toList();

        if (rows.isEmpty() || !CsvParser.parseLine(rows.get(0).trim()).equals(CSV_HEADER_FIELDS)) {
            throw new BusinessException(ErrorCode.INVALID_CSV, "CSV 헤더가 올바르지 않습니다.");
        }

        List<String> dataRows = rows.subList(1, rows.size());
        if (dataRows.size() > MAX_IMPORT_ROWS) {
            throw new BusinessException(ErrorCode.INVALID_CSV, "행 수가 " + MAX_IMPORT_ROWS + "행을 초과했습니다.");
        }

        int imported = 0;
        List<CsvImportError> errors = new ArrayList<>();
        for (int i = 0; i < dataRows.size(); i++) {
            int lineNumber = i + 2; // 1번 줄은 헤더
            try {
                importRow(user, dataRows.get(i));
                imported++;
            } catch (RowImportException e) {
                errors.add(new CsvImportError(lineNumber, e.getMessage()));
            } catch (Exception e) {
                errors.add(new CsvImportError(lineNumber, "행을 처리하는 중 오류가 발생했습니다."));
            }
        }

        return new CsvImportResult(imported, errors.size(), errors);
    }

    private void importRow(User user, String row) {
        List<String> fields = CsvParser.parseLine(row);
        if (fields.size() < 6) {
            throw new RowImportException("열 개수가 6개가 아닙니다.");
        }

        TransactionType type = switch (fields.get(1).trim()) {
            case "수입" -> TransactionType.INCOME;
            case "지출" -> TransactionType.EXPENSE;
            default -> throw new RowImportException("구분 값은 수입 또는 지출이어야 합니다.");
        };

        LocalDate txnDate = parseDate(fields.get(0).trim());
        BigDecimal amount = parseAmount(fields.get(3));

        String categoryName = fields.get(2).trim();
        Category category = categoryRepository
                .findByUserAndNameAndTypeAndDeletedAtIsNull(user, categoryName, type)
                .orElseThrow(() -> new RowImportException("카테고리 '" + categoryName + "'를 찾을 수 없습니다."));

        String merchant = fields.get(4).isEmpty() ? null : fields.get(4);
        String memo = fields.get(5).isEmpty() ? null : fields.get(5);

        transactionRepository.save(new Transaction(user, category, type, amount, txnDate, merchant, memo));
    }

    private LocalDate parseDate(String raw) {
        for (DateTimeFormatter format : DATE_FORMATS) {
            try {
                return LocalDate.parse(raw, format);
            } catch (DateTimeParseException ignored) {
                // 다음 형식으로 재시도
            }
        }
        throw new RowImportException("날짜 형식이 올바르지 않습니다: " + raw);
    }

    private BigDecimal parseAmount(String raw) {
        String digitsOnly = raw.replaceAll("[^0-9.]", "");
        if (digitsOnly.isEmpty()) {
            throw new RowImportException("금액이 올바르지 않습니다: " + raw);
        }
        BigDecimal amount = new BigDecimal(digitsOnly);
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new RowImportException("금액은 0보다 커야 합니다.");
        }
        return amount;
    }

    // UTF-8 엄격 디코딩을 강제한다 — 관용 모드로 두면 깨진 바이트가 조용히 대체 문자로 바뀌어
    // MS949 폴백이 영영 동작하지 않는다(CLAUDE.md 5장).
    private String decode(byte[] bytes) {
        try {
            return StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes))
                    .toString();
        } catch (CharacterCodingException e) {
            return new String(bytes, Charset.forName("MS949"));
        }
    }

    // BOM 제거는 디코딩 이후에 한다. 순서를 바꾸면 CP949 파일에서 엉뚱한 바이트를 잘라낸다(CLAUDE.md 5장).
    private String removeBom(String content) {
        return content.startsWith("﻿") ? content.substring(1) : content;
    }

    private byte[] readBytes(MultipartFile file) {
        try {
            return file.getBytes();
        } catch (IOException e) {
            throw new BusinessException(ErrorCode.INVALID_CSV, "파일을 읽을 수 없습니다.");
        }
    }

    // 행 단위 검증 실패를 표현하는 내부 전용 예외. importCsv 루프 밖으로 던져지지 않고 항상 잡혀 errors에 담긴다.
    private static class RowImportException extends RuntimeException {
        RowImportException(String message) {
            super(message);
        }
    }

    private String toCsvRow(Transaction transaction) {
        String type = transaction.getType() == TransactionType.INCOME ? "수입" : "지출";
        String amount = transaction.getAmount().stripTrailingZeros().toPlainString();

        return String.join(",",
                CsvParser.toCsvField(transaction.getTxnDate().toString()),
                CsvParser.toCsvField(type),
                CsvParser.toCsvField(transaction.getCategory().getName()),
                CsvParser.toCsvField(amount),
                CsvParser.toCsvField(transaction.getMerchant()),
                CsvParser.toCsvField(transaction.getMemo()));
    }
}
