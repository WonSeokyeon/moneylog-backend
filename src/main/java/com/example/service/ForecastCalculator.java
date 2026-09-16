package com.example.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;

/**
 * CLAUDE.md 5장 「예측 계산 규칙」의 순수 계산 모음. DB·시간에 의존하지 않아 입력→출력만으로 단위 테스트한다.
 */
public final class ForecastCalculator {

    private static final BigDecimal ANOMALY_THRESHOLD = new BigDecimal("0.30");
    private static final int MIN_DAYS_ELAPSED_FOR_ANOMALY = 7;

    private ForecastCalculator() {
    }

    /** 대상 월 기준 asOf의 경과 일수. asOf가 대상 월 밖이면 그 달 전체가 지난 것으로 간주한다. */
    public static int daysElapsed(YearMonth targetMonth, LocalDate asOf) {
        YearMonth asOfMonth = YearMonth.from(asOf);
        if (asOfMonth.isAfter(targetMonth)) {
            return targetMonth.lengthOfMonth();
        }
        if (asOfMonth.isBefore(targetMonth)) {
            return 0;
        }
        return asOf.getDayOfMonth();
    }

    /** 직전 N개월 지출 합계 ÷ 그 기간의 실제 총 일수(개월 수가 아니다). */
    public static BigDecimal baselineDailyAvg(BigDecimal baselineExpenseSum, int baselineTotalDays) {
        if (baselineTotalDays == 0) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        return baselineExpenseSum.divide(BigDecimal.valueOf(baselineTotalDays), 2, RoundingMode.HALF_UP);
    }

    /** 확정 지출 + 일평균 × 남은 일수. daysElapsed가 daysInMonth와 같으면(과거 달) 남은 일수가 0이라 확정액과 같아진다. */
    public static BigDecimal projectedExpense(BigDecimal confirmedExpense, BigDecimal baselineDailyAvg,
                                               int daysInMonth, int daysElapsed) {
        int remainingDays = Math.max(0, daysInMonth - daysElapsed);
        return confirmedExpense
                .add(baselineDailyAvg.multiply(BigDecimal.valueOf(remainingDays)))
                .setScale(2, RoundingMode.HALF_UP);
    }

    /** 이번 달 해당 카테고리 지출을 하루 평균으로 환산해 한 달 치로 늘린 "현재 속도". */
    public static BigDecimal currentPace(BigDecimal categoryExpense, int daysElapsed, int daysInMonth) {
        if (daysElapsed == 0) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        return categoryExpense
                .divide(BigDecimal.valueOf(daysElapsed), 10, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(daysInMonth))
                .setScale(2, RoundingMode.HALF_UP);
    }

    /** (currentPace - baseline) / baseline. baseline이 0이면 정의되지 않으므로 null을 반환한다. */
    public static BigDecimal deltaRatio(BigDecimal currentPace, BigDecimal baseline) {
        if (baseline == null || baseline.compareTo(BigDecimal.ZERO) == 0) {
            return null;
        }
        return currentPace.subtract(baseline).divide(baseline, 4, RoundingMode.HALF_UP);
    }

    /** |deltaRatio| >= 0.30이고 baseline > 0일 때만 이상치로 본다. 월초(daysElapsed < 7)에는 호출하지 않는다. */
    public static boolean isAnomaly(BigDecimal deltaRatio, BigDecimal baseline) {
        if (deltaRatio == null || baseline == null || baseline.compareTo(BigDecimal.ZERO) <= 0) {
            return false;
        }
        return deltaRatio.abs().compareTo(ANOMALY_THRESHOLD) >= 0;
    }

    public static boolean shouldComputeAnomalies(int daysElapsed) {
        return daysElapsed >= MIN_DAYS_ELAPSED_FOR_ANOMALY;
    }
}
