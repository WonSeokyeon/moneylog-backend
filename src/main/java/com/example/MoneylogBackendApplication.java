package com.example;

import java.util.TimeZone;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

@SpringBootApplication
@EnableJpaAuditing
public class MoneylogBackendApplication {

	static {
		// hibernate.jdbc.time_zone: UTC는 JDBC 값 변환에만 적용된다.
		// @CreatedDate/@LastModifiedDate가 호출하는 LocalDateTime.now()는 JVM 기본 타임존을 그대로 쓰므로,
		// 서버가 KST 환경에서 돌면 감사 필드가 KST로 저장된다. JVM 기본 타임존 자체를 UTC로 고정한다.
		// static 블록에 둔 이유: @DataJpaTest는 main()을 호출하지 않고 이 클래스를 설정 소스로만 쓰지만,
		// 클래스 로딩은 그 경우에도 일어나므로 main() 실행 여부와 무관하게 항상 적용된다.
		TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
	}

	public static void main(String[] args) {
		SpringApplication.run(MoneylogBackendApplication.class, args);
	}

}
