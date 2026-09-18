package com.example.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.domain.Budget;
import com.example.domain.BudgetRepository;
import com.example.domain.Category;
import com.example.domain.Transaction;
import com.example.domain.TransactionRepository;
import com.example.domain.TransactionType;
import com.example.domain.User;

@Service
public class StatsService {

    public record Summary(BigDecimal income, BigDecimal expense, BigDecimal net) {
    }

    public record CategoryAmount(Long categoryId, String name, String color, boolean deleted,
                                  BigDecimal amount, BigDecimal ratio) {
    }

    public record DailyAmount(LocalDate date, BigDecimal expense, BigDecimal income) {
    }

    /** basisMonths == 0이면 forecast 전체가 null이어야 한다(CLAUDE.md 5장) — 그 판단은 이 레코드 밖(호출자)에서 한다. */
    public record Forecast(BigDecimal confirmedExpense, BigDecimal projectedExpense, BigDecimal baselineDailyAvg,
                            int daysElapsed, int daysInMonth, int basisMonths) {
    }

    public record Anomaly(Long categoryId, String name, BigDecimal currentPace, BigDecimal baseline,
                           BigDecimal deltaRatio) {
    }

    public record BudgetStat(Long categoryId, String name, BigDecimal budget, BigDecimal spent,
                              BigDecimal usageRatio, boolean exceeded) {
    }

    public record MonthlyStats(String yearMonth, Summary summary, List<CategoryAmount> byCategory,
                                List<DailyAmount> daily, Forecast forecast, List<Anomaly> anomalies,
                                List<BudgetStat> budgetStats) {
    }

    private final TransactionRepository transactionRepository;
    private final BudgetRepository budgetRepository;

    public StatsService(TransactionRepository transactionRepository, BudgetRepository budgetRepository) {
        this.transactionRepository = transactionRepository;
        this.budgetRepository = budgetRepository;
    }

    private List<Budget> findBudgets(User user, String yearMonth) {
        return budgetRepository.findByUserAndYearMonth(user, yearMonth);
    }

    // 대시보드 한 화면이 필요로 하는 요약·카테고리별·일별·예측·이상치·예산 소진율을 한 번에 조합한다
    // (CLAUDE.md 5장 "대시보드 집계는 엔드포인트 하나로 묶는다" — 그 조합 로직은 controller가 아니라 여기 있어야 한다).
    @Transactional(readOnly = true)
    public MonthlyStats getMonthlyStats(User user, String yearMonth, LocalDate asOf) {
        YearMonth targetMonth = YearMonth.parse(yearMonth);

        List<Transaction> monthTransactions = findMonthTransactions(user, targetMonth);
        Summary summary = summarize(monthTransactions);
        List<CategoryAmount> byCategory = byCategory(monthTransactions, summary.expense());
        List<DailyAmount> daily = daily(monthTransactions, targetMonth);

        List<Transaction> baselineTransactions = findBaselineTransactions(user, targetMonth);
        Forecast forecast = computeForecast(targetMonth, asOf, summary.expense(), baselineTransactions);

        int daysInMonth = targetMonth.lengthOfMonth();
        int daysElapsed = ForecastCalculator.daysElapsed(targetMonth, asOf);
        List<Anomaly> anomalies = computeAnomalies(
                targetMonth, daysElapsed, daysInMonth, monthTransactions, baselineTransactions);

        List<Budget> budgets = findBudgets(user, yearMonth);
        List<BudgetStat> budgetStats = budgetStats(byCategory, budgets);

        return new MonthlyStats(yearMonth, summary, byCategory, daily, forecast, anomalies, budgetStats);
    }

    /** recurring 엔드포인트 전용. 임의의 날짜 범위로 거래를 조회한다(카테고리 join fetch 포함). */
    @Transactional(readOnly = true)
    public List<Transaction> findTransactionsBetween(User user, LocalDate from, LocalDate to) {
        return transactionRepository.findForMonth(user, from, to);
    }

