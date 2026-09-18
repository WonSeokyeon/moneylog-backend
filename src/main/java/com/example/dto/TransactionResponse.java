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
        Double latitude,
        Double longitude,
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
                transaction.getLatitude(),
                transaction.getLongitude(),
                CategoryResponse.from(transaction.getCategory()));
    }
}
