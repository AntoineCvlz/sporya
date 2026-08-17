package com.sporya.club;

import static org.assertj.core.api.Assertions.assertThat;

import com.sporya.auth.controller.dto.AuthResponse;
import com.sporya.auth.controller.dto.LoginRequest;
import com.sporya.auth.controller.dto.RegisterRequest;
import com.sporya.auth.domain.Role;
import com.sporya.auth.infrastructure.security.JwtService;
import com.sporya.club.controller.dto.AddMemberRequest;
import com.sporya.club.controller.dto.ClubResponse;
import com.sporya.club.controller.dto.CreateClubRequest;
import com.sporya.club.controller.dto.MemberResponse;
import io.jsonwebtoken.Claims;
import java.util.List;
import java.util.Map;
import java.util.UUID;
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

/** Preuve de bout en bout du RBAC par club : membership à la création, ajout par un ADMIN. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class ClubMembershipRbacIT {

  @Container @ServiceConnection
  static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

  @Autowired private TestRestTemplate restTemplate;
  @Autowired private JwtService jwtService;

  private String register(String email, String password) {
    restTemplate.postForEntity(
        "/api/v1/auth/register", new RegisterRequest(email, password), Void.class);
    return login(email, password);
  }

  private String login(String email, String password) {
    ResponseEntity<AuthResponse> response =
        restTemplate.postForEntity(
            "/api/v1/auth/login", new LoginRequest(email, password), AuthResponse.class);
    return response.getBody().accessToken();
  }

  private HttpHeaders authHeaders(String accessToken) {
    HttpHeaders headers = new HttpHeaders();
    headers.setBearerAuth(accessToken);
    return headers;
  }

  private UUID createClub(String accessToken) {
    ResponseEntity<ClubResponse> response =
        restTemplate.exchange(
            "/api/v1/clubs",
            HttpMethod.POST,
            new HttpEntity<>(
                new CreateClubRequest("FC Sporya", "France"), authHeaders(accessToken)),
            ClubResponse.class);
    return response.getBody().id();
  }

  @Test
  void clubCreatorAppearsAsAdminInMembersList() {
    String email = "admin+" + System.nanoTime() + "@sporya.test";
    String accessToken = register(email, "correct-horse-battery");
    UUID clubId = createClub(accessToken);

    ResponseEntity<MemberResponse[]> response =
        restTemplate.exchange(
            "/api/v1/clubs/" + clubId + "/members",
            HttpMethod.GET,
            new HttpEntity<>(authHeaders(accessToken)),
            MemberResponse[].class);
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody()).isNotNull();
    assertThat(List.of(response.getBody()))
        .anySatisfy(
            member -> {
              assertThat(member.email()).isEqualTo(email);
              assertThat(member.role()).isEqualTo(Role.ADMIN);
            });
  }

  @Test
  void adminAddsMemberWithCoachRole() {
    String adminEmail = "admin+" + System.nanoTime() + "@sporya.test";
    String adminToken = register(adminEmail, "correct-horse-battery");
    UUID clubId = createClub(adminToken);
    // Le token émis au login précédent ne porte pas encore le membership ADMIN accordé pendant
    // la création du club (JWT non rafraîchi en direct, voir le spec) : reconnexion nécessaire.
    adminToken = login(adminEmail, "correct-horse-battery");

    String coachEmail = "coach+" + System.nanoTime() + "@sporya.test";
    register(coachEmail, "correct-horse-battery");

    ResponseEntity<MemberResponse> addResponse =
        restTemplate.exchange(
            "/api/v1/clubs/" + clubId + "/members",
            HttpMethod.POST,
            new HttpEntity<>(new AddMemberRequest(coachEmail, Role.COACH), authHeaders(adminToken)),
            MemberResponse.class);
    assertThat(addResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    assertThat(addResponse.getBody()).isNotNull();
    assertThat(addResponse.getBody().email()).isEqualTo(coachEmail);
    assertThat(addResponse.getBody().role()).isEqualTo(Role.COACH);

    ResponseEntity<MemberResponse[]> listResponse =
        restTemplate.exchange(
            "/api/v1/clubs/" + clubId + "/members",
            HttpMethod.GET,
            new HttpEntity<>(authHeaders(adminToken)),
            MemberResponse[].class);
    assertThat(List.of(listResponse.getBody()))
        .anySatisfy(member -> assertThat(member.email()).isEqualTo(coachEmail));
  }

  @Test
  void nonAdminCannotAddMember() {
    String adminEmail = "admin+" + System.nanoTime() + "@sporya.test";
    String adminToken = register(adminEmail, "correct-horse-battery");
    UUID clubId = createClub(adminToken);

    String outsiderEmail = "outsider+" + System.nanoTime() + "@sporya.test";
    String outsiderToken = register(outsiderEmail, "correct-horse-battery");

    String targetEmail = "target+" + System.nanoTime() + "@sporya.test";
    register(targetEmail, "correct-horse-battery");

    ResponseEntity<Void> response =
        restTemplate.exchange(
            "/api/v1/clubs/" + clubId + "/members",
            HttpMethod.POST,
            new HttpEntity<>(
                new AddMemberRequest(targetEmail, Role.PLAYER), authHeaders(outsiderToken)),
            Void.class);
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
  }

  @Test
  void addingUnknownEmailReturns404() {
    String adminEmail = "admin+" + System.nanoTime() + "@sporya.test";
    String adminToken = register(adminEmail, "correct-horse-battery");
    UUID clubId = createClub(adminToken);
    adminToken = login(adminEmail, "correct-horse-battery");

    ResponseEntity<Void> response =
        restTemplate.exchange(
            "/api/v1/clubs/" + clubId + "/members",
            HttpMethod.POST,
            new HttpEntity<>(
                new AddMemberRequest("ghost@sporya.test", Role.PLAYER), authHeaders(adminToken)),
            Void.class);
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
  }

  @Test
  void newLoginTokenCarriesMembershipGrantedAfterFirstLogin() {
    String adminEmail = "admin+" + System.nanoTime() + "@sporya.test";
    String adminToken = register(adminEmail, "correct-horse-battery");
    UUID clubId = createClub(adminToken);
    adminToken = login(adminEmail, "correct-horse-battery");

    String coachEmail = "coach+" + System.nanoTime() + "@sporya.test";
    String coachPassword = "correct-horse-battery";
    register(coachEmail, coachPassword);

    restTemplate.exchange(
        "/api/v1/clubs/" + clubId + "/members",
        HttpMethod.POST,
        new HttpEntity<>(new AddMemberRequest(coachEmail, Role.COACH), authHeaders(adminToken)),
        MemberResponse.class);

    String newCoachToken = login(coachEmail, coachPassword);
    Claims claims = jwtService.parseAndValidate(newCoachToken);

    @SuppressWarnings("unchecked")
    List<Map<String, Object>> memberships = claims.get("memberships", List.class);
    assertThat(memberships)
        .anySatisfy(
            m -> {
              assertThat(m.get("clubId")).isEqualTo(clubId.toString());
              assertThat(m.get("role")).isEqualTo("COACH");
            });
  }
}
