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
- 실행 전 `.env.example`을 참고해 `.env` 또는 환경변수를 채운다. `JWT_SECRET`은 raw UTF-8 32자 이상이어야 한다(Base64 디코드하지 않음).

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

## 테스트

- 통합 테스트 DB는 **로컬 PostgreSQL `moneylog_test`**를 쓴다. H2·Testcontainers는 쓰지 않는다(`CLAUDE.md` 12장 — 집계 쿼리가 PostgreSQL 전용 동작에 의존한다).
- `@DataJpaTest`는 `@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)`을 반드시 함께 붙인다. 기본값은 임베디드 DB로 교체를 시도한다.
