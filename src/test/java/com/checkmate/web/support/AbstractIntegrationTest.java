package com.checkmate.web.support;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Spring Boot 3.1+ {@code @ServiceConnection} 기반 Testcontainers 통합 테스트 base.
 *
 * <p>{@code @DynamicPropertySource} 보일러플레이트 제거. 컨테이너는 JVM 단위로 1회 기동되며 각 테스트 클래스가 공유한다. Docker가 부재한
 * 환경에서는 통합 테스트가 스킵되도록 lazy-init만 사용 — import-time 부작용 없음.
 *
 * <p>참고: spring-template Phase E0 PR #22 패턴을 CheckMate에 시드.
 */
@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
@TestPropertySource(properties = {"spring.jpa.hibernate.ddl-auto=create-drop"})
public abstract class AbstractIntegrationTest {

  @Container @ServiceConnection
  static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");
}