    /**
     * byCategory(지출이 있던 카테고리)와 budgets(예산이 설정된 카테고리)의 합집합을 대상으로 한다 —
     * 예산만 잡고 아직 안 쓴 카테고리도, 예산 없이 지출만 있는 카테고리도 모두 보여야 한다.
     * budget이 0(=예산 미설정)이면 usageRatio는 0으로 고정한다(Infinity/NaN 방지).
     */
    private List<BudgetStat> budgetStats(List<CategoryAmount> byCategory, List<Budget> budgets) {
        Map<Long, BigDecimal> spentByCategory = new LinkedHashMap<>();
        Map<Long, String> nameByCategory = new LinkedHashMap<>();
        for (CategoryAmount ca : byCategory) {
            spentByCategory.put(ca.categoryId(), ca.amount());
            nameByCategory.put(ca.categoryId(), ca.name());
        }

        Map<Long, BigDecimal> budgetByCategory = new LinkedHashMap<>();
        for (Budget budget : budgets) {
            budgetByCategory.put(budget.getCategory().getId(), budget.getAmount());
            nameByCategory.putIfAbsent(budget.getCategory().getId(), budget.getCategory().getName());
        }

        Set<Long> allCategoryIds = new LinkedHashSet<>();
        allCategoryIds.addAll(spentByCategory.keySet());
        allCategoryIds.addAll(budgetByCategory.keySet());

        List<BudgetStat> result = new ArrayList<>();
        for (Long categoryId : allCategoryIds) {
            BigDecimal budgetAmount = budgetByCategory
                    .getOrDefault(categoryId, BigDecimal.ZERO)
                    .setScale(2, RoundingMode.HALF_UP);
            BigDecimal spent = spentByCategory
                    .getOrDefault(categoryId, BigDecimal.ZERO)
                    .setScale(2, RoundingMode.HALF_UP);

            BigDecimal usageRatio;
            boolean exceeded;
            if (budgetAmount.compareTo(BigDecimal.ZERO) == 0) {
                usageRatio = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
                exceeded = false;
            } else {
                usageRatio = spent.divide(budgetAmount, 2, RoundingMode.HALF_UP);
                exceeded = spent.compareTo(budgetAmount) > 0;
            }

            result.add(new BudgetStat(categoryId, nameByCategory.get(categoryId), budgetAmount, spent,
                    usageRatio, exceeded));
        }
        return result;
    }

    private List<Transaction> findMonthTransactions(User user, YearMonth yearMonth) {
        return transactionRepository.findForMonth(user, yearMonth.atDay(1), yearMonth.atEndOfMonth());
    }

    /** 당월을 제외한 직전 3개월(캘린더 기준) 거래를 한 번에 조회한다. */
    private List<Transaction> findBaselineTransactions(User user, YearMonth targetMonth) {
        YearMonth earliest = targetMonth.minusMonths(3);
        YearMonth latest = targetMonth.minusMonths(1);
        return transactionRepository.findForMonth(user, earliest.atDay(1), latest.atEndOfMonth());
    }

    /**
     * 기준선·런레이트를 계산한다. 직전 3개월 중 거래가 있는 달만 합계·일수에 포함한다("1~2개월치만 있으면
     * 있는 만큼 계산"). 3개월 모두 거래가 없으면 null을 반환해 호출자가 forecast 전체를 비우게 한다.
     */
    private Forecast computeForecast(YearMonth targetMonth, LocalDate asOf,
                                     BigDecimal confirmedExpense, List<Transaction> baselineTransactions) {
        List<YearMonth> baselineMonths = baselineMonths(targetMonth);

        int basisMonths = 0;
        int totalDays = 0;
        BigDecimal totalExpense = BigDecimal.ZERO;

        for (YearMonth month : baselineMonths) {
            List<Transaction> monthTransactions = transactionsInMonth(baselineTransactions, month);
            if (monthTransactions.isEmpty()) {
                continue;
            }
            basisMonths++;
            totalDays += month.lengthOfMonth();
            totalExpense = totalExpense.add(summarize(monthTransactions).expense());
        }

        if (basisMonths == 0) {
            return null;
        }

        BigDecimal baselineDailyAvg = ForecastCalculator.baselineDailyAvg(totalExpense, totalDays);
        int daysInMonth = targetMonth.lengthOfMonth();
        int daysElapsed = ForecastCalculator.daysElapsed(targetMonth, asOf);
        BigDecimal projectedExpense = ForecastCalculator.projectedExpense(
                confirmedExpense, baselineDailyAvg, daysInMonth, daysElapsed);

        return new Forecast(confirmedExpense, projectedExpense, baselineDailyAvg, daysElapsed, daysInMonth, basisMonths);
    }

