package com.sporya.match;

import static org.assertj.core.api.Assertions.assertThat;

import com.sporya.auth.controller.dto.AuthResponse;
import com.sporya.auth.controller.dto.LoginRequest;
import com.sporya.auth.controller.dto.RegisterRequest;
import com.sporya.club.controller.dto.ClubResponse;
import com.sporya.club.controller.dto.CreateClubRequest;
import com.sporya.club.controller.dto.CreatePlayerRequest;
import com.sporya.club.controller.dto.CreateTeamRequest;
import com.sporya.club.controller.dto.PlayerResponse;
import com.sporya.club.controller.dto.TeamResponse;
import com.sporya.match.controller.dto.CompetitionResponse;
import com.sporya.match.controller.dto.CreateCompetitionRequest;
import com.sporya.match.controller.dto.CreateMatchEventRequest;
import com.sporya.match.controller.dto.CreateMatchRequest;
import com.sporya.match.controller.dto.CreateSeasonRequest;
import com.sporya.match.controller.dto.MatchEventResponse;
import com.sporya.match.controller.dto.MatchResponse;
import com.sporya.match.controller.dto.SeasonResponse;
import com.sporya.match.domain.MatchEventType;
import com.sporya.match.domain.MatchStatus;
import java.time.Instant;
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

