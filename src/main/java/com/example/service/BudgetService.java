package com.example.service;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.domain.Budget;
import com.example.domain.BudgetRepository;
import com.example.domain.Category;
import com.example.domain.CategoryRepository;
import com.example.domain.TransactionType;
import com.example.domain.User;
import com.example.dto.BudgetItemRequest;
import com.example.dto.BudgetResponse;
import com.example.dto.BudgetUpsertRequest;
import com.example.exception.BusinessException;
import com.example.exception.ErrorCode;

@Service
public class BudgetService {

    private final BudgetRepository budgetRepository;
    private final CategoryRepository categoryRepository;

    public BudgetService(BudgetRepository budgetRepository, CategoryRepository categoryRepository) {
        this.budgetRepository = budgetRepository;
        this.categoryRepository = categoryRepository;
    }

    // 지출 카테고리 전체를 반환하고, 그 달에 설정된 예산만 amount를 채운다. 화면이 표로 바로 그릴 수 있게 하기 위함이다.
    @Transactional(readOnly = true)
    public List<BudgetResponse> list(User user, String yearMonth) {
        Map<Long, BigDecimal> amountByCategoryId = budgetRepository.findByUserAndYearMonth(user, yearMonth).stream()
                .collect(Collectors.toMap(b -> b.getCategory().getId(), Budget::getAmount));

        return categoryRepository
                .findByUserAndTypeAndDeletedAtIsNullOrderBySortOrderAscIdAsc(user, TransactionType.EXPENSE)
                .stream()
                .map(category -> BudgetResponse.from(category, amountByCategoryId.get(category.getId())))
                .toList();
    }

    // amount가 0 이하거나 null이면 행을 물리 삭제한다(budgets는 deleted_at이 없는 유일한 물리 삭제 테이블).
    @Transactional
    public List<BudgetResponse> upsert(User user, BudgetUpsertRequest request) {
        for (BudgetItemRequest item : request.items()) {
            Category category = categoryRepository.findByIdAndUser(item.categoryId(), user)
                    .orElseThrow(() -> new BusinessException(ErrorCode.CATEGORY_NOT_FOUND));
            if (category.getType() != TransactionType.EXPENSE) {
                throw new BusinessException(ErrorCode.INVALID_INPUT, "예산은 지출 카테고리에만 설정할 수 있습니다.");
            }

            var existing = budgetRepository.findByUserAndCategoryAndYearMonth(user, category, request.yearMonth());
            boolean remove = item.amount() == null || item.amount().compareTo(BigDecimal.ZERO) <= 0;

            if (remove) {
                existing.ifPresent(budgetRepository::delete);
            } else if (existing.isPresent()) {
                existing.get().changeAmount(item.amount());
            } else {
                budgetRepository.save(new Budget(user, category, request.yearMonth(), item.amount()));
            }
        }

        return list(user, request.yearMonth());
    }
}
