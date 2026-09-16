package com.example.domain;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface BudgetRepository extends JpaRepository<Budget, Long> {

    // join fetch로 category를 함께 가져와 StatsService에서 category.getName()을 호출할 때 N+1이 나지 않게 한다.
    @Query("SELECT b FROM Budget b JOIN FETCH b.category WHERE b.user = :user AND b.yearMonth = :yearMonth")
    List<Budget> findByUserAndYearMonth(@Param("user") User user, @Param("yearMonth") String yearMonth);

    // upsert 대상 단건 조회. amount=0/null 시 이 행을 물리 삭제한다(budgets는 deleted_at이 없는 유일한 물리 삭제 테이블).
    Optional<Budget> findByUserAndCategoryAndYearMonth(User user, Category category, String yearMonth);
}
