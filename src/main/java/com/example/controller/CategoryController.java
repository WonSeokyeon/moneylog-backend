package com.example.controller;

import java.util.List;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.example.domain.TransactionType;
import com.example.domain.User;
import com.example.dto.ApiResponse;
import com.example.dto.CategoryCreateRequest;
import com.example.dto.CategoryResponse;
import com.example.dto.CategoryUpdateRequest;
import com.example.service.CategoryService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/categories")
public class CategoryController {

    private final CategoryService categoryService;

    public CategoryController(CategoryService categoryService) {
        this.categoryService = categoryService;
    }

    @GetMapping
    public ApiResponse<List<CategoryResponse>> list(@AuthenticationPrincipal User user,
                                                      @RequestParam(required = false) TransactionType type) {
        List<CategoryResponse> categories = categoryService.list(user, type).stream()
                .map(CategoryResponse::from)
                .toList();

        return ApiResponse.success(categories);
    }

    @PostMapping
    public ApiResponse<CategoryResponse> create(@AuthenticationPrincipal User user,
                                                 @Valid @RequestBody CategoryCreateRequest request) {
        var category = categoryService.create(
                user, request.name(), request.type(), request.color(), request.sortOrder());
        return ApiResponse.success(CategoryResponse.from(category));
    }

    @PutMapping("/{id}")
    public ApiResponse<CategoryResponse> update(@AuthenticationPrincipal User user,
                                                 @PathVariable Long id,
                                                 @Valid @RequestBody CategoryUpdateRequest request) {
        var category = categoryService.update(
                user, id, request.name(), request.color(), request.sortOrder());
        return ApiResponse.success(CategoryResponse.from(category));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@AuthenticationPrincipal User user, @PathVariable Long id) {
        categoryService.softDelete(user, id);
        return ApiResponse.success(null);
    }
}
