package com.example.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Set;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.domain.Category;
import com.example.domain.CategoryRepository;
import com.example.domain.Transaction;
import com.example.domain.TransactionRepository;
import com.example.domain.TransactionType;
import com.example.domain.User;
import com.example.exception.BusinessException;
import com.example.exception.ErrorCode;

@Service
public class TransactionService {

    private static final Set<String> ALLOWED_SORT_FIELDS = Set.of("txnDate", "amount", "createdAt");
    private static final LocalDate MIN_DATE = LocalDate.of(1, 1, 1);
    private static final LocalDate MAX_DATE = LocalDate.of(9999, 12, 31);

    private final TransactionRepository transactionRepository;
    private final CategoryRepository categoryRepository;

    public TransactionService(TransactionRepository transactionRepository, CategoryRepository categoryRepository) {
        this.transactionRepository = transactionRepository;
        this.categoryRepository = categoryRepository;
    }

    @Transactional(readOnly = true)
    public Page<Transaction> list(User user, LocalDate from, LocalDate to, TransactionType type,
                                   Long categoryId, String keyword,
                                   int page, int size, String sortField, String sortDirection) {
        PageRequest pageRequest = PageRequest.of(page, size, resolveSort(sortField, sortDirection));
        LocalDate effectiveFrom = from != null ? from : MIN_DATE;
        LocalDate effectiveTo = to != null ? to : MAX_DATE;
        return transactionRepository.search(
                user.getId(), effectiveFrom, effectiveTo, type, categoryId, keyword, pageRequest);
    }

    @Transactional(readOnly = true)
    public Transaction get(User user, Long transactionId) {
        return findOwned(user, transactionId);
    }

    @Transactional
    public Transaction create(User user, TransactionType type, BigDecimal amount, LocalDate txnDate,
                               Long categoryId, String merchant, String memo, Double latitude, Double longitude) {
        Category category = findOwnedCategory(user, categoryId);
        validateTypeMatches(type, category);

        Transaction transaction = new Transaction(user, category, type, amount, txnDate, merchant, memo, latitude, longitude);
        return transactionRepository.save(transaction);
    }

    @Transactional
    public Transaction update(User user, Long transactionId, TransactionType type, BigDecimal amount,
                               LocalDate txnDate, Long categoryId, String merchant, String memo,
                               Double latitude, Double longitude) {
        Transaction transaction = findOwned(user, transactionId);
        Category category = findOwnedCategory(user, categoryId);
        validateTypeMatches(type, category);

        transaction.update(category, type, amount, txnDate, merchant, memo, latitude, longitude);
        return transaction;
    }

    @Transactional
    public void softDelete(User user, Long transactionId) {
        findOwned(user, transactionId).softDelete();
    }

    private Transaction findOwned(User user, Long transactionId) {
        return transactionRepository.findByIdAndUserAndDeletedAtIsNull(transactionId, user)
                .orElseThrow(() -> new BusinessException(ErrorCode.TRANSACTION_NOT_FOUND));
    }

    private Category findOwnedCategory(User user, Long categoryId) {
        return categoryRepository.findByIdAndUser(categoryId, user)
                .orElseThrow(() -> new BusinessException(ErrorCode.CATEGORY_NOT_FOUND));
    }

    private void validateTypeMatches(TransactionType type, Category category) {
        if (category.getType() != type) {
            throw new BusinessException(ErrorCode.CATEGORY_TYPE_MISMATCH);
        }
    }

    // 허용 필드 화이트리스트 밖의 값은 500이 아니라 기본값(txnDate,desc)으로 대체한다(CLAUDE.md 5장 경고).
    // id desc는 항상 2차 정렬 키로 고정해, 같은 txnDate가 여러 건일 때 페이지 경계에서 항목이 중복·누락되지 않게 한다.
    private Sort resolveSort(String field, String direction) {
        String safeField = ALLOWED_SORT_FIELDS.contains(field) ? field : "txnDate";
        Sort.Direction safeDirection = "asc".equalsIgnoreCase(direction) ? Sort.Direction.ASC : Sort.Direction.DESC;
        return Sort.by(new Sort.Order(safeDirection, safeField)).and(Sort.by(Sort.Order.desc("id")));
    }
}
