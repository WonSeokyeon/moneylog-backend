package com.example.domain;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

import javax.sql.DataSource;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.core.io.ClassPathResource;
import org.springframework.test.context.ActiveProfiles;

/**
 * moneylog_test는 ddl-auto: create-drop이라 컨텍스트가 새로 뜰 때마다 schema-extra.sql의
 * 부분 유니크 인덱스·CHECK 제약이 함께 사라진다. 부분 유니크/CHECK에 의존하는 테스트가
 * 실제 제약을 검증할 수 있도록, Hibernate가 테이블을 만든 뒤 schema-extra.sql을 다시 적용한다.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
abstract class AbstractRepositoryTest {

    @Autowired
    private DataSource dataSource;

    @BeforeAll
    void applySchemaExtra() throws Exception {
        String sql;
        try (InputStream in = new ClassPathResource("db/schema-extra.sql").getInputStream()) {
            sql = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }

        // 줄 단위 주석(--)이 실제 SQL문과 같은 세미콜론 구간에 섞여 있으면
        // "청크가 --로 시작하는가"만 보는 판별로는 주석 뒤에 붙은 문장까지 통째로 건너뛴다.
        // 분리 전에 주석 줄 자체를 전부 제거한다.
        String withoutComments = sql.replaceAll("(?m)^\\s*--.*$", "");

        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {
            for (String rawStatement : withoutComments.split(";")) {
                String trimmed = rawStatement.strip();
                if (trimmed.isEmpty()) {
                    continue;
                }
                try {
                    statement.execute(trimmed);
                } catch (SQLException alreadyApplied) {
                    // 컨텍스트 캐시로 여러 테스트 클래스가 스키마를 공유할 때 중복 적용될 수 있다. 무시한다.
                }
            }
        }
    }
}
