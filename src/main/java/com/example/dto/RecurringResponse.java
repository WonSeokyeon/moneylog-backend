package com.example.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

import com.example.service.RecurringDetector;

public record RecurringResponse(String merchant, Long categoryId, BigDecimal medianAmount,
                                 int monthsSeen, LocalDate lastDate) {

    public static RecurringResponse from(RecurringDetector.Candidate candidate) {
        return new RecurringResponse(candidate.merchant(), candidate.categoryId(), candidate.medianAmount(),
                candidate.monthsSeen(), candidate.lastDate());
    }
}
