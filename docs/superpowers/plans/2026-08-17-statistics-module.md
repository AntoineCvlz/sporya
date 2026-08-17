# Statistics Module Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build the Statistics module (`com.sporya.statistics`) — `PlayerMatchStatistics`/`TeamMatchStatistics` persisted per finished match (populated via an in-process Spring event from Match), plus season-scoped aggregate endpoints for players and teams.

**Architecture:** Same layered structure as the other modules (`controller/application/domain/infrastructure`), new Postgres schema `statistics`. `Match.finish()` publishes `MatchFinishedEvent`; a `@TransactionalEventListener(phase = AFTER_COMMIT)` in Statistics builds the per-match rows by calling `MatchEventService.listForMatch` (Match's application layer, same cross-module convention as Match → Club). Two read-only aggregation services sum those rows per season, in Java (not SQL `SUM`), since row counts per season are small. Built as three vertical slices (persistence + listener → player aggregate → team aggregate), each proven by extending one growing integration test (`StatisticsFlowIT`), mirroring `MatchFlowIT`.

**Tech Stack:** Spring Boot 3.5 / Java 21 (existing `backend/api`), Spring Data JPA, Flyway, Spring's `ApplicationEventPublisher`/`@TransactionalEventListener` (already on the classpath, no new dependency), Testcontainers + `TestRestTemplate`.

**Spec:** `docs/superpowers/specs/2026-08-17-statistics-module-design.md`

## Global Constraints

- Coexists with Match's existing derived endpoints (`GET /players/{id}/stats`, `GET /teams/{id}/form`) — no changes to those, no removal.
- Triggered by `MatchFinishedEvent` (Spring `ApplicationEventPublisher`), consumed via `@TransactionalEventListener(phase = AFTER_COMMIT)` — no `@Async`, no new thread pool.
- `TeamMatchStatistics`: always 2 rows per finished match (home + away). `PlayerMatchStatistics`: one row per player who has **at least one** `MatchEvent` in that match — no row for players with zero events (no lineup/squad concept exists).
- No `assists`/`possession`/`shots` fields — no `MatchEvent` type captures them.
- `season_id` stored directly on both tables (known from `MatchFinishedEvent`) — no cross-module call to Match needed at read time.
- No cross-module validation of `playerId`/`teamId`/`seasonId` on the read endpoints — unknown IDs return a zero-valued aggregate, not 404.
- Migration file: `backend/api/src/main/resources/db/migration/V5__create_statistics_tables.sql`, flat folder, `CREATE SCHEMA IF NOT EXISTS statistics;` explicit as the first statement (see the note on `V4` for why).

---

## Task 1: `MatchFinishedEvent` + Statistics persistence + listener

**Files:**
- Create: `backend/api/src/main/resources/db/migration/V5__create_statistics_tables.sql`
- Modify: `backend/api/src/main/resources/application.yml`
- Create: `backend/api/src/main/java/com/sporya/match/domain/MatchFinishedEvent.java`
- Modify: `backend/api/src/main/java/com/sporya/match/application/MatchService.java`
- Create: `backend/api/src/main/java/com/sporya/statistics/domain/MatchOutcome.java`
- Create: `backend/api/src/main/java/com/sporya/statistics/domain/PlayerMatchStatistics.java`
- Create: `backend/api/src/main/java/com/sporya/statistics/domain/TeamMatchStatistics.java`
- Create: `backend/api/src/main/java/com/sporya/statistics/infrastructure/persistence/PlayerMatchStatisticsRepository.java`
- Create: `backend/api/src/main/java/com/sporya/statistics/infrastructure/persistence/TeamMatchStatisticsRepository.java`
- Create: `backend/api/src/main/java/com/sporya/statistics/application/MatchFinishedListener.java`
- Test: `backend/api/src/test/java/com/sporya/statistics/StatisticsFlowIT.java`

**Interfaces:**
- Consumes: `com.sporya.match.application.MatchEventService.listForMatch(UUID): List<MatchEventResponse>` (existing, returns `MatchEventResponse(id, matchId, type, minute, playerId, teamId, createdAt)` — already carries the denormalized `teamId` needed here).
- Produces: `MatchFinishedEvent(UUID matchId, UUID homeTeamId, UUID awayTeamId, UUID seasonId)`, `PlayerMatchStatisticsRepository.findByPlayerIdAndSeasonId(UUID, UUID): List<PlayerMatchStatistics>`, `TeamMatchStatisticsRepository.findByTeamIdAndSeasonId(UUID, UUID): List<TeamMatchStatistics>`. Task 2 depends on `PlayerMatchStatisticsRepository` + `PlayerMatchStatistics` getters. Task 3 depends on `TeamMatchStatisticsRepository` + `TeamMatchStatistics` getters + `MatchOutcome`.

- [ ] **Step 1: Create the migration for both Statistics tables**

`backend/api/src/main/resources/db/migration/V5__create_statistics_tables.sql`:
```sql
CREATE SCHEMA IF NOT EXISTS statistics;

CREATE TABLE statistics.player_match_statistics (
    id UUID PRIMARY KEY,
    player_id UUID NOT NULL,
    match_id UUID NOT NULL,
    team_id UUID NOT NULL,
    season_id UUID NOT NULL,
    goals INT NOT NULL,
    yellow_cards INT NOT NULL,
    red_cards INT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (player_id, match_id)
);

CREATE TABLE statistics.team_match_statistics (
    id UUID PRIMARY KEY,
    team_id UUID NOT NULL,
    match_id UUID NOT NULL,
    season_id UUID NOT NULL,
    goals_for INT NOT NULL,
    goals_against INT NOT NULL,
    result VARCHAR(10) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (team_id, match_id)
);
```

- [ ] **Step 2: Add the `statistics` schema to Flyway's managed schemas**

In `backend/api/src/main/resources/application.yml`, find:
```yaml
  flyway:
    schemas: auth,club,match
    default-schema: auth
    create-schemas: true
```
Replace with:
```yaml
  flyway:
    schemas: auth,club,match,statistics
    default-schema: auth
    create-schemas: true
```

- [ ] **Step 3: Write `MatchFinishedEvent`**

`backend/api/src/main/java/com/sporya/match/domain/MatchFinishedEvent.java`:
```java
package com.sporya.match.domain;

import java.util.UUID;

public record MatchFinishedEvent(UUID matchId, UUID homeTeamId, UUID awayTeamId, UUID seasonId) {}
```

- [ ] **Step 4: Write the Statistics domain entities**

`backend/api/src/main/java/com/sporya/statistics/domain/MatchOutcome.java`:
```java
package com.sporya.statistics.domain;

public enum MatchOutcome {
  WIN,
  DRAW,
  LOSS
}
```

`backend/api/src/main/java/com/sporya/statistics/domain/PlayerMatchStatistics.java`:
```java
package com.sporya.statistics.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "player_match_statistics", schema = "statistics")
public class PlayerMatchStatistics {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(name = "player_id", nullable = false)
  private UUID playerId;

  @Column(name = "match_id", nullable = false)
  private UUID matchId;

  @Column(name = "team_id", nullable = false)
  private UUID teamId;

  @Column(name = "season_id", nullable = false)
  private UUID seasonId;

  @Column(nullable = false)
  private int goals;

  @Column(name = "yellow_cards", nullable = false)
  private int yellowCards;

  @Column(name = "red_cards", nullable = false)
  private int redCards;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  protected PlayerMatchStatistics() {}

  public PlayerMatchStatistics(
      UUID playerId,
      UUID matchId,
      UUID teamId,
      UUID seasonId,
      int goals,
      int yellowCards,
      int redCards) {
    this.playerId = playerId;
    this.matchId = matchId;
    this.teamId = teamId;
    this.seasonId = seasonId;
    this.goals = goals;
    this.yellowCards = yellowCards;
    this.redCards = redCards;
    this.createdAt = Instant.now();
  }

  public UUID getId() {
    return id;
  }

  public UUID getPlayerId() {
    return playerId;
  }

  public UUID getMatchId() {
    return matchId;
  }

  public UUID getTeamId() {
    return teamId;
  }

  public UUID getSeasonId() {
    return seasonId;
  }

  public int getGoals() {
    return goals;
  }

  public int getYellowCards() {
    return yellowCards;
  }

  public int getRedCards() {
    return redCards;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }
}
```

`backend/api/src/main/java/com/sporya/statistics/domain/TeamMatchStatistics.java`:
```java
package com.sporya.statistics.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "team_match_statistics", schema = "statistics")
public class TeamMatchStatistics {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(name = "team_id", nullable = false)
  private UUID teamId;

  @Column(name = "match_id", nullable = false)
  private UUID matchId;

  @Column(name = "season_id", nullable = false)
  private UUID seasonId;

  @Column(name = "goals_for", nullable = false)
  private int goalsFor;

  @Column(name = "goals_against", nullable = false)
  private int goalsAgainst;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 10)
  private MatchOutcome result;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  protected TeamMatchStatistics() {}

  public TeamMatchStatistics(
      UUID teamId,
      UUID matchId,
      UUID seasonId,
      int goalsFor,
      int goalsAgainst,
      MatchOutcome result) {
    this.teamId = teamId;
    this.matchId = matchId;
    this.seasonId = seasonId;
    this.goalsFor = goalsFor;
    this.goalsAgainst = goalsAgainst;
    this.result = result;
    this.createdAt = Instant.now();
  }

  public UUID getId() {
    return id;
  }

  public UUID getTeamId() {
    return teamId;
  }

  public UUID getMatchId() {
    return matchId;
  }

  public UUID getSeasonId() {
    return seasonId;
  }

  public int getGoalsFor() {
    return goalsFor;
  }

  public int getGoalsAgainst() {
    return goalsAgainst;
  }

  public MatchOutcome getResult() {
    return result;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }
}
```

- [ ] **Step 5: Write the repositories**

`backend/api/src/main/java/com/sporya/statistics/infrastructure/persistence/PlayerMatchStatisticsRepository.java`:
```java
package com.sporya.statistics.infrastructure.persistence;

import com.sporya.statistics.domain.PlayerMatchStatistics;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PlayerMatchStatisticsRepository extends JpaRepository<PlayerMatchStatistics, UUID> {

  List<PlayerMatchStatistics> findByPlayerIdAndSeasonId(UUID playerId, UUID seasonId);
}
```

`backend/api/src/main/java/com/sporya/statistics/infrastructure/persistence/TeamMatchStatisticsRepository.java`:
```java
package com.sporya.statistics.infrastructure.persistence;

import com.sporya.statistics.domain.TeamMatchStatistics;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TeamMatchStatisticsRepository extends JpaRepository<TeamMatchStatistics, UUID> {

  List<TeamMatchStatistics> findByTeamIdAndSeasonId(UUID teamId, UUID seasonId);
}
```

- [ ] **Step 6: Write the failing test**

`backend/api/src/test/java/com/sporya/statistics/StatisticsFlowIT.java`:
```java
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
            new HttpEntity<>(new CreateCompetitionRequest("Ligue Sporya"), authHeaders(accessToken)),
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
            new HttpEntity<>(new CreateClubRequest("FC Sporya", "France"), authHeaders(accessToken)),
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
                new CreateMatchRequest(seasonId, homeTeamId, awayTeamId, Instant.now().plusSeconds(3600)),
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

  private void addEvent(String accessToken, UUID matchId, MatchEventType type, int minute, UUID playerId) {
    restTemplate.exchange(
        "/api/v1/matches/" + matchId + "/events",
        HttpMethod.POST,
        new HttpEntity<>(new CreateMatchEventRequest(type, minute, playerId), authHeaders(accessToken)),
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

    assertThat(teamMatchStatisticsRepository.findByTeamIdAndSeasonId(homeTeamId, seasonId)).isEmpty();
  }
}
```

Note: `createMatch`/`transition`/`addEvent` here are private copies local to this test class (this module's own IT file, not shared with `MatchFlowIT` — same duplication convention as everywhere else in this codebase).

- [ ] **Step 7: Run the test to verify it fails**

Run: `cd backend/api && ./mvnw test -Dtest=StatisticsFlowIT`
Expected: FAIL — `finishingAMatchPersistsPlayerAndTeamStatistics` fails because `Match.finish()` doesn't publish any event yet, so no rows exist. `aMatchThatNeverFinishesGeneratesNoStatistics` may pass vacuously (nothing to find either way) — that's fine, the first test is the one carrying the signal.

- [ ] **Step 8: Publish `MatchFinishedEvent` from `MatchService.finish()`**

Replace the full contents of `backend/api/src/main/java/com/sporya/match/application/MatchService.java`:
```java
package com.sporya.match.application;

import com.sporya.auth.domain.Role;
import com.sporya.auth.infrastructure.security.AuthenticatedUser;
import com.sporya.club.application.TeamService;
import com.sporya.match.controller.dto.CreateMatchRequest;
import com.sporya.match.controller.dto.MatchResponse;
import com.sporya.match.controller.dto.RecentMatchResult;
import com.sporya.match.controller.dto.TeamFormResponse;
import com.sporya.match.domain.InvalidMatchStateException;
import com.sporya.match.domain.Match;
import com.sporya.match.domain.MatchAccessDeniedException;
import com.sporya.match.domain.MatchEventType;
import com.sporya.match.domain.MatchFinishedEvent;
import com.sporya.match.domain.MatchNotFoundException;
import com.sporya.match.domain.MatchResult;
import com.sporya.match.domain.MatchStatus;
import com.sporya.match.domain.SeasonNotFoundException;
import com.sporya.match.infrastructure.persistence.MatchEventRepository;
import com.sporya.match.infrastructure.persistence.MatchRepository;
import com.sporya.match.infrastructure.persistence.SeasonRepository;
import java.util.List;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MatchService {

  private final MatchRepository matchRepository;
  private final SeasonRepository seasonRepository;
  private final MatchEventRepository matchEventRepository;
  private final TeamService teamService;
  private final ApplicationEventPublisher eventPublisher;

  public MatchService(
      MatchRepository matchRepository,
      SeasonRepository seasonRepository,
      MatchEventRepository matchEventRepository,
      TeamService teamService,
      ApplicationEventPublisher eventPublisher) {
    this.matchRepository = matchRepository;
    this.seasonRepository = seasonRepository;
    this.matchEventRepository = matchEventRepository;
    this.teamService = teamService;
    this.eventPublisher = eventPublisher;
  }

  @Transactional
  public MatchResponse create(AuthenticatedUser caller, CreateMatchRequest request) {
    if (!seasonRepository.existsById(request.seasonId())) {
      throw new SeasonNotFoundException(request.seasonId());
    }
    UUID homeClubId = teamService.get(request.homeTeamId()).clubId();
    teamService.get(request.awayTeamId());
    if (!caller.hasAnyRole(homeClubId, Role.ADMIN, Role.COACH)) {
      throw new MatchAccessDeniedException("Not authorized to create a match for this home team");
    }
    Match match =
        new Match(
            request.seasonId(), request.homeTeamId(), request.awayTeamId(), request.kickoffAt());
    return toResponse(matchRepository.save(match));
  }

  @Transactional(readOnly = true)
  public List<MatchResponse> list() {
    return matchRepository.findAll().stream().map(this::toResponse).toList();
  }

  @Transactional(readOnly = true)
  public MatchResponse get(UUID matchId) {
    return toResponse(findMatch(matchId));
  }

  @Transactional
  public MatchResponse start(AuthenticatedUser caller, UUID matchId) {
    Match match = findMatch(matchId);
    requireHomeOrAwayStaff(caller, match);
    if (match.getStatus() != MatchStatus.SCHEDULED) {
      throw new InvalidMatchStateException(
          "Match must be SCHEDULED to start, was: " + match.getStatus());
    }
    match.setStatus(MatchStatus.LIVE);
    return toResponse(matchRepository.save(match));
  }

  @Transactional
  public MatchResponse halfTime(AuthenticatedUser caller, UUID matchId) {
    Match match = findMatch(matchId);
    requireHomeOrAwayStaff(caller, match);
    if (match.getStatus() != MatchStatus.LIVE) {
      throw new InvalidMatchStateException(
          "Match must be LIVE to go to half-time, was: " + match.getStatus());
    }
    match.setStatus(MatchStatus.HALF_TIME);
    return toResponse(matchRepository.save(match));
  }

  @Transactional
  public MatchResponse resume(AuthenticatedUser caller, UUID matchId) {
    Match match = findMatch(matchId);
    requireHomeOrAwayStaff(caller, match);
    if (match.getStatus() != MatchStatus.HALF_TIME) {
      throw new InvalidMatchStateException(
          "Match must be HALF_TIME to resume, was: " + match.getStatus());
    }
    match.setStatus(MatchStatus.LIVE);
    return toResponse(matchRepository.save(match));
  }

  @Transactional
  public MatchResponse finish(AuthenticatedUser caller, UUID matchId) {
    Match match = findMatch(matchId);
    requireHomeOrAwayStaff(caller, match);
    if (match.getStatus() != MatchStatus.LIVE) {
      throw new InvalidMatchStateException(
          "Match must be LIVE to finish, was: " + match.getStatus());
    }
    match.setStatus(MatchStatus.FINISHED);
    Match saved = matchRepository.save(match);
    eventPublisher.publishEvent(
        new MatchFinishedEvent(
            saved.getId(), saved.getHomeTeamId(), saved.getAwayTeamId(), saved.getSeasonId()));
    return toResponse(saved);
  }

  Match findMatch(UUID matchId) {
    return matchRepository.findById(matchId).orElseThrow(() -> new MatchNotFoundException(matchId));
  }

  void requireHomeOrAwayStaff(AuthenticatedUser caller, Match match) {
    UUID homeClubId = teamService.get(match.getHomeTeamId()).clubId();
    UUID awayClubId = teamService.get(match.getAwayTeamId()).clubId();
    boolean authorized =
        caller.hasAnyRole(homeClubId, Role.ADMIN, Role.COACH)
            || caller.hasAnyRole(awayClubId, Role.ADMIN, Role.COACH);
    if (!authorized) {
      throw new MatchAccessDeniedException("Not authorized to manage this match");
    }
  }

  @Transactional(readOnly = true)
  public TeamFormResponse recentForm(UUID teamId) {
    List<Match> matches =
        matchRepository.findRecentByTeamAndStatus(
            teamId, MatchStatus.FINISHED, PageRequest.of(0, 5));
    List<RecentMatchResult> results =
        matches.stream().map(match -> toRecentResult(teamId, match)).toList();
    return new TeamFormResponse(teamId, results);
  }

  private RecentMatchResult toRecentResult(UUID teamId, Match match) {
    int homeScore =
        (int)
            matchEventRepository.countByMatchIdAndTeamIdAndType(
                match.getId(), match.getHomeTeamId(), MatchEventType.GOAL_SCORED);
    int awayScore =
        (int)
            matchEventRepository.countByMatchIdAndTeamIdAndType(
                match.getId(), match.getAwayTeamId(), MatchEventType.GOAL_SCORED);
    UUID opponentTeamId =
        match.getHomeTeamId().equals(teamId) ? match.getAwayTeamId() : match.getHomeTeamId();
    MatchResult result;
    if (homeScore == awayScore) {
      result = MatchResult.DRAW;
    } else {
      boolean teamIsHome = match.getHomeTeamId().equals(teamId);
      boolean homeWon = homeScore > awayScore;
      result = teamIsHome == homeWon ? MatchResult.WIN : MatchResult.LOSS;
    }
    return new RecentMatchResult(
        match.getId(), result, opponentTeamId, homeScore, awayScore, match.getKickoffAt());
  }

  private MatchResponse toResponse(Match match) {
    int homeScore =
        (int)
            matchEventRepository.countByMatchIdAndTeamIdAndType(
                match.getId(), match.getHomeTeamId(), MatchEventType.GOAL_SCORED);
    int awayScore =
        (int)
            matchEventRepository.countByMatchIdAndTeamIdAndType(
                match.getId(), match.getAwayTeamId(), MatchEventType.GOAL_SCORED);
    return MatchResponse.from(match, homeScore, awayScore);
  }
}
```

- [ ] **Step 9: Implement `MatchFinishedListener`**

`backend/api/src/main/java/com/sporya/statistics/application/MatchFinishedListener.java`:
```java
package com.sporya.statistics.application;

import com.sporya.match.application.MatchEventService;
import com.sporya.match.controller.dto.MatchEventResponse;
import com.sporya.match.domain.MatchEventType;
import com.sporya.match.domain.MatchFinishedEvent;
import com.sporya.statistics.domain.MatchOutcome;
import com.sporya.statistics.domain.PlayerMatchStatistics;
import com.sporya.statistics.domain.TeamMatchStatistics;
import com.sporya.statistics.infrastructure.persistence.PlayerMatchStatisticsRepository;
import com.sporya.statistics.infrastructure.persistence.TeamMatchStatisticsRepository;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class MatchFinishedListener {

  private final MatchEventService matchEventService;
  private final PlayerMatchStatisticsRepository playerMatchStatisticsRepository;
  private final TeamMatchStatisticsRepository teamMatchStatisticsRepository;

  public MatchFinishedListener(
      MatchEventService matchEventService,
      PlayerMatchStatisticsRepository playerMatchStatisticsRepository,
      TeamMatchStatisticsRepository teamMatchStatisticsRepository) {
    this.matchEventService = matchEventService;
    this.playerMatchStatisticsRepository = playerMatchStatisticsRepository;
    this.teamMatchStatisticsRepository = teamMatchStatisticsRepository;
  }

  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void onMatchFinished(MatchFinishedEvent event) {
    List<MatchEventResponse> events = matchEventService.listForMatch(event.matchId());

    int homeGoals = countGoalsForTeam(events, event.homeTeamId());
    int awayGoals = countGoalsForTeam(events, event.awayTeamId());

    teamMatchStatisticsRepository.save(
        new TeamMatchStatistics(
            event.homeTeamId(),
            event.matchId(),
            event.seasonId(),
            homeGoals,
            awayGoals,
            outcomeFor(homeGoals, awayGoals)));
    teamMatchStatisticsRepository.save(
        new TeamMatchStatistics(
            event.awayTeamId(),
            event.matchId(),
            event.seasonId(),
            awayGoals,
            homeGoals,
            outcomeFor(awayGoals, homeGoals)));

    Map<UUID, List<MatchEventResponse>> eventsByPlayer =
        events.stream().collect(Collectors.groupingBy(MatchEventResponse::playerId));
    eventsByPlayer.forEach(
        (playerId, playerEvents) -> {
          UUID teamId = playerEvents.get(0).teamId();
          int goals = countByType(playerEvents, MatchEventType.GOAL_SCORED);
          int yellowCards = countByType(playerEvents, MatchEventType.YELLOW_CARD);
          int redCards = countByType(playerEvents, MatchEventType.RED_CARD);
          playerMatchStatisticsRepository.save(
              new PlayerMatchStatistics(
                  playerId, event.matchId(), teamId, event.seasonId(), goals, yellowCards, redCards));
        });
  }

  private static int countGoalsForTeam(List<MatchEventResponse> events, UUID teamId) {
    return (int)
        events.stream()
            .filter(e -> e.type() == MatchEventType.GOAL_SCORED && e.teamId().equals(teamId))
            .count();
  }

  private static int countByType(List<MatchEventResponse> events, MatchEventType type) {
    return (int) events.stream().filter(e -> e.type() == type).count();
  }

  private static MatchOutcome outcomeFor(int goalsFor, int goalsAgainst) {
    if (goalsFor == goalsAgainst) {
      return MatchOutcome.DRAW;
    }
    return goalsFor > goalsAgainst ? MatchOutcome.WIN : MatchOutcome.LOSS;
  }
}
```

- [ ] **Step 10: Run the test to verify it passes**

Run: `cd backend/api && ./mvnw test -Dtest=StatisticsFlowIT`
Expected: PASS (2 tests).

- [ ] **Step 11: Run the full backend test suite**

Run: `cd backend/api && ./mvnw verify`
Expected: PASS — every existing test still green, including `MatchFlowIT` (the `MatchService` constructor change is additive, Spring wires `ApplicationEventPublisher` automatically, no other caller needs updating).

- [ ] **Step 12: Commit**

```bash
git add backend/api/src/main/resources/db/migration/V5__create_statistics_tables.sql \
        backend/api/src/main/resources/application.yml \
        backend/api/src/main/java/com/sporya/match/domain/MatchFinishedEvent.java \
        backend/api/src/main/java/com/sporya/match/application/MatchService.java \
        backend/api/src/main/java/com/sporya/statistics \
        backend/api/src/test/java/com/sporya/statistics
git commit -m "feat(statistics): persist PlayerMatchStatistics/TeamMatchStatistics on match finish"
```

---

## Task 2: Player season statistics endpoint

**Files:**
- Create: `backend/api/src/main/java/com/sporya/statistics/controller/dto/PlayerSeasonStatisticsResponse.java`
- Create: `backend/api/src/main/java/com/sporya/statistics/application/PlayerStatisticsService.java`
- Create: `backend/api/src/main/java/com/sporya/statistics/controller/PlayerStatisticsController.java`
- Modify: `backend/api/src/test/java/com/sporya/statistics/StatisticsFlowIT.java`

**Interfaces:**
- Consumes: `PlayerMatchStatisticsRepository.findByPlayerIdAndSeasonId` (Task 1).
- Produces: `PlayerSeasonStatisticsResponse(UUID playerId, UUID seasonId, int goals, int yellowCards, int redCards, int matchesPlayed)`, route `GET /api/v1/players/{playerId}/seasons/{seasonId}/statistics`. Nothing later in this plan depends on this task.

- [ ] **Step 1: Write the DTO**

`backend/api/src/main/java/com/sporya/statistics/controller/dto/PlayerSeasonStatisticsResponse.java`:
```java
package com.sporya.statistics.controller.dto;

import java.util.UUID;

public record PlayerSeasonStatisticsResponse(
    UUID playerId, UUID seasonId, int goals, int yellowCards, int redCards, int matchesPlayed) {}
```

- [ ] **Step 2: Extend `StatisticsFlowIT` with a failing test**

Add these imports to `backend/api/src/test/java/com/sporya/statistics/StatisticsFlowIT.java`:
```java
import com.sporya.statistics.controller.dto.PlayerSeasonStatisticsResponse;
```
Add this test method after `aMatchThatNeverFinishesGeneratesNoStatistics`:
```java
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
    String accessToken = register("staff+" + System.nanoTime() + "@sporya.test", "correct-horse-battery");
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
```

- [ ] **Step 3: Run the test to verify it fails**

Run: `cd backend/api && ./mvnw test -Dtest=StatisticsFlowIT`
Expected: FAIL — 401/404, no route mapped yet for `/api/v1/players/{playerId}/seasons/{seasonId}/statistics`.

- [ ] **Step 4: Implement `PlayerStatisticsService`**

`backend/api/src/main/java/com/sporya/statistics/application/PlayerStatisticsService.java`:
```java
package com.sporya.statistics.application;

import com.sporya.statistics.controller.dto.PlayerSeasonStatisticsResponse;
import com.sporya.statistics.domain.PlayerMatchStatistics;
import com.sporya.statistics.infrastructure.persistence.PlayerMatchStatisticsRepository;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PlayerStatisticsService {

  private final PlayerMatchStatisticsRepository playerMatchStatisticsRepository;

  public PlayerStatisticsService(PlayerMatchStatisticsRepository playerMatchStatisticsRepository) {
    this.playerMatchStatisticsRepository = playerMatchStatisticsRepository;
  }

  @Transactional(readOnly = true)
  public PlayerSeasonStatisticsResponse seasonStatsFor(UUID playerId, UUID seasonId) {
    List<PlayerMatchStatistics> rows =
        playerMatchStatisticsRepository.findByPlayerIdAndSeasonId(playerId, seasonId);
    int goals = rows.stream().mapToInt(PlayerMatchStatistics::getGoals).sum();
    int yellowCards = rows.stream().mapToInt(PlayerMatchStatistics::getYellowCards).sum();
    int redCards = rows.stream().mapToInt(PlayerMatchStatistics::getRedCards).sum();
    return new PlayerSeasonStatisticsResponse(
        playerId, seasonId, goals, yellowCards, redCards, rows.size());
  }
}
```

- [ ] **Step 5: Implement `PlayerStatisticsController`**

`backend/api/src/main/java/com/sporya/statistics/controller/PlayerStatisticsController.java`:
```java
package com.sporya.statistics.controller;

import com.sporya.statistics.application.PlayerStatisticsService;
import com.sporya.statistics.controller.dto.PlayerSeasonStatisticsResponse;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/players/{playerId}/seasons/{seasonId}/statistics")
public class PlayerStatisticsController {

  private final PlayerStatisticsService playerStatisticsService;

  public PlayerStatisticsController(PlayerStatisticsService playerStatisticsService) {
    this.playerStatisticsService = playerStatisticsService;
  }

  @GetMapping
  public PlayerSeasonStatisticsResponse stats(
      @PathVariable UUID playerId, @PathVariable UUID seasonId) {
    return playerStatisticsService.seasonStatsFor(playerId, seasonId);
  }
}
```

- [ ] **Step 6: Run the test to verify it passes**

Run: `cd backend/api && ./mvnw test -Dtest=StatisticsFlowIT`
Expected: PASS (4 tests).

- [ ] **Step 7: Commit**

```bash
git add backend/api/src/main/java/com/sporya/statistics backend/api/src/test/java/com/sporya/statistics
git commit -m "feat(statistics): add player season statistics endpoint"
```

---

## Task 3: Team season statistics endpoint

**Files:**
- Create: `backend/api/src/main/java/com/sporya/statistics/controller/dto/TeamSeasonStatisticsResponse.java`
- Create: `backend/api/src/main/java/com/sporya/statistics/application/TeamStatisticsService.java`
- Create: `backend/api/src/main/java/com/sporya/statistics/controller/TeamStatisticsController.java`
- Modify: `backend/api/src/test/java/com/sporya/statistics/StatisticsFlowIT.java`

**Interfaces:**
- Consumes: `TeamMatchStatisticsRepository.findByTeamIdAndSeasonId`, `MatchOutcome` (Task 1).
- Produces: `TeamSeasonStatisticsResponse(UUID teamId, UUID seasonId, int wins, int draws, int losses, int goalsFor, int goalsAgainst)`, route `GET /api/v1/teams/{teamId}/seasons/{seasonId}/statistics`. Last task in this plan.

- [ ] **Step 1: Write the DTO**

`backend/api/src/main/java/com/sporya/statistics/controller/dto/TeamSeasonStatisticsResponse.java`:
```java
package com.sporya.statistics.controller.dto;

import java.util.UUID;

public record TeamSeasonStatisticsResponse(
    UUID teamId, UUID seasonId, int wins, int draws, int losses, int goalsFor, int goalsAgainst) {}
```

- [ ] **Step 2: Extend `StatisticsFlowIT` with failing tests**

Add this import to `StatisticsFlowIT`:
```java
import com.sporya.statistics.controller.dto.TeamSeasonStatisticsResponse;
```
Add these test methods after `playerSeasonStatisticsForUnknownPlayerReturnsZeros`:
```java
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
```

- [ ] **Step 3: Run the test to verify it fails**

Run: `cd backend/api && ./mvnw test -Dtest=StatisticsFlowIT`
Expected: FAIL — 401/404, no route mapped yet for `/api/v1/teams/{teamId}/seasons/{seasonId}/statistics`.

- [ ] **Step 4: Implement `TeamStatisticsService`**

`backend/api/src/main/java/com/sporya/statistics/application/TeamStatisticsService.java`:
```java
package com.sporya.statistics.application;

import com.sporya.statistics.controller.dto.TeamSeasonStatisticsResponse;
import com.sporya.statistics.domain.MatchOutcome;
import com.sporya.statistics.domain.TeamMatchStatistics;
import com.sporya.statistics.infrastructure.persistence.TeamMatchStatisticsRepository;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TeamStatisticsService {

  private final TeamMatchStatisticsRepository teamMatchStatisticsRepository;

  public TeamStatisticsService(TeamMatchStatisticsRepository teamMatchStatisticsRepository) {
    this.teamMatchStatisticsRepository = teamMatchStatisticsRepository;
  }

  @Transactional(readOnly = true)
  public TeamSeasonStatisticsResponse seasonStatsFor(UUID teamId, UUID seasonId) {
    List<TeamMatchStatistics> rows =
        teamMatchStatisticsRepository.findByTeamIdAndSeasonId(teamId, seasonId);
    int wins = (int) rows.stream().filter(row -> row.getResult() == MatchOutcome.WIN).count();
    int draws = (int) rows.stream().filter(row -> row.getResult() == MatchOutcome.DRAW).count();
    int losses = (int) rows.stream().filter(row -> row.getResult() == MatchOutcome.LOSS).count();
    int goalsFor = rows.stream().mapToInt(TeamMatchStatistics::getGoalsFor).sum();
    int goalsAgainst = rows.stream().mapToInt(TeamMatchStatistics::getGoalsAgainst).sum();
    return new TeamSeasonStatisticsResponse(teamId, seasonId, wins, draws, losses, goalsFor, goalsAgainst);
  }
}
```

- [ ] **Step 5: Implement `TeamStatisticsController`**

`backend/api/src/main/java/com/sporya/statistics/controller/TeamStatisticsController.java`:
```java
package com.sporya.statistics.controller;

import com.sporya.statistics.application.TeamStatisticsService;
import com.sporya.statistics.controller.dto.TeamSeasonStatisticsResponse;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/teams/{teamId}/seasons/{seasonId}/statistics")
public class TeamStatisticsController {

  private final TeamStatisticsService teamStatisticsService;

  public TeamStatisticsController(TeamStatisticsService teamStatisticsService) {
    this.teamStatisticsService = teamStatisticsService;
  }

  @GetMapping
  public TeamSeasonStatisticsResponse stats(
      @PathVariable UUID teamId, @PathVariable UUID seasonId) {
    return teamStatisticsService.seasonStatsFor(teamId, seasonId);
  }
}
```

- [ ] **Step 6: Run the test to verify it passes**

Run: `cd backend/api && ./mvnw test -Dtest=StatisticsFlowIT`
Expected: PASS (6 tests).

- [ ] **Step 7: Run the full backend test suite**

Run: `cd backend/api && ./mvnw verify`
Expected: PASS — every test green, spotless clean.

- [ ] **Step 8: Commit**

```bash
git add backend/api/src/main/java/com/sporya/statistics backend/api/src/test/java/com/sporya/statistics
git commit -m "feat(statistics): add team season statistics endpoint"
```