    /**
     * 카테고리별 "현재 속도"와 "기준선"을 비교해 이상치를 찾는다. 기준선도 forecast와 같은 원칙(거래가 있는
     * 달만 사용)을 따르되, 카테고리 단위로 다시 계산한다. daysElapsed < 7이면 호출 자체를 건너뛴다(월초 노이즈 방지).
     */
    private List<Anomaly> computeAnomalies(YearMonth targetMonth, int daysElapsed, int daysInMonth,
                                           List<Transaction> currentMonthTransactions,
                                           List<Transaction> baselineTransactions) {
        if (!ForecastCalculator.shouldComputeAnomalies(daysElapsed)) {
            return List.of();
        }

        Map<Long, BigDecimal> currentExpenseByCategory = new LinkedHashMap<>();
        Map<Long, Category> categoriesById = new LinkedHashMap<>();
        for (Transaction t : currentMonthTransactions) {
            if (t.getType() != TransactionType.EXPENSE) {
                continue;
            }
            currentExpenseByCategory.merge(t.getCategory().getId(), t.getAmount(), BigDecimal::add);
            categoriesById.putIfAbsent(t.getCategory().getId(), t.getCategory());
        }

        Map<Long, BigDecimal> baselineExpenseByCategory = new LinkedHashMap<>();
        int totalDaysUsed = 0;
        for (YearMonth month : baselineMonths(targetMonth)) {
            List<Transaction> monthTransactions = transactionsInMonth(baselineTransactions, month);
            if (monthTransactions.isEmpty()) {
                continue;
            }
            totalDaysUsed += month.lengthOfMonth();
            for (Transaction t : monthTransactions) {
                if (t.getType() != TransactionType.EXPENSE) {
                    continue;
                }
                baselineExpenseByCategory.merge(t.getCategory().getId(), t.getAmount(), BigDecimal::add);
                categoriesById.putIfAbsent(t.getCategory().getId(), t.getCategory());
            }
        }

        List<Anomaly> anomalies = new ArrayList<>();
        for (Map.Entry<Long, BigDecimal> entry : currentExpenseByCategory.entrySet()) {
            Long categoryId = entry.getKey();
            BigDecimal categoryExpense = entry.getValue();
            BigDecimal baselineSum = baselineExpenseByCategory.getOrDefault(categoryId, BigDecimal.ZERO);

            BigDecimal baselineDailyAvg = totalDaysUsed == 0
                    ? BigDecimal.ZERO
                    : baselineSum.divide(BigDecimal.valueOf(totalDaysUsed), 10, RoundingMode.HALF_UP);
            BigDecimal baseline = baselineDailyAvg
                    .multiply(BigDecimal.valueOf(daysInMonth))
                    .setScale(2, RoundingMode.HALF_UP);

            BigDecimal currentPace = ForecastCalculator.currentPace(categoryExpense, daysElapsed, daysInMonth);
            BigDecimal deltaRatio = ForecastCalculator.deltaRatio(currentPace, baseline);

            if (ForecastCalculator.isAnomaly(deltaRatio, baseline)) {
                Category category = categoriesById.get(categoryId);
                anomalies.add(new Anomaly(categoryId, category.getName(), currentPace, baseline, deltaRatio));
            }
        }
        return anomalies;
    }

