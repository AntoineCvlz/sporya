package com.sporya.auth;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Preuve que la chaîne Spring Boot -> Flyway -> PostgreSQL fonctionne de bout
 * en bout (squelette minimal, voir Phase 6 dans docs/architecture/overview.md).
 * La vraie logique métier (inscription, JWT) arrive dans un incrément suivant.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class AuthServiceApplicationTests {

  @Container @ServiceConnection
  static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

  @Test
  void contextLoads() {
    assertThat(postgres.isRunning()).isTrue();
  }
}
