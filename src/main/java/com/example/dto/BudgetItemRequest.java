package com.example.dto;

import java.math.BigDecimal;

import jakarta.validation.constraints.NotNull;

public record BudgetItemRequest(
        @NotNull Long categoryId,
        BigDecimal amount
) {
}
