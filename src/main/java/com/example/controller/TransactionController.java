package com.example.controller;

import java.time.LocalDate;

import org.springframework.data.domain.Page;
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

import com.example.domain.Transaction;
import com.example.domain.TransactionType;
import com.example.domain.User;
import com.example.dto.ApiResponse;
import com.example.dto.PageResponse;
import com.example.dto.TransactionCreateRequest;
import com.example.dto.TransactionResponse;
import com.example.dto.TransactionUpdateRequest;
import com.example.service.TransactionService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/transactions")
public class TransactionController {

    private final TransactionService transactionService;

    public TransactionController(TransactionService transactionService) {
        this.transactionService = transactionService;
    }

    @GetMapping
    public ApiResponse<PageResponse<TransactionResponse>> list(
            @AuthenticationPrincipal User user,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "txnDate,desc") String sort,
            @RequestParam(required = false) LocalDate from,
            @RequestParam(required = false) LocalDate to,
            @RequestParam(required = false) TransactionType type,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) String keyword) {

        String[] sortParts = sort.split(",");
        String sortField = sortParts[0];
        String sortDirection = sortParts.length > 1 ? sortParts[1] : "desc";

        Page<Transaction> result = transactionService.list(
                user, from, to, type, categoryId, keyword, page, size, sortField, sortDirection);

        return ApiResponse.success(PageResponse.from(result.map(TransactionResponse::from)));
    }

    @PostMapping
    public ApiResponse<TransactionResponse> create(@AuthenticationPrincipal User user,
                                                    @Valid @RequestBody TransactionCreateRequest request) {
        Transaction transaction = transactionService.create(
                user, request.type(), request.amount(), request.txnDate(),
                request.categoryId(), request.merchant(), request.memo(),
                request.latitude(), request.longitude());
        return ApiResponse.success(TransactionResponse.from(transaction));
    }

    @GetMapping("/{id}")
    public ApiResponse<TransactionResponse> get(@AuthenticationPrincipal User user, @PathVariable Long id) {
        return ApiResponse.success(TransactionResponse.from(transactionService.get(user, id)));
    }

    @PutMapping("/{id}")
    public ApiResponse<TransactionResponse> update(@AuthenticationPrincipal User user,
                                                     @PathVariable Long id,
                                                     @Valid @RequestBody TransactionUpdateRequest request) {
        Transaction transaction = transactionService.update(
                user, id, request.type(), request.amount(), request.txnDate(),
                request.categoryId(), request.merchant(), request.memo(),
                request.latitude(), request.longitude());
        return ApiResponse.success(TransactionResponse.from(transaction));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@AuthenticationPrincipal User user, @PathVariable Long id) {
        transactionService.softDelete(user, id);
        return ApiResponse.success(null);
    }
}
