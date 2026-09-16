-- moneylog_db 성능 측정 전용 시드: 같은 계정(seed@moneylog.local)에 24개월치 20,000건을 추가한다.
-- Phase4 DoD의 "키워드 검색 포함 목록 조회가 워밍업 후 3회 측정 중앙값 500ms 이내(20,000건 기준)"를
-- 측정할 때만 적용한다. seed-dev.sql이 먼저 적용돼 있어야 한다(계정·카테고리를 그대로 재사용).
--
-- 재실행 가능하도록, 이 스크립트가 만든 행만 memo='PERF-SEED' 태그로 구분해 먼저 지운다.
-- (seed-dev.sql이 심은 400건은 이 태그가 없으므로 영향받지 않는다.)
--
-- 성능 측정이 끝나면 아래 DELETE 문만 다시 실행해 이 20,000건을 제거하고 seed-dev.sql 상태로 되돌린다:
--   DELETE FROM transactions
--   WHERE user_id = (SELECT id FROM users WHERE email = 'seed@moneylog.local') AND memo = 'PERF-SEED';

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM users WHERE email = 'seed@moneylog.local') THEN
        RAISE EXCEPTION 'seed@moneylog.local 계정이 없습니다. 먼저 db/seed-dev.sql을 적용하세요.';
    END IF;
END $$;

DELETE FROM transactions
WHERE user_id = (SELECT id FROM users WHERE email = 'seed@moneylog.local')
  AND memo = 'PERF-SEED';

INSERT INTO transactions (user_id, category_id, type, amount, txn_date, merchant, memo, created_at, updated_at)
SELECT
    u.id,
    cat.id,
    'EXPENSE',
    (1000 + ((n * 977) % 480000))::numeric(15,2),
    (date_trunc('month', CURRENT_DATE) - ((n % 24) || ' months')::interval)::date + (n % 27),
    'PERF' || (n % 50),
    'PERF-SEED',
    now(), now()
FROM generate_series(1, 20000) AS n
CROSS JOIN (SELECT id FROM users WHERE email = 'seed@moneylog.local') u
JOIN categories cat
    ON cat.user_id = u.id
    AND cat.type = 'EXPENSE'
    AND cat.name = (ARRAY['식비','교통','주거/통신','생활용품','문화/여가','의료/건강','기타'])[(n % 7) + 1];
