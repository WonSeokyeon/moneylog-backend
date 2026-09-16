package com.example.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.domain.Category;
import com.example.domain.CategoryRepository;
import com.example.domain.TransactionType;
import com.example.domain.User;
import com.example.exception.BusinessException;
import com.example.exception.ErrorCode;

@Service
public class CategoryService {

    private final CategoryRepository categoryRepository;

    public CategoryService(CategoryRepository categoryRepository) {
        this.categoryRepository = categoryRepository;
    }

    @Transactional(readOnly = true)
    public List<Category> list(User user, TransactionType type) {
        if (type != null) {
            return categoryRepository.findByUserAndTypeAndDeletedAtIsNullOrderBySortOrderAscIdAsc(user, type);
        }
        return categoryRepository.findByUserAndDeletedAtIsNullOrderBySortOrderAscIdAsc(user);
    }

    @Transactional
    public Category create(User user, String name, TransactionType type, String color, Integer sortOrder) {
        boolean duplicated = categoryRepository
                .findByUserAndNameAndTypeAndDeletedAtIsNull(user, name, type)
                .isPresent();
        if (duplicated) {
            throw new BusinessException(ErrorCode.CATEGORY_DUPLICATED);
        }

        Category category = new Category(user, name, type, color, sortOrder == null ? 0 : sortOrder);
        return categoryRepository.save(category);
    }

    @Transactional
    public Category update(User user, Long categoryId, String name, String color, Integer sortOrder) {
        Category category = findOwned(user, categoryId);

        category.rename(name);
        category.changeColor(color);
        if (sortOrder != null) {
            category.changeSortOrder(sortOrder);
        }
        return category;
    }

    @Transactional
    public void softDelete(User user, Long categoryId) {
        Category category = findOwned(user, categoryId);
        category.softDelete();
    }

    private Category findOwned(User user, Long categoryId) {
        return categoryRepository.findByIdAndUser(categoryId, user)
                .orElseThrow(() -> new BusinessException(ErrorCode.CATEGORY_NOT_FOUND));
    }
}
