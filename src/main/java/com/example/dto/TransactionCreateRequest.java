package com.example.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

import com.example.domain.TransactionType;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record TransactionCreateRequest(
        @NotNull TransactionType type,

        @NotNull
        @DecimalMin(value = "0.01", message = "amount는 0보다 커야 합니다.")
        @DecimalMax(value = "9999999999999.99", message = "amount가 허용 범위를 초과했습니다.")
        BigDecimal amount,

        @NotNull LocalDate txnDate,
        @NotNull Long categoryId,
        @Size(max = 100) String merchant,
        @Size(max = 500) String memo
) {
}
