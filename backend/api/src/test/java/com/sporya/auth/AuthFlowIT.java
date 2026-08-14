package com.sporya.auth;

import static org.assertj.core.api.Assertions.assertThat;

import com.sporya.auth.controller.dto.AuthResponse;
import com.sporya.auth.controller.dto.LoginRequest;
import com.sporya.auth.controller.dto.RegisterRequest;
import com.sporya.auth.controller.dto.UserResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/** Preuve de bout en bout : inscription -> connexion -> profil protégé par JWT. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class AuthFlowIT {

  @Container @ServiceConnection
  static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

  @Autowired private TestRestTemplate restTemplate;

  @Test
  void registerThenLoginThenFetchProtectedProfile() {
    String email = "player+" + System.nanoTime() + "@sporya.test";
    String password = "correct-horse-battery";

    ResponseEntity<UserResponse> registerResponse =
        restTemplate.postForEntity(
            "/api/v1/auth/register", new RegisterRequest(email, password), UserResponse.class);
    assertThat(registerResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    assertThat(registerResponse.getBody()).isNotNull();
    assertThat(registerResponse.getBody().email()).isEqualTo(email);

    ResponseEntity<Void> duplicateResponse =
        restTemplate.postForEntity(
            "/api/v1/auth/register", new RegisterRequest(email, password), Void.class);
    assertThat(duplicateResponse.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);

    ResponseEntity<AuthResponse> wrongPasswordResponse =
        restTemplate.postForEntity(
            "/api/v1/auth/login", new LoginRequest(email, "not-the-password"), AuthResponse.class);
    assertThat(wrongPasswordResponse.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);

    ResponseEntity<AuthResponse> loginResponse =
        restTemplate.postForEntity(
            "/api/v1/auth/login", new LoginRequest(email, password), AuthResponse.class);
    assertThat(loginResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(loginResponse.getBody()).isNotNull();
    String accessToken = loginResponse.getBody().accessToken();
    assertThat(accessToken).isNotBlank();

    ResponseEntity<UserResponse> unauthenticatedMeResponse =
        restTemplate.getForEntity("/api/v1/auth/me", UserResponse.class);
    assertThat(unauthenticatedMeResponse.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);

    HttpHeaders headers = new HttpHeaders();
    headers.setBearerAuth(accessToken);
    ResponseEntity<UserResponse> meResponse =
        restTemplate.exchange(
            "/api/v1/auth/me", HttpMethod.GET, new HttpEntity<>(headers), UserResponse.class);
    assertThat(meResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(meResponse.getBody()).isNotNull();
    assertThat(meResponse.getBody().email()).isEqualTo(email);
  }
}
