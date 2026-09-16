package com.example.service;

import java.util.List;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.config.JwtTokenProvider;
import com.example.domain.Category;
import com.example.domain.CategoryRepository;
import com.example.domain.TransactionType;
import com.example.domain.User;
import com.example.domain.UserRepository;
import com.example.exception.BusinessException;
import com.example.exception.ErrorCode;

@Service
public class AuthService {

    private record DefaultCategory(TransactionType type, String name, String color) {
    }

    // CLAUDE.md 6장 표. 사용자는 이후 자유롭게 수정·삭제·추가할 수 있고 별도 플래그는 두지 않는다.
    private static final List<DefaultCategory> DEFAULT_CATEGORIES = List.of(
            new DefaultCategory(TransactionType.EXPENSE, "식비", "#EF4444"),
            new DefaultCategory(TransactionType.EXPENSE, "교통", "#F59E0B"),
            new DefaultCategory(TransactionType.EXPENSE, "주거/통신", "#6366F1"),
            new DefaultCategory(TransactionType.EXPENSE, "생활용품", "#10B981"),
            new DefaultCategory(TransactionType.EXPENSE, "문화/여가", "#EC4899"),
            new DefaultCategory(TransactionType.EXPENSE, "의료/건강", "#14B8A6"),
            new DefaultCategory(TransactionType.EXPENSE, "기타", "#737373"),
            new DefaultCategory(TransactionType.INCOME, "급여", "#4F46E5"),
            new DefaultCategory(TransactionType.INCOME, "기타수입", "#737373")
    );

    private final UserRepository userRepository;
    private final CategoryRepository categoryRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;

    public AuthService(UserRepository userRepository, CategoryRepository categoryRepository,
                        PasswordEncoder passwordEncoder, JwtTokenProvider jwtTokenProvider) {
        this.userRepository = userRepository;
        this.categoryRepository = categoryRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtTokenProvider = jwtTokenProvider;
    }

    @Transactional
    public void signup(String email, String rawPassword, String nickname) {
        if (userRepository.findByEmail(email).isPresent()) {
            throw new BusinessException(ErrorCode.EMAIL_DUPLICATED);
        }

        User user = userRepository.save(new User(email, passwordEncoder.encode(rawPassword), nickname));

        int expenseOrder = 0;
        int incomeOrder = 0;
        for (DefaultCategory defaultCategory : DEFAULT_CATEGORIES) {
            int sortOrder = defaultCategory.type() == TransactionType.EXPENSE ? expenseOrder++ : incomeOrder++;
            categoryRepository.save(new Category(
                    user, defaultCategory.name(), defaultCategory.type(), defaultCategory.color(), sortOrder));
        }
    }

    @Transactional(readOnly = true)
    public String login(String email, String rawPassword) {
        // 이메일이 없는 경우와 비밀번호가 틀린 경우의 메시지를 절대 구분하지 않는다(존재 여부 노출 방지).
        User user = userRepository.findByEmail(email)
                .filter(u -> u.getDeletedAt() == null)
                .orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHORIZED, "이메일 또는 비밀번호가 올바르지 않습니다."));

        if (!passwordEncoder.matches(rawPassword, user.getPassword())) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "이메일 또는 비밀번호가 올바르지 않습니다.");
        }

        return jwtTokenProvider.createToken(user.getId(), user.getEmail());
    }
}
