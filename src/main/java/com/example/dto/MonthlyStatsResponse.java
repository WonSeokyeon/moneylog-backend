package com.example.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import com.example.service.StatsService;

public record MonthlyStatsResponse(
        String yearMonth,
        SummaryResponse summary,
        List<CategoryStatResponse> byCategory,
        List<DailyStatResponse> daily,
        ForecastResponse forecast,
        List<AnomalyResponse> anomalies,
        List<BudgetStatResponse> budgets
) {

    public static MonthlyStatsResponse from(StatsService.MonthlyStats stats) {
        return new MonthlyStatsResponse(
                stats.yearMonth(),
                SummaryResponse.from(stats.summary()),
                stats.byCategory().stream().map(CategoryStatResponse::from).toList(),
                stats.daily().stream().map(DailyStatResponse::from).toList(),
                ForecastResponse.from(stats.forecast()),
                stats.anomalies().stream().map(AnomalyResponse::from).toList(),
                stats.budgetStats().stream().map(BudgetStatResponse::from).toList());
    }

    public record SummaryResponse(BigDecimal income, BigDecimal expense, BigDecimal net) {
        public static SummaryResponse from(StatsService.Summary summary) {
            return new SummaryResponse(summary.income(), summary.expense(), summary.net());
        }
    }

    public record CategoryStatResponse(Long categoryId, String name, String color, boolean deleted,
                                        BigDecimal amount, BigDecimal ratio) {
        public static CategoryStatResponse from(StatsService.CategoryAmount categoryAmount) {
            return new CategoryStatResponse(categoryAmount.categoryId(), categoryAmount.name(),
                    categoryAmount.color(), categoryAmount.deleted(), categoryAmount.amount(),
                    categoryAmount.ratio());
        }
    }

    public record DailyStatResponse(LocalDate date, BigDecimal expense, BigDecimal income) {
        public static DailyStatResponse from(StatsService.DailyAmount dailyAmount) {
            return new DailyStatResponse(dailyAmount.date(), dailyAmount.expense(), dailyAmount.income());
        }
    }

    // 직전 3개월 데이터가 전혀 없으면 forecast 전체가 null이다(CLAUDE.md 5장).
    public record ForecastResponse(BigDecimal confirmedExpense, BigDecimal projectedExpense,
                                    BigDecimal baselineDailyAvg, int daysElapsed, int daysInMonth,
                                    int basisMonths) {
        public static ForecastResponse from(StatsService.Forecast forecast) {
            if (forecast == null) {
                return null;
            }
            return new ForecastResponse(forecast.confirmedExpense(), forecast.projectedExpense(),
                    forecast.baselineDailyAvg(), forecast.daysElapsed(), forecast.daysInMonth(),
                    forecast.basisMonths());
        }
    }

    public record AnomalyResponse(Long categoryId, String name, BigDecimal currentPace, BigDecimal baseline,
                                   BigDecimal deltaRatio) {
        public static AnomalyResponse from(StatsService.Anomaly anomaly) {
            return new AnomalyResponse(anomaly.categoryId(), anomaly.name(), anomaly.currentPace(),
                    anomaly.baseline(), anomaly.deltaRatio());
        }
    }

    public record BudgetStatResponse(Long categoryId, String name, BigDecimal budget, BigDecimal spent,
                                      BigDecimal usageRatio, boolean exceeded) {
        public static BudgetStatResponse from(StatsService.BudgetStat budgetStat) {
            return new BudgetStatResponse(budgetStat.categoryId(), budgetStat.name(), budgetStat.budget(),
                    budgetStat.spent(), budgetStat.usageRatio(), budgetStat.exceeded());
        }
    }
}
