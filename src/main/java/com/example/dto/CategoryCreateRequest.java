package com.example.dto;

import com.example.domain.TransactionType;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CategoryCreateRequest(
        @NotBlank @Size(min = 1, max = 30) String name,
        @NotNull TransactionType type,
        @NotBlank @Pattern(regexp = "^#[0-9A-Fa-f]{6}$", message = "color는 #RRGGBB 형식이어야 합니다.") String color,
        Integer sortOrder
) {
}