    private List<YearMonth> baselineMonths(YearMonth targetMonth) {
        return List.of(targetMonth.minusMonths(1), targetMonth.minusMonths(2), targetMonth.minusMonths(3));
    }

    private List<Transaction> transactionsInMonth(List<Transaction> transactions, YearMonth month) {
        return transactions.stream()
                .filter(t -> YearMonth.from(t.getTxnDate()).equals(month))
                .toList();
    }

    private Summary summarize(List<Transaction> transactions) {
        BigDecimal income = sumByType(transactions, TransactionType.INCOME);
        BigDecimal expense = sumByType(transactions, TransactionType.EXPENSE);
        return new Summary(income, expense, income.subtract(expense));
    }

    // 삭제된 카테고리도 과거 지출 집계에서 빠지지 않는다 — 카테고리 자체가 아니라 거래의 category 참조로 그룹핑한다.
    private List<CategoryAmount> byCategory(List<Transaction> transactions, BigDecimal totalExpense) {
        Map<Long, BigDecimal> sums = new LinkedHashMap<>();
        Map<Long, Category> categories = new LinkedHashMap<>();

        for (Transaction t : transactions) {
            if (t.getType() != TransactionType.EXPENSE) {
                continue;
            }
            Category category = t.getCategory();
            sums.merge(category.getId(), t.getAmount(), BigDecimal::add);
            categories.putIfAbsent(category.getId(), category);
        }

        List<CategoryAmount> result = new ArrayList<>();
        for (Map.Entry<Long, BigDecimal> entry : sums.entrySet()) {
            Category category = categories.get(entry.getKey());
            BigDecimal amount = entry.getValue().setScale(2, RoundingMode.HALF_UP);
            BigDecimal ratio = totalExpense.compareTo(BigDecimal.ZERO) == 0
                    ? BigDecimal.ZERO.setScale(4, RoundingMode.HALF_UP)
                    : amount.divide(totalExpense, 4, RoundingMode.HALF_UP);
            result.add(new CategoryAmount(
                    category.getId(), category.getName(), category.getColor(),
                    category.isDeleted(), amount, ratio));
        }
        return result;
    }

    // COALESCE로는 해결되지 않는다 — GROUP BY는 거래가 있는 날만 반환하므로 빈 날은 그룹 자체가 없다.
    // 1일부터 말일까지 직접 순회해 없는 날짜를 0.00으로 채운다.
    private List<DailyAmount> daily(List<Transaction> transactions, YearMonth yearMonth) {
        Map<LocalDate, BigDecimal> expenseByDate = new LinkedHashMap<>();
        Map<LocalDate, BigDecimal> incomeByDate = new LinkedHashMap<>();

        for (Transaction t : transactions) {
            Map<LocalDate, BigDecimal> target =
                    t.getType() == TransactionType.EXPENSE ? expenseByDate : incomeByDate;
            target.merge(t.getTxnDate(), t.getAmount(), BigDecimal::add);
        }

        List<DailyAmount> result = new ArrayList<>();
        int daysInMonth = yearMonth.lengthOfMonth();
        for (int day = 1; day <= daysInMonth; day++) {
            LocalDate date = yearMonth.atDay(day);
            BigDecimal expense = expenseByDate.getOrDefault(date, BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);
            BigDecimal income = incomeByDate.getOrDefault(date, BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);
            result.add(new DailyAmount(date, expense, income));
        }
        return result;
    }

    private BigDecimal sumByType(List<Transaction> transactions, TransactionType type) {
        BigDecimal sum = BigDecimal.ZERO;
        for (Transaction t : transactions) {
            if (t.getType() == type) {
                sum = sum.add(t.getAmount());
            }
        }
        return sum.setScale(2, RoundingMode.HALF_UP);
    }
}
