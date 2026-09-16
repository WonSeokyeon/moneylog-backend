package com.example.domain;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface CategoryRepository extends JpaRepository<Category, Long> {

    List<Category> findByUserAndDeletedAtIsNullOrderBySortOrderAscIdAsc(User user);

    List<Category> findByUserAndTypeAndDeletedAtIsNullOrderBySortOrderAscIdAsc(User user, TransactionType type);

    Optional<Category> findByUserAndNameAndTypeAndDeletedAtIsNull(User user, String name, TransactionType type);

    Optional<Category> findByIdAndUser(Long id, User user);
}
