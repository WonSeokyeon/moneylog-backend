package com.example.controller;

import java.util.List;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.domain.CategoryRepository;
import com.example.domain.User;
import com.example.dto.ApiResponse;
import com.example.dto.CategoryResponse;

@RestController
@RequestMapping("/api/v1/categories")
public class CategoryController {

    private final CategoryRepository categoryRepository;

    public CategoryController(CategoryRepository categoryRepository) {
        this.categoryRepository = categoryRepository;
    }

    // POST/PUT/DELETE, ?type= 필터는 Phase4에서 이어서 추가한다.
    @GetMapping
    public ApiResponse<List<CategoryResponse>> list(@AuthenticationPrincipal User user) {
        List<CategoryResponse> categories = categoryRepository
                .findByUserAndDeletedAtIsNullOrderBySortOrderAscIdAsc(user)
                .stream()
                .map(CategoryResponse::from)
                .toList();

        return ApiResponse.success(categories);
    }
}
