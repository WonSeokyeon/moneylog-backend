package com.example.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.dao.DataIntegrityViolationException;

class BudgetRepositoryTest extends AbstractRepositoryTest {

    @Autowired
    private BudgetRepository budgetRepository;

    @Autowired
    private TestEntityManager entityManager;

    private Category persistCategoryWithUser(String email) {
        User user = entityManager.persistAndFlush(new User(email, "hashed-password", "테스터"));
        return entityManager.persistAndFlush(
                new Category(user, "식비", TransactionType.EXPENSE, "#EF4444", 0));
    }

    @Test
    void 저장하면_createdAt이_채워지고_deletedAt_필드는_없다() {
        Category category = persistCategoryWithUser("budget-basic@example.com");

        Budget budget = new Budget(category.getUser(), category, "2026-09", new BigDecimal("600000"));

        Budget saved = budgetRepository.saveAndFlush(budget);

        assertThat(saved.getCreatedAt()).isNotNull();
    }

    @Test
    void 예산_금액이_0이면_DB_제약에_의해_거부된다() {
        Category category = persistCategoryWithUser("budget-zero@example.com");

        Budget zeroBudget = new Budget(category.getUser(), category, "2026-09", BigDecimal.ZERO);

        assertThatThrownBy(() -> budgetRepository.saveAndFlush(zeroBudget))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
