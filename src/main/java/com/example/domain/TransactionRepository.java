package com.example.domain;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TransactionRepository extends JpaRepository<Transaction, Long> {

    // 조회/수정/삭제 전 소유권 검증용. 이미 삭제된 거래는 다시 꺼내 쓸 수 없어야 하므로 deletedAt IS NULL을 건다.
    Optional<Transaction> findByIdAndUserAndDeletedAtIsNull(Long id, User user);

    // 월 집계(summary/byCategory/daily) 전용. GROUP BY로 DB에서 합산하지 않고 한 번에 불러와 자바에서 집계한다 —
    // 한 달치 거래는 최대 수백~천 건 수준이라 성능 문제가 없고, CASE 안에서 enum 리터럴을 비교하는 JPQL이
    // 이 프로젝트의 Hibernate 7.4.5 + PostgreSQL 조합에서 반복적으로 파라미터 타입 추론 문제를 일으켰던 것도 피한다.
    // join fetch로 category를 함께 가져와 삭제된 카테고리를 쓰던 거래도 그대로 집계에 포함시킨다(CLAUDE.md 4장).
    @Query("SELECT t FROM Transaction t JOIN FETCH t.category "
            + "WHERE t.user = :user AND t.deletedAt IS NULL AND t.txnDate BETWEEN :from AND :to "
            + "ORDER BY t.txnDate ASC")
    List<Transaction> findForMonth(@Param("user") User user,
                                    @Param("from") LocalDate from,
                                    @Param("to") LocalDate to);

    // 카테고리 조인에는 deleted_at IS NULL을 걸지 않는다 — 삭제된 카테고리를 쓰던 과거 거래도 그대로 보여야 한다(CLAUDE.md 4장).
    // join fetch는 *ToOne(category) 관계라 페이지네이션과 함께 써도 안전하다(*ToMany 컬렉션 fetch join과 다름).
    //
    // from/to는 "IS NULL이면 무시" 분기를 JPQL에 두지 않는다. 같은 이름 파라미터가 텍스트에 두 번(IS NULL 검사 +
    // 비교) 나타나면 각각 별도의 SQL 위치 파라미터로 컴파일되는데, Hibernate 7.4.5 + PostgreSQL 조합에서
    // "IS NULL"로만 쓰인 자리는 값이 실제 null이 아니어도 타입을 추론하지 못해
    // "could not determine data type of parameter"로 500이 난다(서비스에서 null을 최소/최대 날짜로 치환해 호출한다).
    @Query(
            value = "SELECT t FROM Transaction t JOIN FETCH t.category c "
                    + "WHERE t.user.id = :userId AND t.deletedAt IS NULL "
                    + "AND t.txnDate >= :from AND t.txnDate <= :to "
                    + "AND (:type IS NULL OR t.type = :type) "
                    + "AND (:categoryId IS NULL OR c.id = :categoryId) "
                    + "AND (:keyword IS NULL "
                    + "     OR LOWER(t.merchant) LIKE LOWER(CONCAT('%', CAST(:keyword AS string), '%')) "
                    + "     OR LOWER(t.memo) LIKE LOWER(CONCAT('%', CAST(:keyword AS string), '%')))",
            countQuery = "SELECT COUNT(t) FROM Transaction t "
                    + "WHERE t.user.id = :userId AND t.deletedAt IS NULL "
                    + "AND t.txnDate >= :from AND t.txnDate <= :to "
                    + "AND (:type IS NULL OR t.type = :type) "
                    + "AND (:categoryId IS NULL OR t.category.id = :categoryId) "
                    + "AND (:keyword IS NULL "
                    + "     OR LOWER(t.merchant) LIKE LOWER(CONCAT('%', CAST(:keyword AS string), '%')) "
                    + "     OR LOWER(t.memo) LIKE LOWER(CONCAT('%', CAST(:keyword AS string), '%')))")
    Page<Transaction> search(@Param("userId") Long userId,
                              @Param("from") LocalDate from,
                              @Param("to") LocalDate to,
                              @Param("type") TransactionType type,
                              @Param("categoryId") Long categoryId,
                              @Param("keyword") String keyword,
                              Pageable pageable);
}
