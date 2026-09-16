package com.example.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import com.example.domain.Transaction;

/**
 * CLAUDE.md 5장의 고정지출 감지 규칙(정규화 상호 + 3개월 연속 + 금액 중앙값 ±10%)을 순수 계산으로 구현한다.
 * 결과를 DB에 저장하지 않는다 — 화면에 "이거 고정지출로 보여요"만 보여주는 용도다.
 */
public final class RecurringDetector {

    private static final BigDecimal LOWER_RATIO = new BigDecimal("0.90");
    private static final BigDecimal UPPER_RATIO = new BigDecimal("1.10");

    private RecurringDetector() {
    }

    public record Candidate(String merchant, Long categoryId, BigDecimal medianAmount,
                             int monthsSeen, LocalDate lastDate) {
    }

    /** 공백·괄호·숫자를 제거하고 소문자로 바꾼다. merchant가 비어 있으면 빈 문자열을 반환한다. */
    public static String normalize(String merchant) {
        if (merchant == null) {
            return "";
        }
        return merchant.toLowerCase()
                .trim()
                .replaceAll("\\s+", "")
                .replaceAll("[()\\[\\]{}]", "")
                .replaceAll("[0-9]", "");
    }

    /**
     * transactions는 이미 targetMonths(정확히 3개월) 범위로 조회된 거래 목록이어야 한다.
     * merchant가 비어 있는 거래는 제외하고, 정규화 상호가 targetMonths 각각에 1건 이상 있으며
     * 금액이 전부 중앙값의 90~110% 이내인 경우만 결과에 담는다.
     */
    public static List<Candidate> detect(List<Transaction> transactions, List<YearMonth> targetMonths) {
        Map<String, List<Transaction>> byNormalizedMerchant = new LinkedHashMap<>();
        for (Transaction t : transactions) {
            String merchant = t.getMerchant();
            if (merchant == null || merchant.isBlank()) {
                continue;
            }
            String normalized = normalize(merchant);
            if (normalized.isEmpty()) {
                continue;
            }
            byNormalizedMerchant.computeIfAbsent(normalized, key -> new ArrayList<>()).add(t);
        }

        List<Candidate> result = new ArrayList<>();
        for (List<Transaction> group : byNormalizedMerchant.values()) {
            Set<YearMonth> monthsPresent = group.stream()
                    .map(t -> YearMonth.from(t.getTxnDate()))
                    .collect(Collectors.toSet());

            boolean everyTargetMonthPresent = targetMonths.stream().allMatch(monthsPresent::contains);
            if (!everyTargetMonthPresent) {
                continue;
            }

            List<BigDecimal> amounts = group.stream()
                    .map(Transaction::getAmount)
                    .sorted()
                    .toList();
            BigDecimal median = median(amounts);
            BigDecimal lowerBound = median.multiply(LOWER_RATIO);
            BigDecimal upperBound = median.multiply(UPPER_RATIO);

            boolean allWithinTenPercent = amounts.stream()
                    .allMatch(amount -> amount.compareTo(lowerBound) >= 0 && amount.compareTo(upperBound) <= 0);
            if (!allWithinTenPercent) {
                continue;
            }

            Transaction latest = group.stream()
                    .max(Comparator.comparing(Transaction::getTxnDate))
                    .orElseThrow();

            result.add(new Candidate(
                    latest.getMerchant(),
                    latest.getCategory().getId(),
                    median.setScale(2, RoundingMode.HALF_UP),
                    monthsPresent.size(),
                    latest.getTxnDate()));
        }
        return result;
    }

    private static BigDecimal median(List<BigDecimal> sortedAmounts) {
        int size = sortedAmounts.size();
        if (size % 2 == 1) {
            return sortedAmounts.get(size / 2);
        }
        BigDecimal lower = sortedAmounts.get(size / 2 - 1);
        BigDecimal upper = sortedAmounts.get(size / 2);
        return lower.add(upper).divide(BigDecimal.valueOf(2), 2, RoundingMode.HALF_UP);
    }
}
