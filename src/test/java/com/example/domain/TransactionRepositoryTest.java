package com.example.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.LocalDate;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.dao.DataIntegrityViolationException;

class TransactionRepositoryTest extends AbstractRepositoryTest {

    @Autowired
    private TransactionRepository transactionRepository;

    @Autowired
    private TestEntityManager entityManager;

    private Category persistCategoryWithUser(String email) {
        User user = entityManager.persistAndFlush(new User(email, "hashed-password", "테스터"));
        return entityManager.persistAndFlush(
                new Category(user, "식비", TransactionType.EXPENSE, "#EF4444", 0));
    }

    @Test
    void 금액은_BigDecimal_왕복에서_compareTo로_비교해야_일치한다() {
        Category category = persistCategoryWithUser("txn-amount@example.com");

        Transaction transaction = new Transaction(
                category.getUser(), category, TransactionType.EXPENSE,
                new BigDecimal("12500"), LocalDate.of(2026, 9, 14), "스타벅스", null, null, null);

        Long id = transactionRepository.saveAndFlush(transaction).getId();
        entityManager.clear();

        Transaction reloaded = transactionRepository.findById(id).orElseThrow();

        assertThat(reloaded.getAmount()).isEqualByComparingTo(new BigDecimal("12500"));
    }

    @Test
    void 금액이_0이면_DB_제약에_의해_거부된다() {
        Category category = persistCategoryWithUser("txn-zero@example.com");

        Transaction zeroAmount = new Transaction(
                category.getUser(), category, TransactionType.EXPENSE,
                BigDecimal.ZERO, LocalDate.of(2026, 9, 14), null, null, null, null);

        assertThatThrownBy(() -> transactionRepository.saveAndFlush(zeroAmount))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
