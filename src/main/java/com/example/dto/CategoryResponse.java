package com.example.dto;

import com.example.domain.Category;
import com.example.domain.TransactionType;

public record CategoryResponse(Long id, String name, TransactionType type, String color, boolean deleted) {

    public static CategoryResponse from(Category category) {
        return new CategoryResponse(
                category.getId(),
                category.getName(),
                category.getType(),
                category.getColor(),
                category.isDeleted());
    }
}
