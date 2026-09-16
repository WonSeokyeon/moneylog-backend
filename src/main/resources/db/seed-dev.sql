-- moneylog_db 개발용 시드: 테스트 계정 1개 + 기본 카테고리 9개 + 최근 6개월 거래 약 400건.
-- 적용 방법은 moneylog-backend/CLAUDE.md 참조. 재실행 가능하도록 기존 시드 계정 데이터를 먼저 지운다.
--
-- 날짜는 CURRENT_DATE 기준 상대 계산이다. 고정 캘린더 날짜(예: 2026-09)를 박아두면
-- 적용 시점이 지날수록 "최근 6개월"이라는 전제가 깨져 Phase5 예측 기준선(직전 3개월) 검증이 무의미해진다.
--
-- 로그인 테스트 계정: seed@moneylog.local / moneylog1234 (로컬 개발 전용, 아래 해시는 이 평문의 BCrypt 값이다)

DELETE FROM transactions WHERE user_id IN (SELECT id FROM users WHERE email = 'seed@moneylog.local');
DELETE FROM budgets WHERE user_id IN (SELECT id FROM users WHERE email = 'seed@moneylog.local');
DELETE FROM categories WHERE user_id IN (SELECT id FROM users WHERE email = 'seed@moneylog.local');
DELETE FROM users WHERE email = 'seed@moneylog.local';

INSERT INTO users (email, password, nickname, created_at, updated_at)
VALUES (
    'seed@moneylog.local',
    '$2a$10$M.mTqKtupNhCJPnJ5RYsluty99DC8eYXIaAOZpeL7xYeibjmIgsm6',
    '시드계정',
    now(), now()
);

-- 기본 카테고리 9개 (CLAUDE.md 6장 표와 동일)
INSERT INTO categories (user_id, name, type, color, sort_order, created_at, updated_at)
SELECT u.id, c.name, c.type, c.color, c.sort_order, now(), now()
FROM (SELECT id FROM users WHERE email = 'seed@moneylog.local') u
CROSS JOIN (VALUES
    ('식비',      'EXPENSE', '#EF4444', 0),
    ('교통',      'EXPENSE', '#F59E0B', 1),
    ('주거/통신', 'EXPENSE', '#6366F1', 2),
    ('생활용품',  'EXPENSE', '#10B981', 3),
    ('문화/여가', 'EXPENSE', '#EC4899', 4),
    ('의료/건강', 'EXPENSE', '#14B8A6', 5),
    ('기타',      'EXPENSE', '#737373', 6),
    ('급여',      'INCOME',  '#4F46E5', 0),
    ('기타수입',  'INCOME',  '#737373', 1)
) AS c(name, type, color, sort_order);

-- 고정지출 패턴 1: 넷플릭스 17,000원, 매월 5일, 6개월 연속 (Phase5 고정지출 감지 검증용)
INSERT INTO transactions (user_id, category_id, type, amount, txn_date, merchant, memo, created_at, updated_at)
SELECT
    u.id, cat.id, 'EXPENSE', 17000,
    (date_trunc('month', CURRENT_DATE) - (m || ' months')::interval)::date + 4,
    '넷플릭스', NULL, now(), now()
FROM generate_series(0, 5) AS m
CROSS JOIN (SELECT id FROM users WHERE email = 'seed@moneylog.local') u
CROSS JOIN (
    SELECT id FROM categories
    WHERE user_id = (SELECT id FROM users WHERE email = 'seed@moneylog.local') AND name = '문화/여가'
) cat;

-- 고정지출 패턴 2: 통신비 45,000원, 매월 12일, 6개월 연속
INSERT INTO transactions (user_id, category_id, type, amount, txn_date, merchant, memo, created_at, updated_at)
SELECT
    u.id, cat.id, 'EXPENSE', 45000,
    (date_trunc('month', CURRENT_DATE) - (m || ' months')::interval)::date + 11,
    '통신비', NULL, now(), now()
FROM generate_series(0, 5) AS m
CROSS JOIN (SELECT id FROM users WHERE email = 'seed@moneylog.local') u
CROSS JOIN (
    SELECT id FROM categories
    WHERE user_id = (SELECT id FROM users WHERE email = 'seed@moneylog.local') AND name = '주거/통신'
) cat;

-- 급여 3,000,000원, 매월 25일, 6개월 연속
INSERT INTO transactions (user_id, category_id, type, amount, txn_date, merchant, memo, created_at, updated_at)
SELECT
    u.id, cat.id, 'INCOME', 3000000,
    (date_trunc('month', CURRENT_DATE) - (m || ' months')::interval)::date + 24,
    '(주)머니로그', NULL, now(), now()
FROM generate_series(0, 5) AS m
CROSS JOIN (SELECT id FROM users WHERE email = 'seed@moneylog.local') u
CROSS JOIN (
    SELECT id FROM categories
    WHERE user_id = (SELECT id FROM users WHERE email = 'seed@moneylog.local') AND name = '급여'
) cat;

-- 나머지 지출 382건: 6개월에 걸쳐 EXPENSE 카테고리 7종을 순환하며 생성한다.
-- 18(고정 패턴) + 382 = 400.
INSERT INTO transactions (user_id, category_id, type, amount, txn_date, merchant, memo, created_at, updated_at)
SELECT
    u.id,
    cat.id,
    'EXPENSE',
    (1000 + ((n * 137) % 48000))::numeric(15,2),
    (date_trunc('month', CURRENT_DATE) - ((n % 6) || ' months')::interval)::date + (n % 27),
    (ARRAY['스타벅스','GS25','이마트','올리브영','CGV','지하철','약국','다이소','배달의민족','카카오T'])[(n % 10) + 1],
    CASE WHEN n % 5 = 0 THEN '메모 ' || n ELSE NULL END,
    now(), now()
FROM generate_series(1, 382) AS n
CROSS JOIN (SELECT id FROM users WHERE email = 'seed@moneylog.local') u
JOIN categories cat
    ON cat.user_id = u.id
    AND cat.type = 'EXPENSE'
    AND cat.name = (ARRAY['식비','교통','주거/통신','생활용품','문화/여가','의료/건강','기타'])[(n % 7) + 1];
