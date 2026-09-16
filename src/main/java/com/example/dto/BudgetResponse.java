package com.example.dto;

import java.math.BigDecimal;

import com.example.domain.Category;

public record BudgetResponse(Long categoryId, String name, String color, BigDecimal amount) {

    public static BudgetResponse from(Category category, BigDecimal amount) {
        return new BudgetResponse(category.getId(), category.getName(), category.getColor(), amount);
    }
}