/** Preuve de bout en bout du module Match : compétition, saison, match, transitions, événements. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class MatchFlowIT {

  @Container @ServiceConnection
  static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

  @Autowired private TestRestTemplate restTemplate;

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

  private ResponseEntity<MatchResponse> transition(
      String accessToken, UUID matchId, String transition) {
    return restTemplate.exchange(
        "/api/v1/matches/" + matchId + "/" + transition,
        HttpMethod.POST,
        new HttpEntity<>(authHeaders(accessToken)),
        MatchResponse.class);
  }

  private UUID createPlayer(String accessToken, UUID teamId) {
    ResponseEntity<PlayerResponse> response =
        restTemplate.exchange(
            "/api/v1/teams/" + teamId + "/players",
            HttpMethod.POST,
            new HttpEntity<>(
                new CreatePlayerRequest("Alex Martin", LocalDate.of(2005, 4, 12), "Milieu"),
                authHeaders(accessToken)),
            PlayerResponse.class);
    return response.getBody().id();
  }

  private ResponseEntity<MatchEventResponse> addEvent(
      String accessToken, UUID matchId, MatchEventType type, int minute, UUID playerId) {
    return restTemplate.exchange(
        "/api/v1/matches/" + matchId + "/events",
        HttpMethod.POST,
        new HttpEntity<>(
            new CreateMatchEventRequest(type, minute, playerId), authHeaders(accessToken)),
        MatchEventResponse.class);
  }

  @Test
  void createCompetitionThenSeasonThenGetThenList() {
    String email = "staff+" + System.nanoTime() + "@sporya.test";
    String accessToken = register(email, "correct-horse-battery");

    UUID competitionId = createCompetition(accessToken);
    ResponseEntity<CompetitionResponse> getCompetition =
        restTemplate.exchange(
            "/api/v1/competitions/" + competitionId,
            HttpMethod.GET,
            new HttpEntity<>(authHeaders(accessToken)),
            CompetitionResponse.class);
    assertThat(getCompetition.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(getCompetition.getBody()).isNotNull();
    assertThat(getCompetition.getBody().name()).isEqualTo("Ligue Sporya");

    UUID seasonId = createSeason(accessToken, competitionId);
    ResponseEntity<SeasonResponse> getSeason =
        restTemplate.exchange(
            "/api/v1/seasons/" + seasonId,
            HttpMethod.GET,
            new HttpEntity<>(authHeaders(accessToken)),
            SeasonResponse.class);
    assertThat(getSeason.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(getSeason.getBody()).isNotNull();
    assertThat(getSeason.getBody().competitionId()).isEqualTo(competitionId);

    ResponseEntity<SeasonResponse[]> listSeasons =
        restTemplate.exchange(
            "/api/v1/competitions/" + competitionId + "/seasons",
            HttpMethod.GET,
            new HttpEntity<>(authHeaders(accessToken)),
            SeasonResponse[].class);
    assertThat(listSeasons.getBody()).isNotNull();
    assertThat(List.of(listSeasons.getBody())).extracting(SeasonResponse::id).contains(seasonId);
  }

  @Test
  void createSeasonUnderUnknownCompetitionReturns404() {
    String email = "staff+" + System.nanoTime() + "@sporya.test";
    String accessToken = register(email, "correct-horse-battery");

    ResponseEntity<Void> response =
        restTemplate.exchange(
            "/api/v1/competitions/" + UUID.randomUUID() + "/seasons",
            HttpMethod.POST,
            new HttpEntity<>(new CreateSeasonRequest("2026"), authHeaders(accessToken)),
            Void.class);
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
  }

  @Test
  void createMatchThenGetThenList() {
    String email = "staff+" + System.nanoTime() + "@sporya.test";
    String password = "correct-horse-battery";
    String accessToken = register(email, password);

    UUID competitionId = createCompetition(accessToken);
    UUID seasonId = createSeason(accessToken, competitionId);

    UUID homeClubId = createClub(accessToken);
    UUID homeTeamId = createTeam(accessToken, homeClubId);
    // Le token émis au login précédent ne porte pas encore le membership ADMIN accordé pendant
    // la création du club (JWT non rafraîchi en direct) : reconnexion nécessaire.
    accessToken = login(email, password);

    UUID awayClubId = createClub(accessToken);
    UUID awayTeamId = createTeam(accessToken, awayClubId);

    ResponseEntity<MatchResponse> createResponse =
        restTemplate.exchange(
            "/api/v1/matches",
            HttpMethod.POST,
            new HttpEntity<>(
                new CreateMatchRequest(
                    seasonId, homeTeamId, awayTeamId, Instant.now().plusSeconds(3600)),
                authHeaders(accessToken)),
            MatchResponse.class);
    assertThat(createResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    assertThat(createResponse.getBody()).isNotNull();
    assertThat(createResponse.getBody().status()).isEqualTo(MatchStatus.SCHEDULED);
    UUID matchId = createResponse.getBody().id();

    ResponseEntity<MatchResponse> getResponse =
        restTemplate.exchange(
            "/api/v1/matches/" + matchId,
            HttpMethod.GET,
            new HttpEntity<>(authHeaders(accessToken)),
            MatchResponse.class);
    assertThat(getResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(getResponse.getBody()).isNotNull();
    assertThat(getResponse.getBody().id()).isEqualTo(matchId);

    ResponseEntity<MatchResponse[]> listResponse =
        restTemplate.exchange(
            "/api/v1/matches",
            HttpMethod.GET,
            new HttpEntity<>(authHeaders(accessToken)),
            MatchResponse[].class);
    assertThat(listResponse.getBody()).isNotNull();
    assertThat(List.of(listResponse.getBody())).extracting(MatchResponse::id).contains(matchId);
  }

  @Test
  void createMatchWithoutHomeClubRoleReturns403() {
    String accessToken =
        register("staff+" + System.nanoTime() + "@sporya.test", "correct-horse-battery");
    UUID competitionId = createCompetition(accessToken);
    UUID seasonId = createSeason(accessToken, competitionId);

    String otherAccessToken =
        register("other+" + System.nanoTime() + "@sporya.test", "correct-horse-battery");
    UUID homeClubId = createClub(otherAccessToken);
    UUID homeTeamId = createTeam(otherAccessToken, homeClubId);
    UUID awayClubId = createClub(accessToken);
    UUID awayTeamId = createTeam(accessToken, awayClubId);

    ResponseEntity<Void> response =
        restTemplate.exchange(
            "/api/v1/matches",
            HttpMethod.POST,
            new HttpEntity<>(
                new CreateMatchRequest(
                    seasonId, homeTeamId, awayTeamId, Instant.now().plusSeconds(3600)),
                authHeaders(accessToken)),
            Void.class);
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
  }

  @Test
  void matchTransitionsThroughFullSequence() {
    String email = "staff+" + System.nanoTime() + "@sporya.test";
    String password = "correct-horse-battery";
    String accessToken = register(email, password);
    UUID competitionId = createCompetition(accessToken);
    UUID seasonId = createSeason(accessToken, competitionId);
    UUID homeClubId = createClub(accessToken);
    UUID homeTeamId = createTeam(accessToken, homeClubId);
    accessToken = login(email, password);
    UUID awayClubId = createClub(accessToken);
    UUID awayTeamId = createTeam(accessToken, awayClubId);
    UUID matchId = createMatch(accessToken, seasonId, homeTeamId, awayTeamId);

    assertThat(transition(accessToken, matchId, "start").getBody().status())
        .isEqualTo(MatchStatus.LIVE);
    assertThat(transition(accessToken, matchId, "half-time").getBody().status())
        .isEqualTo(MatchStatus.HALF_TIME);
    assertThat(transition(accessToken, matchId, "resume").getBody().status())
        .isEqualTo(MatchStatus.LIVE);
    assertThat(transition(accessToken, matchId, "finish").getBody().status())
        .isEqualTo(MatchStatus.FINISHED);
  }

  @Test
  void finishingAScheduledMatchReturns409() {
    String email = "staff+" + System.nanoTime() + "@sporya.test";
    String password = "correct-horse-battery";
    String accessToken = register(email, password);
    UUID competitionId = createCompetition(accessToken);
    UUID seasonId = createSeason(accessToken, competitionId);
    UUID homeClubId = createClub(accessToken);
    UUID homeTeamId = createTeam(accessToken, homeClubId);
    accessToken = login(email, password);
    UUID awayClubId = createClub(accessToken);
    UUID awayTeamId = createTeam(accessToken, awayClubId);
    UUID matchId = createMatch(accessToken, seasonId, homeTeamId, awayTeamId);

    ResponseEntity<Void> response =
        restTemplate.exchange(
            "/api/v1/matches/" + matchId + "/finish",
            HttpMethod.POST,
            new HttpEntity<>(authHeaders(accessToken)),
            Void.class);
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
  }

  @Test
  void addGoalEventWhileLiveThenListIt() {
    String email = "staff+" + System.nanoTime() + "@sporya.test";
    String password = "correct-horse-battery";
    String accessToken = register(email, password);
    UUID competitionId = createCompetition(accessToken);
    UUID seasonId = createSeason(accessToken, competitionId);
    UUID homeClubId = createClub(accessToken);
    UUID homeTeamId = createTeam(accessToken, homeClubId);
    accessToken = login(email, password);
    UUID homePlayerId = createPlayer(accessToken, homeTeamId);
    UUID awayClubId = createClub(accessToken);
    UUID awayTeamId = createTeam(accessToken, awayClubId);
    UUID matchId = createMatch(accessToken, seasonId, homeTeamId, awayTeamId);
    transition(accessToken, matchId, "start");

    ResponseEntity<MatchEventResponse> response =
        addEvent(accessToken, matchId, MatchEventType.GOAL_SCORED, 23, homePlayerId);
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    assertThat(response.getBody()).isNotNull();
    assertThat(response.getBody().teamId()).isEqualTo(homeTeamId);

    ResponseEntity<MatchEventResponse[]> listResponse =
        restTemplate.exchange(
            "/api/v1/matches/" + matchId + "/events",
            HttpMethod.GET,
            new HttpEntity<>(authHeaders(accessToken)),
            MatchEventResponse[].class);
    assertThat(listResponse.getBody()).isNotNull();
    assertThat(List.of(listResponse.getBody()))
        .extracting(MatchEventResponse::id)
        .contains(response.getBody().id());
  }

  @Test
  void addEventBeforeMatchIsLiveReturns409() {
    String email = "staff+" + System.nanoTime() + "@sporya.test";
    String password = "correct-horse-battery";
    String accessToken = register(email, password);
    UUID competitionId = createCompetition(accessToken);
    UUID seasonId = createSeason(accessToken, competitionId);
    UUID homeClubId = createClub(accessToken);
    UUID homeTeamId = createTeam(accessToken, homeClubId);
    accessToken = login(email, password);
    UUID homePlayerId = createPlayer(accessToken, homeTeamId);
    UUID awayClubId = createClub(accessToken);
    UUID awayTeamId = createTeam(accessToken, awayClubId);
    UUID matchId = createMatch(accessToken, seasonId, homeTeamId, awayTeamId);

    ResponseEntity<Void> response =
        restTemplate.exchange(
            "/api/v1/matches/" + matchId + "/events",
            HttpMethod.POST,
            new HttpEntity<>(
                new CreateMatchEventRequest(MatchEventType.GOAL_SCORED, 10, homePlayerId),
                authHeaders(accessToken)),
            Void.class);
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
  }

  @Test
  void addEventForPlayerNotInMatchReturns400() {
    String email = "staff+" + System.nanoTime() + "@sporya.test";
    String password = "correct-horse-battery";
    String accessToken = register(email, password);
    UUID competitionId = createCompetition(accessToken);
    UUID seasonId = createSeason(accessToken, competitionId);
    UUID homeClubId = createClub(accessToken);
    UUID homeTeamId = createTeam(accessToken, homeClubId);
    accessToken = login(email, password);
    UUID awayClubId = createClub(accessToken);
    UUID awayTeamId = createTeam(accessToken, awayClubId);
    UUID matchId = createMatch(accessToken, seasonId, homeTeamId, awayTeamId);
    transition(accessToken, matchId, "start");

    UUID outsiderClubId = createClub(accessToken);
    UUID outsiderTeamId = createTeam(accessToken, outsiderClubId);
    UUID outsiderPlayerId = createPlayer(accessToken, outsiderTeamId);

    ResponseEntity<Void> response =
        restTemplate.exchange(
            "/api/v1/matches/" + matchId + "/events",
            HttpMethod.POST,
            new HttpEntity<>(
                new CreateMatchEventRequest(MatchEventType.GOAL_SCORED, 10, outsiderPlayerId),
                authHeaders(accessToken)),
            Void.class);
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
  }

  @Test
  void redCardBlocksFurtherGoalsButNotOtherEventsForSamePlayer() {
    String email = "staff+" + System.nanoTime() + "@sporya.test";
    String password = "correct-horse-battery";
    String accessToken = register(email, password);
    UUID competitionId = createCompetition(accessToken);
    UUID seasonId = createSeason(accessToken, competitionId);
    UUID homeClubId = createClub(accessToken);
    UUID homeTeamId = createTeam(accessToken, homeClubId);
    accessToken = login(email, password);
    UUID homePlayerId = createPlayer(accessToken, homeTeamId);
    UUID awayClubId = createClub(accessToken);
    UUID awayTeamId = createTeam(accessToken, awayClubId);
    UUID matchId = createMatch(accessToken, seasonId, homeTeamId, awayTeamId);
    transition(accessToken, matchId, "start");

    addEvent(accessToken, matchId, MatchEventType.RED_CARD, 10, homePlayerId);

    ResponseEntity<Void> goalAttempt =
        restTemplate.exchange(
            "/api/v1/matches/" + matchId + "/events",
            HttpMethod.POST,
            new HttpEntity<>(
                new CreateMatchEventRequest(MatchEventType.GOAL_SCORED, 15, homePlayerId),
                authHeaders(accessToken)),
            Void.class);
    assertThat(goalAttempt.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);

    ResponseEntity<MatchEventResponse> yellowAfterRed =
        addEvent(accessToken, matchId, MatchEventType.YELLOW_CARD, 16, homePlayerId);
    assertThat(yellowAfterRed.getStatusCode()).isEqualTo(HttpStatus.CREATED);
  }

  @Test
  void scoreReflectsGoalEventsForBothTeams() {
    String email = "staff+" + System.nanoTime() + "@sporya.test";
    String password = "correct-horse-battery";
    String accessToken = register(email, password);
    UUID competitionId = createCompetition(accessToken);
    UUID seasonId = createSeason(accessToken, competitionId);
    UUID homeClubId = createClub(accessToken);
    UUID homeTeamId = createTeam(accessToken, homeClubId);
    accessToken = login(email, password);
    UUID homePlayerId = createPlayer(accessToken, homeTeamId);
    UUID awayClubId = createClub(accessToken);
    UUID awayTeamId = createTeam(accessToken, awayClubId);
    UUID awayPlayerId = createPlayer(accessToken, awayTeamId);
    UUID matchId = createMatch(accessToken, seasonId, homeTeamId, awayTeamId);
    transition(accessToken, matchId, "start");

    addEvent(accessToken, matchId, MatchEventType.GOAL_SCORED, 10, homePlayerId);
    addEvent(accessToken, matchId, MatchEventType.GOAL_SCORED, 20, homePlayerId);
    addEvent(accessToken, matchId, MatchEventType.GOAL_SCORED, 30, awayPlayerId);

    ResponseEntity<MatchResponse> getResponse =
        restTemplate.exchange(
            "/api/v1/matches/" + matchId,
            HttpMethod.GET,
            new HttpEntity<>(authHeaders(accessToken)),
            MatchResponse.class);
    assertThat(getResponse.getBody()).isNotNull();
    assertThat(getResponse.getBody().homeScore()).isEqualTo(2);
    assertThat(getResponse.getBody().awayScore()).isEqualTo(1);
  }
}
