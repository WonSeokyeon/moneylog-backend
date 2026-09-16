package com.example.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

import com.example.domain.Transaction;
import com.example.domain.TransactionType;

public record TransactionResponse(
        Long id,
        TransactionType type,
        BigDecimal amount,
        LocalDate txnDate,
        String merchant,
        String memo,
        CategoryResponse category
) {

    public static TransactionResponse from(Transaction transaction) {
        return new TransactionResponse(
                transaction.getId(),
                transaction.getType(),
                transaction.getAmount(),
                transaction.getTxnDate(),
                transaction.getMerchant(),
                transaction.getMemo(),
                CategoryResponse.from(transaction.getCategory()));
    }
}
