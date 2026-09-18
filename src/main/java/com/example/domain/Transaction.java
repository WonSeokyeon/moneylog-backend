package com.example.domain;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "transactions", indexes = {
        @Index(name = "idx_txn_user_date", columnList = "user_id, txn_date DESC, deleted_at"),
        @Index(name = "idx_txn_user_category", columnList = "user_id, category_id, deleted_at")
})
public class Transaction extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id", nullable = false)
    private Category category;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private TransactionType type;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal amount;

    @Column(name = "txn_date", nullable = false)
    private LocalDate txnDate;

    @Column(length = 100)
    private String merchant;

    @Column(length = 500)
    private String memo;

    private Double latitude;

    private Double longitude;

    private LocalDateTime deletedAt;

    protected Transaction() {
    }

    public Transaction(User user, Category category, TransactionType type, BigDecimal amount,
                        LocalDate txnDate, String merchant, String memo, Double latitude, Double longitude) {
        this.user = user;
        this.category = category;
        this.type = type;
        this.amount = amount;
        this.txnDate = txnDate;
        this.merchant = merchant;
        this.memo = memo;
        this.latitude = latitude;
        this.longitude = longitude;
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

    public TransactionType getType() {
        return type;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public LocalDate getTxnDate() {
        return txnDate;
    }

    public String getMerchant() {
        return merchant;
    }

    public String getMemo() {
        return memo;
    }

    public Double getLatitude() {
        return latitude;
    }

    public Double getLongitude() {
        return longitude;
    }

    public LocalDateTime getDeletedAt() {
        return deletedAt;
    }

    public boolean isDeleted() {
        return deletedAt != null;
    }

    public void update(Category category, TransactionType type, BigDecimal amount,
                        LocalDate txnDate, String merchant, String memo, Double latitude, Double longitude) {
        this.category = category;
        this.type = type;
        this.amount = amount;
        this.txnDate = txnDate;
        this.merchant = merchant;
        this.memo = memo;
        this.latitude = latitude;
        this.longitude = longitude;
    }

    public void softDelete() {
        this.deletedAt = LocalDateTime.now();
    }
}
