package com.example.controller;

import java.util.List;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.example.domain.User;
import com.example.dto.ApiResponse;
import com.example.dto.BudgetResponse;
import com.example.dto.BudgetUpsertRequest;
import com.example.service.BudgetService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/budgets")
public class BudgetController {

    private final BudgetService budgetService;

    public BudgetController(BudgetService budgetService) {
        this.budgetService = budgetService;
    }

    @GetMapping
    public ApiResponse<List<BudgetResponse>> list(@AuthenticationPrincipal User user,
                                                    @RequestParam String yearMonth) {
        return ApiResponse.success(budgetService.list(user, yearMonth));
    }

    @PutMapping
    public ApiResponse<List<BudgetResponse>> upsert(@AuthenticationPrincipal User user,
                                                      @Valid @RequestBody BudgetUpsertRequest request) {
        return ApiResponse.success(budgetService.upsert(user, request));
    }
}
