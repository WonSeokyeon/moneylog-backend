package com.example.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import java.time.ZoneOffset;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;

class UserRepositoryTest extends AbstractRepositoryTest {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private TestEntityManager entityManager;

    @Test
    void 저장하면_createdAt이_채워진다() {
        User user = new User("user1@example.com", "hashed-password", "테스터");

        User saved = userRepository.save(user);
        entityManager.flush();

        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(saved.getUpdatedAt()).isNotNull();
    }

    @Test
    void createdAt은_UTC_기준으로_저장된다() {
        LocalDateTime beforeUtc = LocalDateTime.now(ZoneOffset.UTC);

        User saved = userRepository.save(new User("utc-check@example.com", "hashed-password", "테스터"));
        entityManager.flush();
        entityManager.clear();

        User reloaded = userRepository.findById(saved.getId()).orElseThrow();
        LocalDateTime afterUtc = LocalDateTime.now(ZoneOffset.UTC);

        // hibernate.jdbc.time_zone: UTC가 없으면 JVM 기본 타임존(KST, UTC+9)으로 저장되어
        // afterUtc와 9시간 가까이 벌어진다. 몇 초가 아니라 시간 단위 오차이므로 느슨한 허용치로도 오탐이 없다.
        assertThat(reloaded.getCreatedAt()).isBetween(beforeUtc.minusSeconds(5), afterUtc.plusSeconds(5));
    }
}
