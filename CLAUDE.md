# moneylog-backend 개발 가이드

> 이 저장소를 단독으로 클론하면 부모 문서(`moneylog-project/CLAUDE.md`)가 없다.
> **전체 스펙(데이터 모델·API 명세·인증 설계·에러 처리 등)은 그 문서가 정본이다.**
> 여기에는 **이 저장소에서만** 참조하는 빌드 명령과 계층 규칙만 적는다.

## 실행 명령어

개발 환경은 Windows다. 아래는 Git Bash 기준이며, PowerShell/cmd는 `mvnw.cmd`를 쓴다.

```bash
./mvnw spring-boot:run          # http://localhost:8080
./mvnw test
./mvnw compile
```

- **`./mvnw`는 POSIX 셸 스크립트라 PowerShell에서 직접 실행되지 않는다.** Git Bash를 쓰거나 `mvnw.cmd`를 쓴다.
- Swagger UI: http://localhost:8080/swagger-ui/index.html
- 로컬 DB 생성(최초 1회): `createdb moneylog_db`, `createdb moneylog_test`
  - **생성 후 DB 기본 타임존을 UTC로 맞춘다**(최초 1회): `ALTER DATABASE moneylog_db SET timezone TO 'UTC';` / `ALTER DATABASE moneylog_test SET timezone TO 'UTC';`. `created_at` 등은 `TIMESTAMP`(타임존 없음) 컬럼이라 저장값 자체는 이 설정과 무관하지만(JVM 기본 타임존이 실제 원인), `psql`로 확인할 때 `SHOW timezone`이 `Asia/Seoul`처럼 나오면 헷갈리기 쉬워 맞춰둔다.
- 실행 전 `.env.example`을 참고해 `.env` 또는 환경변수를 채운다. `JWT_SECRET`은 raw UTF-8 32자 이상이어야 한다(Base64 디코드하지 않음).
- **JVM 기본 타임존을 UTC로 고정한다** (`MoneylogBackendApplication`의 static 블록에서 `TimeZone.setDefault(...)`). `hibernate.jdbc.time_zone: UTC`는 JDBC 값 변환에만 적용되고, `@CreatedDate`/`@LastModifiedDate`가 쓰는 `LocalDateTime.now()`는 JVM 기본 타임존을 그대로 따른다 — 이 설정이 없으면 서버가 KST 환경에서 돌 때 감사 필드가 KST로 저장된다.

## 패키지 계층 규칙

```
com.example
├── domain/       # 엔티티, Repository
├── service/      # 비즈니스 로직, 집계·예측, CSV
├── controller/    # REST API
├── dto/          # 요청/응답 DTO (record)
├── config/       # Security, JWT, Swagger, CORS 설정
└── exception/    # BusinessException, ErrorCode, GlobalExceptionHandler
```

- **컨트롤러는 엔티티를 직접 반환하지 않는다.** 항상 `dto/`의 DTO로 변환한다.
- 엔티티에 `@Setter`를 두지 않는다. 변경은 의미 있는 메서드로 한다(`updateAmount(...)`, `softDelete()`).
- Service에 `@Transactional`을 붙이고, 조회 전용 메서드는 `readOnly = true`로 명시한다.
- 집계 쿼리는 Repository 메서드 이름 규칙이 아니라 `@Query`로 명시적으로 작성한다.

## 이 저장소에서 만들지 않는 것

- `application.properties` — `.yml`과 공존하면 그쪽 설정이 조용히 무시된다. `.yml` 3종(공통/local/test)만 쓴다.
- Flyway 등 마이그레이션 도구 — `ddl-auto: update` + `db/schema-extra.sql` 수동 적용이 이 프로젝트의 방침이다.
- OAuth2 관련 의존성 — 소셜 로그인은 이번 범위 밖이다(`PRD.md` 1장, `AUTH-09`는 P2).

## db/schema-extra.sql 적용 방법

Hibernate `ddl-auto`가 만들지 못하는 부분 유니크 인덱스(`categories`)와 CHECK 제약(`transactions`·`budgets`의 `amount > 0`)이 들어 있다. **4개 테이블이 먼저 생성된 뒤에만** 적용된다(테이블이 없으면 실패).

```bash
# 1) 앱을 한 번 기동해 ddl-auto:update로 4개 테이블을 만든다 (Ctrl+C로 종료해도 무방)
./mvnw spring-boot:run

# 2) moneylog_db에 적용
"/c/Program Files/PostgreSQL/17/bin/psql.exe" -U postgres -h localhost -d moneylog_db \
  -f src/main/resources/db/schema-extra.sql
```

- `psql` 경로는 로컬 PostgreSQL 설치 버전에 맞게 바꾼다.
- 멱등적으로 작성돼 있다(`IF NOT EXISTS`, 이미 있는 제약을 다시 추가하면 에러가 나므로 재적용 전 `\d categories`/`\d transactions`로 존재 여부를 먼저 확인한다).

## 테스트

- 통합 테스트 DB는 **로컬 PostgreSQL `moneylog_test`**를 쓴다. H2·Testcontainers는 쓰지 않는다(`CLAUDE.md` 12장 — 집계 쿼리가 PostgreSQL 전용 동작에 의존한다).
- `@DataJpaTest`는 `@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)`을 반드시 함께 붙인다. 기본값은 임베디드 DB로 교체를 시도한다.
- **`moneylog_test`는 `ddl-auto: create-drop`이라 테스트가 끝나면 스키마가 통째로 사라진다.** `schema-extra.sql`의 부분 유니크 인덱스·CHECK 제약도 함께 사라지므로, 그 제약에 의존하는 테스트(카테고리 중복 생성 실패, `amount<=0` 거부 등)를 돌리기 전에는 **매번** `moneylog_test`에 재적용해야 한다. 자동화하지 않는다(Flyway 등 도입 없이 수동 적용이 이 프로젝트의 방침).
