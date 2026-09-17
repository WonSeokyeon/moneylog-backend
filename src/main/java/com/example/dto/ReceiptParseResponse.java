package com.example.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

// 인식하지 못한 필드는 null로 내려간다 — 프론트는 채워진 필드만 폼에 반영하고 나머지는 비워둔다(TXN-13).
public record ReceiptParseResponse(
        LocalDate txnDate,
        Long categoryId,
        String categoryName,
        String merchant,
        BigDecimal amount
) {
}
