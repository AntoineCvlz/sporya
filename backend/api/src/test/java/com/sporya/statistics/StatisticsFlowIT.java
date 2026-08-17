package com.sporya.statistics;

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
import com.sporya.statistics.controller.dto.PlayerSeasonStatisticsResponse;
import com.sporya.statistics.controller.dto.TeamSeasonStatisticsResponse;
import com.sporya.statistics.domain.MatchOutcome;
import com.sporya.statistics.domain.PlayerMatchStatistics;
import com.sporya.statistics.domain.TeamMatchStatistics;
import com.sporya.statistics.infrastructure.persistence.PlayerMatchStatisticsRepository;
import com.sporya.statistics.infrastructure.persistence.TeamMatchStatisticsRepository;
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
import org.springframework.http.ResponseEntity;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/** Preuve de bout en bout du module Statistics : persistance déclenchée par la fin d'un match. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class StatisticsFlowIT {

  @Container @ServiceConnection
  static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

  @Autowired private TestRestTemplate restTemplate;
  @Autowired private PlayerMatchStatisticsRepository playerMatchStatisticsRepository;
  @Autowired private TeamMatchStatisticsRepository teamMatchStatisticsRepository;

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

  private void addEvent(
      String accessToken, UUID matchId, MatchEventType type, int minute, UUID playerId) {
    restTemplate.exchange(
        "/api/v1/matches/" + matchId + "/events",
        HttpMethod.POST,
        new HttpEntity<>(
            new CreateMatchEventRequest(type, minute, playerId), authHeaders(accessToken)),
        MatchEventResponse.class);
  }

  @Test
  void finishingAMatchPersistsPlayerAndTeamStatistics() {
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
    addEvent(accessToken, matchId, MatchEventType.GOAL_SCORED, 10, homePlayerId);
    addEvent(accessToken, matchId, MatchEventType.YELLOW_CARD, 20, homePlayerId);
    transition(accessToken, matchId, "finish");

    List<PlayerMatchStatistics> playerStats =
        playerMatchStatisticsRepository.findByPlayerIdAndSeasonId(homePlayerId, seasonId);
    assertThat(playerStats).hasSize(1);
    assertThat(playerStats.get(0).getGoals()).isEqualTo(1);
    assertThat(playerStats.get(0).getYellowCards()).isEqualTo(1);
    assertThat(playerStats.get(0).getRedCards()).isEqualTo(0);
    assertThat(playerStats.get(0).getTeamId()).isEqualTo(homeTeamId);

    List<TeamMatchStatistics> homeTeamStats =
        teamMatchStatisticsRepository.findByTeamIdAndSeasonId(homeTeamId, seasonId);
    assertThat(homeTeamStats).hasSize(1);
    assertThat(homeTeamStats.get(0).getGoalsFor()).isEqualTo(1);
    assertThat(homeTeamStats.get(0).getGoalsAgainst()).isEqualTo(0);
    assertThat(homeTeamStats.get(0).getResult()).isEqualTo(MatchOutcome.WIN);

    List<TeamMatchStatistics> awayTeamStats =
        teamMatchStatisticsRepository.findByTeamIdAndSeasonId(awayTeamId, seasonId);
    assertThat(awayTeamStats).hasSize(1);
    assertThat(awayTeamStats.get(0).getGoalsFor()).isEqualTo(0);
    assertThat(awayTeamStats.get(0).getGoalsAgainst()).isEqualTo(1);
    assertThat(awayTeamStats.get(0).getResult()).isEqualTo(MatchOutcome.LOSS);
  }

  @Test
  void aMatchThatNeverFinishesGeneratesNoStatistics() {
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
    createMatch(accessToken, seasonId, homeTeamId, awayTeamId);

    assertThat(teamMatchStatisticsRepository.findByTeamIdAndSeasonId(homeTeamId, seasonId))
        .isEmpty();
  }

  @Test
  void playerSeasonStatisticsAggregateAcrossMatches() {
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

    UUID firstMatchId = createMatch(accessToken, seasonId, homeTeamId, awayTeamId);
    transition(accessToken, firstMatchId, "start");
    addEvent(accessToken, firstMatchId, MatchEventType.GOAL_SCORED, 10, homePlayerId);
    transition(accessToken, firstMatchId, "finish");

    UUID secondMatchId = createMatch(accessToken, seasonId, homeTeamId, awayTeamId);
    transition(accessToken, secondMatchId, "start");
    addEvent(accessToken, secondMatchId, MatchEventType.GOAL_SCORED, 5, homePlayerId);
    addEvent(accessToken, secondMatchId, MatchEventType.GOAL_SCORED, 15, homePlayerId);
    transition(accessToken, secondMatchId, "finish");

    ResponseEntity<PlayerSeasonStatisticsResponse> response =
        restTemplate.exchange(
            "/api/v1/players/" + homePlayerId + "/seasons/" + seasonId + "/statistics",
            HttpMethod.GET,
            new HttpEntity<>(authHeaders(accessToken)),
            PlayerSeasonStatisticsResponse.class);
    assertThat(response.getBody()).isNotNull();
    assertThat(response.getBody().goals()).isEqualTo(3);
    assertThat(response.getBody().matchesPlayed()).isEqualTo(2);
  }

  @Test
  void playerSeasonStatisticsForUnknownPlayerReturnsZeros() {
    String accessToken =
        register("staff+" + System.nanoTime() + "@sporya.test", "correct-horse-battery");
    UUID competitionId = createCompetition(accessToken);
    UUID seasonId = createSeason(accessToken, competitionId);

    ResponseEntity<PlayerSeasonStatisticsResponse> response =
        restTemplate.exchange(
            "/api/v1/players/" + UUID.randomUUID() + "/seasons/" + seasonId + "/statistics",
            HttpMethod.GET,
            new HttpEntity<>(authHeaders(accessToken)),
            PlayerSeasonStatisticsResponse.class);
    assertThat(response.getBody()).isNotNull();
    assertThat(response.getBody().goals()).isEqualTo(0);
    assertThat(response.getBody().matchesPlayed()).isEqualTo(0);
  }

  @Test
  void teamSeasonStatisticsAggregateWinsDrawsLosses() {
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

    UUID winMatchId = createMatch(accessToken, seasonId, homeTeamId, awayTeamId);
    transition(accessToken, winMatchId, "start");
    addEvent(accessToken, winMatchId, MatchEventType.GOAL_SCORED, 10, homePlayerId);
    addEvent(accessToken, winMatchId, MatchEventType.GOAL_SCORED, 20, homePlayerId);
    transition(accessToken, winMatchId, "finish");

    UUID lossMatchId = createMatch(accessToken, seasonId, homeTeamId, awayTeamId);
    transition(accessToken, lossMatchId, "start");
    addEvent(accessToken, lossMatchId, MatchEventType.GOAL_SCORED, 10, awayPlayerId);
    transition(accessToken, lossMatchId, "finish");

    ResponseEntity<TeamSeasonStatisticsResponse> response =
        restTemplate.exchange(
            "/api/v1/teams/" + homeTeamId + "/seasons/" + seasonId + "/statistics",
            HttpMethod.GET,
            new HttpEntity<>(authHeaders(accessToken)),
            TeamSeasonStatisticsResponse.class);
    assertThat(response.getBody()).isNotNull();
    assertThat(response.getBody().wins()).isEqualTo(1);
    assertThat(response.getBody().draws()).isEqualTo(0);
    assertThat(response.getBody().losses()).isEqualTo(1);
    assertThat(response.getBody().goalsFor()).isEqualTo(2);
    assertThat(response.getBody().goalsAgainst()).isEqualTo(1);
  }

  @Test
  void teamSeasonStatisticsDoNotLeakAcrossSeasons() {
    String email = "staff+" + System.nanoTime() + "@sporya.test";
    String password = "correct-horse-battery";
    String accessToken = register(email, password);
    UUID competitionId = createCompetition(accessToken);
    UUID firstSeasonId = createSeason(accessToken, competitionId);
    UUID secondSeasonId = createSeason(accessToken, competitionId);
    UUID homeClubId = createClub(accessToken);
    UUID homeTeamId = createTeam(accessToken, homeClubId);
    accessToken = login(email, password);
    UUID homePlayerId = createPlayer(accessToken, homeTeamId);
    UUID awayClubId = createClub(accessToken);
    UUID awayTeamId = createTeam(accessToken, awayClubId);

    UUID matchId = createMatch(accessToken, firstSeasonId, homeTeamId, awayTeamId);
    transition(accessToken, matchId, "start");
    addEvent(accessToken, matchId, MatchEventType.GOAL_SCORED, 10, homePlayerId);
    transition(accessToken, matchId, "finish");

    ResponseEntity<TeamSeasonStatisticsResponse> secondSeasonResponse =
        restTemplate.exchange(
            "/api/v1/teams/" + homeTeamId + "/seasons/" + secondSeasonId + "/statistics",
            HttpMethod.GET,
            new HttpEntity<>(authHeaders(accessToken)),
            TeamSeasonStatisticsResponse.class);
    assertThat(secondSeasonResponse.getBody()).isNotNull();
    assertThat(secondSeasonResponse.getBody().wins()).isEqualTo(0);
    assertThat(secondSeasonResponse.getBody().goalsFor()).isEqualTo(0);
  }
}
