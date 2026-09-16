package com.example.domain;

import java.math.BigDecimal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "budgets")
public class Budget extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id", nullable = false)
    private Category category;

    @Column(name = "year_month", nullable = false, columnDefinition = "CHAR(7)")
    private String yearMonth;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal amount;

    protected Budget() {
    }

    public Budget(User user, Category category, String yearMonth, BigDecimal amount) {
        this.user = user;
        this.category = category;
        this.yearMonth = yearMonth;
        this.amount = amount;
    }

    public Long getId() {
        return id;
    }

    public User getUser() {
        return user;
    }

    public Category getCategory() {
        return category;
    }

    public String getYearMonth() {
        return yearMonth;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void changeAmount(BigDecimal amount) {
        this.amount = amount;
    }
}
