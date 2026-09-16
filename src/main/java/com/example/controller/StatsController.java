package com.example.controller;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.example.domain.Budget;
import com.example.domain.Transaction;
import com.example.domain.User;
import com.example.dto.ApiResponse;
import com.example.dto.MonthlyStatsResponse;
import com.example.dto.RecurringResponse;
import com.example.service.ForecastCalculator;
import com.example.service.RecurringDetector;
import com.example.service.StatsService;

@RestController
@RequestMapping("/api/v1/stats")
public class StatsController {

    private final StatsService statsService;

    public StatsController(StatsService statsService) {
        this.statsService = statsService;
    }

    // yearMonth/asOf는 요청 파라미터로만 받는다. "이번 달"·"오늘"을 서버가 판정하지 않는다(CLAUDE.md 4장).
    @GetMapping("/monthly")
    public ApiResponse<MonthlyStatsResponse> monthly(@AuthenticationPrincipal User user,
                                                       @RequestParam String yearMonth,
                                                       @RequestParam LocalDate asOf) {
        YearMonth targetMonth = YearMonth.parse(yearMonth);

        List<Transaction> monthTransactions = statsService.findMonthTransactions(user, targetMonth);
        StatsService.Summary summary = statsService.summarize(monthTransactions);
        List<StatsService.CategoryAmount> byCategory =
                statsService.byCategory(monthTransactions, summary.expense());
        List<StatsService.DailyAmount> daily = statsService.daily(monthTransactions, targetMonth);

        List<Transaction> baselineTransactions = statsService.findBaselineTransactions(user, targetMonth);
        StatsService.Forecast forecast =
                statsService.computeForecast(targetMonth, asOf, summary.expense(), baselineTransactions);

        int daysInMonth = targetMonth.lengthOfMonth();
        int daysElapsed = ForecastCalculator.daysElapsed(targetMonth, asOf);
        List<StatsService.Anomaly> anomalies = statsService.computeAnomalies(
                targetMonth, daysElapsed, daysInMonth, monthTransactions, baselineTransactions);

        List<Budget> budgets = statsService.findBudgets(user, yearMonth);
        List<StatsService.BudgetStat> budgetStats = statsService.budgetStats(byCategory, budgets);

        MonthlyStatsResponse response = new MonthlyStatsResponse(
                yearMonth,
                MonthlyStatsResponse.SummaryResponse.from(summary),
                byCategory.stream().map(MonthlyStatsResponse.CategoryStatResponse::from).toList(),
                daily.stream().map(MonthlyStatsResponse.DailyStatResponse::from).toList(),
                MonthlyStatsResponse.ForecastResponse.from(forecast),
                anomalies.stream().map(MonthlyStatsResponse.AnomalyResponse::from).toList(),
                budgetStats.stream().map(MonthlyStatsResponse.BudgetStatResponse::from).toList());

        return ApiResponse.success(response);
    }

    // 최근 3개월(당월 포함) 스캔이라 비용이 커 monthly와 분리한다(CLAUDE.md 5장).
    @GetMapping("/recurring")
    public ApiResponse<List<RecurringResponse>> recurring(@AuthenticationPrincipal User user,
                                                            @RequestParam LocalDate asOf) {
        YearMonth currentMonth = YearMonth.from(asOf);
        List<YearMonth> recentMonths = List.of(
                currentMonth, currentMonth.minusMonths(1), currentMonth.minusMonths(2));

        LocalDate from = currentMonth.minusMonths(2).atDay(1);
        List<Transaction> recentTransactions = statsService.findTransactionsBetween(user, from, asOf);

        List<RecurringDetector.Candidate> candidates =
                RecurringDetector.detect(recentTransactions, recentMonths);

        return ApiResponse.success(candidates.stream().map(RecurringResponse::from).toList());
    }
}
