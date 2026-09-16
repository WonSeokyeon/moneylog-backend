-- Hibernate의 ddl-auto가 만들 수 없는 제약만 담는다.
-- 4개 테이블이 생성된 뒤(ddl-auto: update 1회 기동 후)에 수동으로 적용한다.
-- 적용 방법은 moneylog-backend/CLAUDE.md 참조.

-- categories: 같은 이름의 카테고리를 삭제 후 재생성할 수 있어야 하므로 부분 유니크 인덱스로 건다.
CREATE UNIQUE INDEX IF NOT EXISTS uq_categories_user_name_type
    ON categories (user_id, name, type)
    WHERE deleted_at IS NULL;

-- transactions/budgets: 금액의 부호는 type으로만 표현한다. 음수·0은 허용하지 않는다.
ALTER TABLE transactions
    ADD CONSTRAINT chk_transactions_amount_positive CHECK (amount > 0);

ALTER TABLE budgets
    ADD CONSTRAINT chk_budgets_amount_positive CHECK (amount > 0);
