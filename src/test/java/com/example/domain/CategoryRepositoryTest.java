package com.example.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.dao.DataIntegrityViolationException;

class CategoryRepositoryTest extends AbstractRepositoryTest {

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private TestEntityManager entityManager;

    private User persistUser(String email) {
        User user = new User(email, "hashed-password", "테스터");
        return entityManager.persistAndFlush(user);
    }

    @Test
    void 삭제된_카테고리와_같은_이름을_재생성할_수_있다() {
        User user = persistUser("cat-recreate@example.com");

        Category deleted = new Category(user, "식비", TransactionType.EXPENSE, "#EF4444", 0);
        categoryRepository.saveAndFlush(deleted);
        deleted.softDelete();
        categoryRepository.saveAndFlush(deleted);

        Category recreated = new Category(user, "식비", TransactionType.EXPENSE, "#EF4444", 0);

        assertThat(categoryRepository.saveAndFlush(recreated).getId()).isNotNull();
    }

    @Test
    void 삭제하지_않은_같은_이름_카테고리는_중복_생성할_수_없다() {
        User user = persistUser("cat-duplicate@example.com");

        Category active = new Category(user, "교통", TransactionType.EXPENSE, "#F59E0B", 0);
        categoryRepository.saveAndFlush(active);

        Category duplicate = new Category(user, "교통", TransactionType.EXPENSE, "#F59E0B", 1);

        assertThatThrownBy(() -> categoryRepository.saveAndFlush(duplicate))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
