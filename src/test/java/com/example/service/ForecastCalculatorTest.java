package com.example.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;

import org.junit.jupiter.api.Test;

class ForecastCalculatorTest {

    @Test
    void daysElapsed_같은_달이면_asOf의_일자를_그대로_쓴다() {
        YearMonth target = YearMonth.of(2026, 9);
        assertThat(ForecastCalculator.daysElapsed(target, LocalDate.of(2026, 9, 16))).isEqualTo(16);
    }

    @Test
    void daysElapsed_asOf가_대상월_이후면_그_달_전체_일수로_간주한다() {
        YearMonth target = YearMonth.of(2026, 8);
        assertThat(ForecastCalculator.daysElapsed(target, LocalDate.of(2026, 9, 1))).isEqualTo(31);
    }

    @Test
    void daysElapsed_asOf가_대상월_이전이면_0이다() {
        YearMonth target = YearMonth.of(2026, 10);
        assertThat(ForecastCalculator.daysElapsed(target, LocalDate.of(2026, 9, 30))).isEqualTo(0);
    }

    @Test
    void baselineDailyAvg는_지출합을_총일수로_나눈다() {
        BigDecimal result = ForecastCalculator.baselineDailyAvg(new BigDecimal("1000"), 10);
        assertThat(result).isEqualByComparingTo("100.00");
    }

    @Test
    void baselineDailyAvg_총일수가_0이면_0을_반환한다() {
        BigDecimal result = ForecastCalculator.baselineDailyAvg(new BigDecimal("1000"), 0);
        assertThat(result).isEqualByComparingTo("0.00");
    }

    @Test
    void projectedExpense는_확정액과_일평균곱한_남은일수의_합이다() {
        BigDecimal result = ForecastCalculator.projectedExpense(
                new BigDecimal("1842300.00"), new BigDecimal("85300.00"), 30, 15);
        // 1842300 + 85300 * 15 = 1842300 + 1279500 = 3121800.00
        assertThat(result).isEqualByComparingTo("3121800.00");
    }

    @Test
    void projectedExpense_과거달_조회시_확정액과_같다() {
        BigDecimal confirmed = new BigDecimal("500000.00");
        BigDecimal result = ForecastCalculator.projectedExpense(confirmed, new BigDecimal("10000.00"), 30, 30);
        assertThat(result).isEqualByComparingTo(confirmed);
    }

    @Test
    void currentPace는_경과일_기준_지출을_한달치로_환산한다() {
        BigDecimal result = ForecastCalculator.currentPace(new BigDecimal("100000"), 10, 30);
        // 100000 / 10 * 30 = 300000.00
        assertThat(result).isEqualByComparingTo("300000.00");
    }

    @Test
    void currentPace_경과일이_0이면_0이다() {
        assertThat(ForecastCalculator.currentPace(new BigDecimal("100000"), 0, 30))
                .isEqualByComparingTo("0.00");
    }

    @Test
    void deltaRatio_baseline이_0이면_null이다() {
        assertThat(ForecastCalculator.deltaRatio(new BigDecimal("100"), BigDecimal.ZERO)).isNull();
    }

    @Test
    void deltaRatio는_currentPace와_baseline의_상대_차이다() {
        BigDecimal result = ForecastCalculator.deltaRatio(new BigDecimal("824000.00"), new BigDecimal("615000.00"));
        // (824000-615000)/615000 = 0.33983...
        assertThat(result).isEqualByComparingTo("0.3398");
    }

    @Test
    void isAnomaly_30퍼센트는_이상치다() {
        BigDecimal deltaRatio = ForecastCalculator.deltaRatio(new BigDecimal("130"), new BigDecimal("100"));
        assertThat(ForecastCalculator.isAnomaly(deltaRatio, new BigDecimal("100"))).isTrue();
    }

    @Test
    void isAnomaly_29퍼센트는_이상치가_아니다() {
        BigDecimal deltaRatio = ForecastCalculator.deltaRatio(new BigDecimal("129"), new BigDecimal("100"));
        assertThat(ForecastCalculator.isAnomaly(deltaRatio, new BigDecimal("100"))).isFalse();
    }

    @Test
    void isAnomaly_31퍼센트는_이상치다() {
        BigDecimal deltaRatio = ForecastCalculator.deltaRatio(new BigDecimal("131"), new BigDecimal("100"));
        assertThat(ForecastCalculator.isAnomaly(deltaRatio, new BigDecimal("100"))).isTrue();
    }

    @Test
    void isAnomaly_baseline이_0이하면_이상치로_보지_않는다() {
        assertThat(ForecastCalculator.isAnomaly(new BigDecimal("0.5"), BigDecimal.ZERO)).isFalse();
    }

    @Test
    void shouldComputeAnomalies_경과일_7미만이면_false() {
        assertThat(ForecastCalculator.shouldComputeAnomalies(6)).isFalse();
        assertThat(ForecastCalculator.shouldComputeAnomalies(7)).isTrue();
    }
}
