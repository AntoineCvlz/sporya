package com.sporya.club;

import static org.assertj.core.api.Assertions.assertThat;

import com.sporya.auth.application.MembershipService;
import com.sporya.auth.controller.dto.AuthResponse;
import com.sporya.auth.controller.dto.LoginRequest;
import com.sporya.auth.controller.dto.RegisterRequest;
import com.sporya.auth.domain.ClubRole;
import com.sporya.auth.domain.Role;
import com.sporya.auth.infrastructure.security.JwtService;
import com.sporya.club.controller.dto.ClubResponse;
import com.sporya.club.controller.dto.CreateClubRequest;
import com.sporya.club.controller.dto.CreatePlayerRequest;
import com.sporya.club.controller.dto.CreateTeamRequest;
import com.sporya.club.controller.dto.PlayerResponse;
import com.sporya.club.controller.dto.TeamResponse;
import java.time.LocalDate;
import java.util.List;
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

/** Preuve de bout en bout du module Club : créer un club, le lire, le lister. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class ClubFlowIT {

  @Container @ServiceConnection
  static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

  @Autowired private TestRestTemplate restTemplate;
  @Autowired private MembershipService membershipService;
  @Autowired private JwtService jwtService;

  private String registerAndLogin() {
    String email = "coach+" + System.nanoTime() + "@sporya.test";
    String password = "correct-horse-battery";
    restTemplate.postForEntity(
        "/api/v1/auth/register", new RegisterRequest(email, password), Void.class);
    ResponseEntity<AuthResponse> loginResponse =
        restTemplate.postForEntity(
            "/api/v1/auth/login", new LoginRequest(email, password), AuthResponse.class);
    return loginResponse.getBody().accessToken();
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

  private UUID createTeam(String accessToken, UUID clubId) {
    ResponseEntity<TeamResponse> response =
        restTemplate.exchange(
            "/api/v1/clubs/" + clubId + "/teams",
            HttpMethod.POST,
            new HttpEntity<>(new CreateTeamRequest("U19"), authHeaders(accessToken)),
            TeamResponse.class);
    return response.getBody().id();
  }

  @Test
  void createClubThenGetThenList() {
    String accessToken = registerAndLogin();

    ResponseEntity<ClubResponse> createResponse =
        restTemplate.exchange(
            "/api/v1/clubs",
            HttpMethod.POST,
            new HttpEntity<>(
                new CreateClubRequest("FC Sporya", "France"), authHeaders(accessToken)),
            ClubResponse.class);
    assertThat(createResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    assertThat(createResponse.getBody()).isNotNull();
    assertThat(createResponse.getBody().name()).isEqualTo("FC Sporya");
    UUID clubId = createResponse.getBody().id();

    ResponseEntity<ClubResponse> getResponse =
        restTemplate.exchange(
            "/api/v1/clubs/" + clubId,
            HttpMethod.GET,
            new HttpEntity<>(authHeaders(accessToken)),
            ClubResponse.class);
    assertThat(getResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(getResponse.getBody()).isNotNull();
    assertThat(getResponse.getBody().id()).isEqualTo(clubId);

    ResponseEntity<ClubResponse[]> listResponse =
        restTemplate.exchange(
            "/api/v1/clubs",
            HttpMethod.GET,
            new HttpEntity<>(authHeaders(accessToken)),
            ClubResponse[].class);
    assertThat(listResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(listResponse.getBody()).isNotNull();
    assertThat(List.of(listResponse.getBody())).extracting(ClubResponse::id).contains(clubId);
  }

  @Test
  void getUnknownClubReturns404() {
    String accessToken = registerAndLogin();

    ResponseEntity<Void> response =
        restTemplate.exchange(
            "/api/v1/clubs/" + UUID.randomUUID(),
            HttpMethod.GET,
            new HttpEntity<>(authHeaders(accessToken)),
            Void.class);
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
  }

  @Test
  void createClubWithoutAuthReturns401() {
    ResponseEntity<Void> response =
        restTemplate.postForEntity(
            "/api/v1/clubs", new CreateClubRequest("FC Sporya", "France"), Void.class);
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
  }

  @Test
  void createClubGrantsCreatorAdminMembership() {
    String accessToken = registerAndLogin();
    UUID clubId = createClub(accessToken);
    UUID userId = UUID.fromString(jwtService.parseAndValidate(accessToken).getSubject());

    assertThat(membershipService.membershipsFor(userId)).contains(new ClubRole(clubId, Role.ADMIN));
  }

  @Test
  void createTeamThenListThenGet() {
    String accessToken = registerAndLogin();
    UUID clubId = createClub(accessToken);

    ResponseEntity<TeamResponse> createResponse =
        restTemplate.exchange(
            "/api/v1/clubs/" + clubId + "/teams",
            HttpMethod.POST,
            new HttpEntity<>(new CreateTeamRequest("U19"), authHeaders(accessToken)),
            TeamResponse.class);
    assertThat(createResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    assertThat(createResponse.getBody()).isNotNull();
    assertThat(createResponse.getBody().name()).isEqualTo("U19");
    assertThat(createResponse.getBody().clubId()).isEqualTo(clubId);
    UUID teamId = createResponse.getBody().id();

    ResponseEntity<TeamResponse[]> listResponse =
        restTemplate.exchange(
            "/api/v1/clubs/" + clubId + "/teams",
            HttpMethod.GET,
            new HttpEntity<>(authHeaders(accessToken)),
            TeamResponse[].class);
    assertThat(listResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(listResponse.getBody()).isNotNull();
    assertThat(List.of(listResponse.getBody())).extracting(TeamResponse::id).contains(teamId);

    ResponseEntity<TeamResponse> getResponse =
        restTemplate.exchange(
            "/api/v1/teams/" + teamId,
            HttpMethod.GET,
            new HttpEntity<>(authHeaders(accessToken)),
            TeamResponse.class);
    assertThat(getResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(getResponse.getBody()).isNotNull();
    assertThat(getResponse.getBody().id()).isEqualTo(teamId);
  }

  @Test
  void createTeamUnderUnknownClubReturns404() {
    String accessToken = registerAndLogin();

    ResponseEntity<Void> response =
        restTemplate.exchange(
            "/api/v1/clubs/" + UUID.randomUUID() + "/teams",
            HttpMethod.POST,
            new HttpEntity<>(new CreateTeamRequest("U19"), authHeaders(accessToken)),
            Void.class);
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
  }

  @Test
  void createPlayerThenList() {
    String accessToken = registerAndLogin();
    UUID clubId = createClub(accessToken);
    UUID teamId = createTeam(accessToken, clubId);

    ResponseEntity<PlayerResponse> createResponse =
        restTemplate.exchange(
            "/api/v1/teams/" + teamId + "/players",
            HttpMethod.POST,
            new HttpEntity<>(
                new CreatePlayerRequest("Alex Martin", LocalDate.of(2005, 4, 12), "Milieu"),
                authHeaders(accessToken)),
            PlayerResponse.class);
    assertThat(createResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    assertThat(createResponse.getBody()).isNotNull();
    assertThat(createResponse.getBody().name()).isEqualTo("Alex Martin");
    assertThat(createResponse.getBody().teamId()).isEqualTo(teamId);
    UUID playerId = createResponse.getBody().id();

    ResponseEntity<PlayerResponse[]> listResponse =
        restTemplate.exchange(
            "/api/v1/teams/" + teamId + "/players",
            HttpMethod.GET,
            new HttpEntity<>(authHeaders(accessToken)),
            PlayerResponse[].class);
    assertThat(listResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(listResponse.getBody()).isNotNull();
    assertThat(List.of(listResponse.getBody())).extracting(PlayerResponse::id).contains(playerId);
  }

  @Test
  void createPlayerUnderUnknownTeamReturns404() {
    String accessToken = registerAndLogin();

    ResponseEntity<Void> response =
        restTemplate.exchange(
            "/api/v1/teams/" + UUID.randomUUID() + "/players",
            HttpMethod.POST,
            new HttpEntity<>(
                new CreatePlayerRequest("Alex Martin", LocalDate.of(2005, 4, 12), "Milieu"),
                authHeaders(accessToken)),
            Void.class);
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
  }
}
