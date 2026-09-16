package com.example.dto;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;

public record BudgetUpsertRequest(
        @NotBlank @Pattern(regexp = "^\\d{4}-\\d{2}$", message = "yearMonth는 yyyy-MM 형식이어야 합니다.") String yearMonth,
        @NotEmpty @Valid List<BudgetItemRequest> items
) {
}
