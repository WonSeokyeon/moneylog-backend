package com.example.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.example.domain.Category;
import com.example.domain.Transaction;
import com.example.domain.TransactionType;
import com.example.domain.User;

class RecurringDetectorTest {

    private final User user = new User("recurring-test@example.com", "hashed", "테스터");
    private final Category category = new Category(user, "문화/여가", TransactionType.EXPENSE, "#EC4899", 0);
    private final List<YearMonth> threeMonths = List.of(
            YearMonth.of(2026, 9), YearMonth.of(2026, 8), YearMonth.of(2026, 7));

    @Test
    void normalize는_공백_괄호_숫자를_제거하고_소문자로_바꾼다() {
        assertThat(RecurringDetector.normalize("넷플릭스")).isEqualTo("넷플릭스");
        assertThat(RecurringDetector.normalize(" 넷플릭스 09 ")).isEqualTo("넷플릭스");
        assertThat(RecurringDetector.normalize("Netflix(9월)")).isEqualTo("netflix월");
        assertThat(RecurringDetector.normalize(null)).isEmpty();
    }

    @Test
    void 금액이_비슷하게_3개월_연속이면_고정지출로_감지된다() {
        List<Transaction> transactions = List.of(
                transaction("넷플릭스", "17000", LocalDate.of(2026, 7, 5)),
                transaction("넷플릭스", "17000", LocalDate.of(2026, 8, 5)),
                transaction("넷플릭스", "17000", LocalDate.of(2026, 9, 5)));

        List<RecurringDetector.Candidate> result = RecurringDetector.detect(transactions, threeMonths);

        assertThat(result).hasSize(1);
        RecurringDetector.Candidate candidate = result.get(0);
        assertThat(candidate.merchant()).isEqualTo("넷플릭스");
        assertThat(candidate.medianAmount()).isEqualByComparingTo("17000.00");
        assertThat(candidate.monthsSeen()).isEqualTo(3);
        assertThat(candidate.lastDate()).isEqualTo(LocalDate.of(2026, 9, 5));
    }

    @Test
    void 두_달만_있으면_고정지출로_감지되지_않는다() {
        List<Transaction> transactions = List.of(
                transaction("넷플릭스", "17000", LocalDate.of(2026, 8, 5)),
                transaction("넷플릭스", "17000", LocalDate.of(2026, 9, 5)));

        List<RecurringDetector.Candidate> result = RecurringDetector.detect(transactions, threeMonths);

        assertThat(result).isEmpty();
    }

    @Test
    void 금액이_10퍼센트를_벗어나면_감지되지_않는다() {
        List<Transaction> transactions = List.of(
                transaction("헬스장", "50000", LocalDate.of(2026, 7, 1)),
                transaction("헬스장", "50000", LocalDate.of(2026, 8, 1)),
                transaction("헬스장", "70000", LocalDate.of(2026, 9, 1))); // 중앙값 50000의 110% = 55000 초과

        List<RecurringDetector.Candidate> result = RecurringDetector.detect(transactions, threeMonths);

        assertThat(result).isEmpty();
    }

    @Test
    void merchant가_비어있는_거래는_후보에서_제외된다() {
        List<Transaction> transactions = new ArrayList<>(List.of(
                transaction(null, "17000", LocalDate.of(2026, 7, 5)),
                transaction("", "17000", LocalDate.of(2026, 8, 5)),
                transaction("   ", "17000", LocalDate.of(2026, 9, 5))));

        List<RecurringDetector.Candidate> result = RecurringDetector.detect(transactions, threeMonths);

        assertThat(result).isEmpty();
    }

    private Transaction transaction(String merchant, String amount, LocalDate txnDate) {
        return new Transaction(user, category, TransactionType.EXPENSE, new BigDecimal(amount),
                txnDate, merchant, null);
    }
}
