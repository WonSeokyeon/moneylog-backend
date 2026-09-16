package com.example.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

// type은 생성 후 변경할 수 없으므로 필드 자체를 두지 않는다(CLAUDE.md §5).
public record CategoryUpdateRequest(
        @NotBlank @Size(min = 1, max = 30) String name,
        @NotBlank @Pattern(regexp = "^#[0-9A-Fa-f]{6}$", message = "color는 #RRGGBB 형식이어야 합니다.") String color,
        Integer sortOrder
) {
}
