package com.sporya.notification;

import static org.assertj.core.api.Assertions.assertThat;

import com.sporya.auth.controller.dto.AuthResponse;
import com.sporya.auth.controller.dto.LoginRequest;
import com.sporya.auth.controller.dto.RegisterRequest;
import com.sporya.auth.infrastructure.security.JwtService;
import com.sporya.club.controller.dto.ClubResponse;
import com.sporya.club.controller.dto.CreateClubRequest;
import com.sporya.club.controller.dto.CreateTeamRequest;
import com.sporya.club.controller.dto.TeamResponse;
import com.sporya.match.controller.dto.CompetitionResponse;
import com.sporya.match.controller.dto.CreateCompetitionRequest;
import com.sporya.match.controller.dto.CreateMatchRequest;
import com.sporya.match.controller.dto.CreateSeasonRequest;
import com.sporya.match.controller.dto.MatchResponse;
import com.sporya.match.controller.dto.SeasonResponse;
import com.sporya.notification.domain.Notification;
import com.sporya.notification.infrastructure.persistence.NotificationRepository;
import java.time.Instant;
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
import org.springframework.http.ResponseEntity;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Preuve de bout en bout du module Notification : notifications déclenchées par la fin d'un match.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class NotificationFlowIT {

  @Container @ServiceConnection
  static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

  @Autowired private TestRestTemplate restTemplate;
  @Autowired private JwtService jwtService;
  @Autowired private NotificationRepository notificationRepository;

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

  private UUID userIdOf(String accessToken) {
    return UUID.fromString(jwtService.parseAndValidate(accessToken).getSubject());
  }

  private HttpHeaders authHeaders(String accessToken) {
    HttpHeaders headers = new HttpHeaders();
    headers.setBearerAuth(accessToken);
    return headers;
  }

  private UUID createCompetition(String accessToken) {
    ResponseEntity<CompetitionResponse> response =
        restTemplate.exchange(
            "/api/v1/competitions",
            HttpMethod.POST,
            new HttpEntity<>(
                new CreateCompetitionRequest("Ligue Sporya"), authHeaders(accessToken)),
            CompetitionResponse.class);
    return response.getBody().id();
  }

  private UUID createSeason(String accessToken, UUID competitionId) {
    ResponseEntity<SeasonResponse> response =
        restTemplate.exchange(
            "/api/v1/competitions/" + competitionId + "/seasons",
            HttpMethod.POST,
            new HttpEntity<>(new CreateSeasonRequest("2026"), authHeaders(accessToken)),
            SeasonResponse.class);
    return response.getBody().id();
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
            new HttpEntity<>(new CreateTeamRequest("Equipe A"), authHeaders(accessToken)),
            TeamResponse.class);
    return response.getBody().id();
  }

  private UUID createMatch(String accessToken, UUID seasonId, UUID homeTeamId, UUID awayTeamId) {
    ResponseEntity<MatchResponse> response =
        restTemplate.exchange(
            "/api/v1/matches",
            HttpMethod.POST,
            new HttpEntity<>(
                new CreateMatchRequest(
                    seasonId, homeTeamId, awayTeamId, Instant.now().plusSeconds(3600)),
                authHeaders(accessToken)),
            MatchResponse.class);
    return response.getBody().id();
  }

  private void transition(String accessToken, UUID matchId, String transition) {
    restTemplate.exchange(
        "/api/v1/matches/" + matchId + "/" + transition,
        HttpMethod.POST,
        new HttpEntity<>(authHeaders(accessToken)),
        MatchResponse.class);
  }

  @Test
  void finishingAMatchNotifiesMembersOfBothClubs() {
    String homeEmail = "home+" + System.nanoTime() + "@sporya.test";
    String homePassword = "correct-horse-battery";
    String homeAccessToken = register(homeEmail, homePassword);
    UUID homeUserId = userIdOf(homeAccessToken);
    UUID competitionId = createCompetition(homeAccessToken);
    UUID seasonId = createSeason(homeAccessToken, competitionId);
    UUID homeClubId = createClub(homeAccessToken);
    UUID homeTeamId = createTeam(homeAccessToken, homeClubId);
    homeAccessToken = login(homeEmail, homePassword);

    String awayEmail = "away+" + System.nanoTime() + "@sporya.test";
    String awayPassword = "correct-horse-battery";
    String awayAccessToken = register(awayEmail, awayPassword);
    UUID awayUserId = userIdOf(awayAccessToken);
    UUID awayClubId = createClub(awayAccessToken);
    UUID awayTeamId = createTeam(awayAccessToken, awayClubId);

    UUID matchId = createMatch(homeAccessToken, seasonId, homeTeamId, awayTeamId);
    transition(homeAccessToken, matchId, "start");
    transition(homeAccessToken, matchId, "finish");

    List<Notification> homeNotifications =
        notificationRepository.findByUserIdOrderByCreatedAtDesc(homeUserId);
    assertThat(homeNotifications).hasSize(1);
    assertThat(homeNotifications.get(0).getMatchId()).isEqualTo(matchId);

    List<Notification> awayNotifications =
        notificationRepository.findByUserIdOrderByCreatedAtDesc(awayUserId);
    assertThat(awayNotifications).hasSize(1);
    assertThat(awayNotifications.get(0).getMatchId()).isEqualTo(matchId);
  }

  @Test
  void aMemberOfBothClubsReceivesOneNotification() {
    String email = "staff+" + System.nanoTime() + "@sporya.test";
    String password = "correct-horse-battery";
    String accessToken = register(email, password);
    UUID userId = userIdOf(accessToken);
    UUID competitionId = createCompetition(accessToken);
    UUID seasonId = createSeason(accessToken, competitionId);
    UUID homeClubId = createClub(accessToken);
    UUID homeTeamId = createTeam(accessToken, homeClubId);
    accessToken = login(email, password);
    UUID awayClubId = createClub(accessToken);
    UUID awayTeamId = createTeam(accessToken, awayClubId);

    UUID matchId = createMatch(accessToken, seasonId, homeTeamId, awayTeamId);
    transition(accessToken, matchId, "start");
    transition(accessToken, matchId, "finish");

    assertThat(notificationRepository.findByUserIdOrderByCreatedAtDesc(userId)).hasSize(1);
  }

  @Test
  void aMatchThatNeverFinishesGeneratesNoNotifications() {
    String email = "staff+" + System.nanoTime() + "@sporya.test";
    String password = "correct-horse-battery";
    String accessToken = register(email, password);
    UUID userId = userIdOf(accessToken);
    UUID competitionId = createCompetition(accessToken);
    UUID seasonId = createSeason(accessToken, competitionId);
    UUID homeClubId = createClub(accessToken);
    UUID homeTeamId = createTeam(accessToken, homeClubId);
    accessToken = login(email, password);
    UUID awayClubId = createClub(accessToken);
    UUID awayTeamId = createTeam(accessToken, awayClubId);
    createMatch(accessToken, seasonId, homeTeamId, awayTeamId);

    assertThat(notificationRepository.findByUserIdOrderByCreatedAtDesc(userId)).isEmpty();
  }
}
