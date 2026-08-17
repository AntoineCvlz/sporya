# Match Module Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build the Match module (`com.sporya.match`) — Competition/Season reference data, Match creation and its state machine (`SCHEDULED → LIVE → HALF_TIME → LIVE → FINISHED`), MatchEvent (goal/card/substitution) with roster validation and the red-card rule, and a score derived from events — plus the minimal frontend to use it. Module #3 in the build order, depends on Club and on the ClubMembership RBAC already shipped.

**Architecture:** Same layered structure as `com.sporya.auth`/`com.sporya.club` (`controller/application/domain/infrastructure`), new Postgres schema `match`. Match calls Club via its public application services (`TeamService`, `PlayerService`) — never Club's repositories directly — preserving the one-directional dependency (Match → Club). Built as five vertical backend slices (Competition/Season → Match CRUD → transitions → events → red card + score), each proven by extending one growing integration test (`MatchFlowIT`), mirroring `ClubFlowIT`. Frontend adds three pages reusing existing shadcn components and TanStack Query, consistent with `ClubsPage`/`ClubDetailPage`.

**Tech Stack:** Spring Boot 3.5 / Java 21 (existing `backend/api`), Spring Data JPA, Flyway, Testcontainers + `TestRestTemplate`, React 19 + TanStack Query + React Router (existing `frontend`).

**Spec:** `docs/superpowers/specs/2026-08-17-match-module-design.md`

## Global Constraints

- No `TeamSeasonRegistration` / player-transfer history — `homeTeamId`/`awayTeamId` reference `club.teams` directly and permanently, no season scoping on the team side. `Season` only groups matches.
- A match is created by an `ADMIN`/`COACH` of the **home** club only. Transitions and events are open to `ADMIN`/`COACH` of the home **or** away club.
- State transitions are dedicated endpoints (`/start`, `/half-time`, `/resume`, `/finish`), never `MatchEvent` records.
- Exactly 4 `MatchEvent` types in this pass: `GOAL_SCORED`, `YELLOW_CARD`, `RED_CARD`, `SUBSTITUTION`. No automatic two-yellows-to-red escalation.
- A `RED_CARD` blocks only future `GOAL_SCORED` for that player in that match — `YELLOW_CARD`/`SUBSTITUTION` remain accepted afterwards.
- Score is computed at read time from `MatchEvent` rows, never stored on `Match`. `team_id` is denormalized onto `MatchEvent` at write time to avoid a Club call on every read.
- `playerId` on an event is validated against the match roster (home or away team) via a Java call to Club.
- `Competition`/`Season`: create + list + get only, no update/delete.
- `Competition`/`Season` have no RBAC (shared reference data, like `GET /clubs` today) — only `Match`/`MatchEvent` are role-gated.
- Migration file: `backend/api/src/main/resources/db/migration/V4__create_match_tables.sql`, flat folder, unqualified table names inside the migration (Flyway's default schema stays `auth`; `match`-schema tables are qualified explicitly, same convention as `V2__create_club_tables.sql`).

---

## Task 1: Competition + Season (schema, entities, services, controllers)

**Files:**
- Create: `backend/api/src/main/resources/db/migration/V4__create_match_tables.sql`
- Modify: `backend/api/src/main/resources/application.yml`
- Create: `backend/api/src/main/java/com/sporya/match/domain/Competition.java`
- Create: `backend/api/src/main/java/com/sporya/match/domain/Season.java`
- Create: `backend/api/src/main/java/com/sporya/match/domain/CompetitionNotFoundException.java`
- Create: `backend/api/src/main/java/com/sporya/match/domain/SeasonNotFoundException.java`
- Create: `backend/api/src/main/java/com/sporya/match/infrastructure/persistence/CompetitionRepository.java`
- Create: `backend/api/src/main/java/com/sporya/match/infrastructure/persistence/SeasonRepository.java`
- Create: `backend/api/src/main/java/com/sporya/match/controller/dto/CompetitionResponse.java`
- Create: `backend/api/src/main/java/com/sporya/match/controller/dto/CreateCompetitionRequest.java`
- Create: `backend/api/src/main/java/com/sporya/match/controller/dto/SeasonResponse.java`
- Create: `backend/api/src/main/java/com/sporya/match/controller/dto/CreateSeasonRequest.java`
- Create: `backend/api/src/main/java/com/sporya/match/controller/dto/ErrorResponse.java`
- Create: `backend/api/src/main/java/com/sporya/match/controller/MatchApiExceptionHandler.java`
- Create: `backend/api/src/main/java/com/sporya/match/application/CompetitionService.java`
- Create: `backend/api/src/main/java/com/sporya/match/application/SeasonService.java`
- Create: `backend/api/src/main/java/com/sporya/match/controller/CompetitionController.java`
- Create: `backend/api/src/main/java/com/sporya/match/controller/SeasonController.java`
- Test: `backend/api/src/test/java/com/sporya/match/MatchFlowIT.java`

**Interfaces:**
- Consumes: nothing outside this task.
- Produces: `CompetitionRepository`/`SeasonRepository` (`existsById`), `CompetitionResponse(UUID id, String name, Instant createdAt)`, `SeasonResponse(UUID id, String label, UUID competitionId, Instant createdAt)`, routes `POST/GET /api/v1/competitions`, `POST/GET /api/v1/competitions/{competitionId}/seasons`, `GET /api/v1/seasons/{seasonId}`. Task 2 depends on `SeasonRepository.existsById` and `SeasonNotFoundException`.

- [ ] **Step 1: Create the migration for all four Match-module tables**

All four tables land together (FK-linked), even though `Match`/`MatchEvent` entities arrive in later tasks — same reasoning as `V2__create_club_tables.sql`.

`backend/api/src/main/resources/db/migration/V4__create_match_tables.sql`:

Note: unlike `V2__create_club_tables.sql`, this migration creates its schema explicitly (`CREATE SCHEMA IF NOT EXISTS match;`) instead of relying solely on `spring.flyway.create-schemas: true` — empirically, Flyway's `createSchemas` did not create the `match` schema before this migration ran (`ERROR: schema "match" does not exist` when creating `match.competitions`), even though it reliably created `club` for `V2`. Root cause not fully diagnosed; the explicit `CREATE SCHEMA IF NOT EXISTS` is a safe, idempotent fix.
```sql
CREATE SCHEMA IF NOT EXISTS match;

CREATE TABLE match.competitions (
    id UUID PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE match.seasons (
    id UUID PRIMARY KEY,
    label VARCHAR(255) NOT NULL,
    competition_id UUID NOT NULL REFERENCES match.competitions(id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE match.matches (
    id UUID PRIMARY KEY,
    season_id UUID NOT NULL REFERENCES match.seasons(id),
    home_team_id UUID NOT NULL,
    away_team_id UUID NOT NULL,
    status VARCHAR(20) NOT NULL,
    kickoff_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE match.match_events (
    id UUID PRIMARY KEY,
    match_id UUID NOT NULL REFERENCES match.matches(id),
    type VARCHAR(20) NOT NULL,
    minute INT NOT NULL,
    player_id UUID NOT NULL,
    team_id UUID NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
```

- [ ] **Step 2: Add the `match` schema to Flyway's managed schemas**

In `backend/api/src/main/resources/application.yml`, find:
```yaml
  flyway:
    schemas: auth,club
    default-schema: auth
    create-schemas: true
```
Replace with:
```yaml
  flyway:
    schemas: auth,club,match
    default-schema: auth
    create-schemas: true
```

- [ ] **Step 3: Write the Competition and Season domain entities + exceptions**

`backend/api/src/main/java/com/sporya/match/domain/Competition.java`:
```java
package com.sporya.match.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "competitions", schema = "match")
public class Competition {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(nullable = false)
  private String name;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  protected Competition() {}

  public Competition(String name) {
    this.name = name;
    this.createdAt = Instant.now();
  }

  public UUID getId() {
    return id;
  }

  public String getName() {
    return name;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }
}
```

`backend/api/src/main/java/com/sporya/match/domain/Season.java`:
```java
package com.sporya.match.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "seasons", schema = "match")
public class Season {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(nullable = false)
  private String label;

  @Column(name = "competition_id", nullable = false)
  private UUID competitionId;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  protected Season() {}

  public Season(String label, UUID competitionId) {
    this.label = label;
    this.competitionId = competitionId;
    this.createdAt = Instant.now();
  }

  public UUID getId() {
    return id;
  }

  public String getLabel() {
    return label;
  }

  public UUID getCompetitionId() {
    return competitionId;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }
}
```

`backend/api/src/main/java/com/sporya/match/domain/CompetitionNotFoundException.java`:
```java
package com.sporya.match.domain;

import java.util.UUID;

public class CompetitionNotFoundException extends RuntimeException {

  public CompetitionNotFoundException(UUID id) {
    super("Competition not found: " + id);
  }
}
```

`backend/api/src/main/java/com/sporya/match/domain/SeasonNotFoundException.java`:
```java
package com.sporya.match.domain;

import java.util.UUID;

public class SeasonNotFoundException extends RuntimeException {

  public SeasonNotFoundException(UUID id) {
    super("Season not found: " + id);
  }
}
```

- [ ] **Step 4: Write the repositories**

`backend/api/src/main/java/com/sporya/match/infrastructure/persistence/CompetitionRepository.java`:
```java
package com.sporya.match.infrastructure.persistence;

import com.sporya.match.domain.Competition;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CompetitionRepository extends JpaRepository<Competition, UUID> {}
```

`backend/api/src/main/java/com/sporya/match/infrastructure/persistence/SeasonRepository.java`:
```java
package com.sporya.match.infrastructure.persistence;

import com.sporya.match.domain.Season;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SeasonRepository extends JpaRepository<Season, UUID> {

  List<Season> findByCompetitionId(UUID competitionId);
}
```

- [ ] **Step 5: Write the DTOs and exception handler**

`backend/api/src/main/java/com/sporya/match/controller/dto/CompetitionResponse.java`:
```java
package com.sporya.match.controller.dto;

import com.sporya.match.domain.Competition;
import java.time.Instant;
import java.util.UUID;

public record CompetitionResponse(UUID id, String name, Instant createdAt) {

  public static CompetitionResponse from(Competition competition) {
    return new CompetitionResponse(competition.getId(), competition.getName(), competition.getCreatedAt());
  }
}
```

`backend/api/src/main/java/com/sporya/match/controller/dto/CreateCompetitionRequest.java`:
```java
package com.sporya.match.controller.dto;

import jakarta.validation.constraints.NotBlank;

public record CreateCompetitionRequest(@NotBlank String name) {}
```

`backend/api/src/main/java/com/sporya/match/controller/dto/SeasonResponse.java`:
```java
package com.sporya.match.controller.dto;

import com.sporya.match.domain.Season;
import java.time.Instant;
import java.util.UUID;

public record SeasonResponse(UUID id, String label, UUID competitionId, Instant createdAt) {

  public static SeasonResponse from(Season season) {
    return new SeasonResponse(
        season.getId(), season.getLabel(), season.getCompetitionId(), season.getCreatedAt());
  }
}
```

`backend/api/src/main/java/com/sporya/match/controller/dto/CreateSeasonRequest.java`:
```java
package com.sporya.match.controller.dto;

import jakarta.validation.constraints.NotBlank;

public record CreateSeasonRequest(@NotBlank String label) {}
```

`backend/api/src/main/java/com/sporya/match/controller/dto/ErrorResponse.java`:
```java
package com.sporya.match.controller.dto;

public record ErrorResponse(String message) {}
```

`backend/api/src/main/java/com/sporya/match/controller/MatchApiExceptionHandler.java`:
```java
package com.sporya.match.controller;

import com.sporya.match.controller.dto.ErrorResponse;
import com.sporya.match.domain.CompetitionNotFoundException;
import com.sporya.match.domain.SeasonNotFoundException;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
class MatchApiExceptionHandler {

  @ExceptionHandler(CompetitionNotFoundException.class)
  ResponseEntity<ErrorResponse> handleCompetitionNotFound(CompetitionNotFoundException ex) {
    return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ErrorResponse(ex.getMessage()));
  }

  @ExceptionHandler(SeasonNotFoundException.class)
  ResponseEntity<ErrorResponse> handleSeasonNotFound(SeasonNotFoundException ex) {
    return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ErrorResponse(ex.getMessage()));
  }

  @ExceptionHandler(MethodArgumentNotValidException.class)
  ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException ex) {
    String message =
        ex.getBindingResult().getFieldErrors().stream()
            .map(error -> error.getField() + ": " + error.getDefaultMessage())
            .collect(Collectors.joining(", "));
    return ResponseEntity.badRequest().body(new ErrorResponse(message));
  }
}
```

- [ ] **Step 6: Write the failing test**

`backend/api/src/test/java/com/sporya/match/MatchFlowIT.java`:
```java
package com.sporya.match;

import static org.assertj.core.api.Assertions.assertThat;

import com.sporya.auth.controller.dto.AuthResponse;
import com.sporya.auth.controller.dto.LoginRequest;
import com.sporya.auth.controller.dto.RegisterRequest;
import com.sporya.match.controller.dto.CompetitionResponse;
import com.sporya.match.controller.dto.CreateCompetitionRequest;
import com.sporya.match.controller.dto.CreateSeasonRequest;
import com.sporya.match.controller.dto.SeasonResponse;
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
}
```

Note: `createCompetition`/`createSeason` are used starting in this task, `register`/`login`/`authHeaders` are added now so Task 2+ don't have to touch them.

- [ ] **Step 7: Run the test to verify it fails**

Run: `cd backend/api && ./mvnw test -Dtest=MatchFlowIT`
Expected: FAIL — compilation error, `CompetitionService`/`CompetitionController`/`SeasonService`/`SeasonController` don't exist yet.

- [ ] **Step 8: Implement `CompetitionService` and `SeasonService`**

`backend/api/src/main/java/com/sporya/match/application/CompetitionService.java`:
```java
package com.sporya.match.application;

import com.sporya.match.controller.dto.CompetitionResponse;
import com.sporya.match.controller.dto.CreateCompetitionRequest;
import com.sporya.match.domain.Competition;
import com.sporya.match.domain.CompetitionNotFoundException;
import com.sporya.match.infrastructure.persistence.CompetitionRepository;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CompetitionService {

  private final CompetitionRepository competitionRepository;

  public CompetitionService(CompetitionRepository competitionRepository) {
    this.competitionRepository = competitionRepository;
  }

  @Transactional
  public CompetitionResponse create(CreateCompetitionRequest request) {
    Competition competition = new Competition(request.name());
    return CompetitionResponse.from(competitionRepository.save(competition));
  }

  @Transactional(readOnly = true)
  public List<CompetitionResponse> list() {
    return competitionRepository.findAll().stream().map(CompetitionResponse::from).toList();
  }

  @Transactional(readOnly = true)
  public CompetitionResponse get(UUID competitionId) {
    Competition competition =
        competitionRepository
            .findById(competitionId)
            .orElseThrow(() -> new CompetitionNotFoundException(competitionId));
    return CompetitionResponse.from(competition);
  }
}
```

`backend/api/src/main/java/com/sporya/match/application/SeasonService.java`:
```java
package com.sporya.match.application;

import com.sporya.match.controller.dto.CreateSeasonRequest;
import com.sporya.match.controller.dto.SeasonResponse;
import com.sporya.match.domain.CompetitionNotFoundException;
import com.sporya.match.domain.Season;
import com.sporya.match.domain.SeasonNotFoundException;
import com.sporya.match.infrastructure.persistence.CompetitionRepository;
import com.sporya.match.infrastructure.persistence.SeasonRepository;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SeasonService {

  private final SeasonRepository seasonRepository;
  private final CompetitionRepository competitionRepository;

  public SeasonService(SeasonRepository seasonRepository, CompetitionRepository competitionRepository) {
    this.seasonRepository = seasonRepository;
    this.competitionRepository = competitionRepository;
  }

  @Transactional
  public SeasonResponse create(UUID competitionId, CreateSeasonRequest request) {
    if (!competitionRepository.existsById(competitionId)) {
      throw new CompetitionNotFoundException(competitionId);
    }
    Season season = new Season(request.label(), competitionId);
    return SeasonResponse.from(seasonRepository.save(season));
  }

  @Transactional(readOnly = true)
  public List<SeasonResponse> listByCompetition(UUID competitionId) {
    if (!competitionRepository.existsById(competitionId)) {
      throw new CompetitionNotFoundException(competitionId);
    }
    return seasonRepository.findByCompetitionId(competitionId).stream()
        .map(SeasonResponse::from)
        .toList();
  }

  @Transactional(readOnly = true)
  public SeasonResponse get(UUID seasonId) {
    Season season =
        seasonRepository.findById(seasonId).orElseThrow(() -> new SeasonNotFoundException(seasonId));
    return SeasonResponse.from(season);
  }
}
```

- [ ] **Step 9: Implement `CompetitionController` and `SeasonController`**

`backend/api/src/main/java/com/sporya/match/controller/CompetitionController.java`:
```java
package com.sporya.match.controller;

import com.sporya.match.application.CompetitionService;
import com.sporya.match.controller.dto.CompetitionResponse;
import com.sporya.match.controller.dto.CreateCompetitionRequest;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/competitions")
public class CompetitionController {

  private final CompetitionService competitionService;

  public CompetitionController(CompetitionService competitionService) {
    this.competitionService = competitionService;
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  public CompetitionResponse create(@Valid @RequestBody CreateCompetitionRequest request) {
    return competitionService.create(request);
  }

  @GetMapping
  public List<CompetitionResponse> list() {
    return competitionService.list();
  }

  @GetMapping("/{competitionId}")
  public CompetitionResponse get(@PathVariable UUID competitionId) {
    return competitionService.get(competitionId);
  }
}
```

`backend/api/src/main/java/com/sporya/match/controller/SeasonController.java`:
```java
package com.sporya.match.controller;

import com.sporya.match.application.SeasonService;
import com.sporya.match.controller.dto.CreateSeasonRequest;
import com.sporya.match.controller.dto.SeasonResponse;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class SeasonController {

  private final SeasonService seasonService;

  public SeasonController(SeasonService seasonService) {
    this.seasonService = seasonService;
  }

  @PostMapping("/api/v1/competitions/{competitionId}/seasons")
  @ResponseStatus(HttpStatus.CREATED)
  public SeasonResponse create(
      @PathVariable UUID competitionId, @Valid @RequestBody CreateSeasonRequest request) {
    return seasonService.create(competitionId, request);
  }

  @GetMapping("/api/v1/competitions/{competitionId}/seasons")
  public List<SeasonResponse> listByCompetition(@PathVariable UUID competitionId) {
    return seasonService.listByCompetition(competitionId);
  }

  @GetMapping("/api/v1/seasons/{seasonId}")
  public SeasonResponse get(@PathVariable UUID seasonId) {
    return seasonService.get(seasonId);
  }
}
```

- [ ] **Step 10: Run the test to verify it passes**

Run: `cd backend/api && ./mvnw test -Dtest=MatchFlowIT`
Expected: PASS (2 tests).

- [ ] **Step 11: Commit**

```bash
git add backend/api/src/main/resources/db/migration/V4__create_match_tables.sql \
        backend/api/src/main/resources/application.yml \
        backend/api/src/main/java/com/sporya/match \
        backend/api/src/test/java/com/sporya/match
git commit -m "feat(match): add Competition/Season entities, create/list/get endpoints"
```

---

## Task 2: Match creation, get, list

**Files:**
- Create: `backend/api/src/main/java/com/sporya/match/domain/MatchStatus.java`
- Create: `backend/api/src/main/java/com/sporya/match/domain/Match.java`
- Create: `backend/api/src/main/java/com/sporya/match/domain/MatchNotFoundException.java`
- Create: `backend/api/src/main/java/com/sporya/match/domain/MatchAccessDeniedException.java`
- Create: `backend/api/src/main/java/com/sporya/match/infrastructure/persistence/MatchRepository.java`
- Create: `backend/api/src/main/java/com/sporya/match/controller/dto/MatchResponse.java`
- Create: `backend/api/src/main/java/com/sporya/match/controller/dto/CreateMatchRequest.java`
- Modify: `backend/api/src/main/java/com/sporya/match/controller/MatchApiExceptionHandler.java`
- Create: `backend/api/src/main/java/com/sporya/match/application/MatchService.java`
- Create: `backend/api/src/main/java/com/sporya/match/controller/MatchController.java`
- Modify: `backend/api/src/test/java/com/sporya/match/MatchFlowIT.java`

**Interfaces:**
- Consumes: `SeasonRepository.existsById`, `SeasonNotFoundException` (Task 1); `com.sporya.club.application.TeamService.get(UUID): TeamResponse` (existing, throws `TeamNotFoundException` — already handled globally by `ClubApiExceptionHandler`); `com.sporya.auth.infrastructure.security.AuthenticatedUser.hasAnyRole` (existing).
- Produces: `MatchStatus` enum (`SCHEDULED, LIVE, HALF_TIME, FINISHED`), `Match` entity (`getId/getSeasonId/getHomeTeamId/getAwayTeamId/getStatus/setStatus/getKickoffAt/getCreatedAt`), `MatchRepository`, `MatchResponse(UUID id, UUID seasonId, UUID homeTeamId, UUID awayTeamId, MatchStatus status, Instant kickoffAt, Instant createdAt)`, `MatchService.create/get/list`, routes `POST/GET /api/v1/matches`, `GET /api/v1/matches/{id}`. Task 3 extends `MatchService`/`MatchController`/`MatchFlowIT` and depends on the `Match` entity's `setStatus`. **Task 5 changes `MatchResponse`'s signature** (adds `homeScore`/`awayScore`) — every task in between must call `MatchResponse.from(match)` exactly as defined here so Task 5's rewrite has a single consistent shape to replace.

- [ ] **Step 1: Write the `MatchStatus` enum**

`backend/api/src/main/java/com/sporya/match/domain/MatchStatus.java`:
```java
package com.sporya.match.domain;

public enum MatchStatus {
  SCHEDULED,
  LIVE,
  HALF_TIME,
  FINISHED
}
```

- [ ] **Step 2: Write the `Match` entity and exceptions**

`backend/api/src/main/java/com/sporya/match/domain/Match.java`:
```java
package com.sporya.match.domain;

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
@Table(name = "matches", schema = "match")
public class Match {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(name = "season_id", nullable = false)
  private UUID seasonId;

  @Column(name = "home_team_id", nullable = false)
  private UUID homeTeamId;

  @Column(name = "away_team_id", nullable = false)
  private UUID awayTeamId;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private MatchStatus status;

  @Column(name = "kickoff_at", nullable = false)
  private Instant kickoffAt;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  protected Match() {}

  public Match(UUID seasonId, UUID homeTeamId, UUID awayTeamId, Instant kickoffAt) {
    this.seasonId = seasonId;
    this.homeTeamId = homeTeamId;
    this.awayTeamId = awayTeamId;
    this.status = MatchStatus.SCHEDULED;
    this.kickoffAt = kickoffAt;
    this.createdAt = Instant.now();
  }

  public UUID getId() {
    return id;
  }

  public UUID getSeasonId() {
    return seasonId;
  }

  public UUID getHomeTeamId() {
    return homeTeamId;
  }

  public UUID getAwayTeamId() {
    return awayTeamId;
  }

  public MatchStatus getStatus() {
    return status;
  }

  public void setStatus(MatchStatus status) {
    this.status = status;
  }

  public Instant getKickoffAt() {
    return kickoffAt;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }
}
```

`backend/api/src/main/java/com/sporya/match/domain/MatchNotFoundException.java`:
```java
package com.sporya.match.domain;

import java.util.UUID;

public class MatchNotFoundException extends RuntimeException {

  public MatchNotFoundException(UUID id) {
    super("Match not found: " + id);
  }
}
```

`backend/api/src/main/java/com/sporya/match/domain/MatchAccessDeniedException.java`:
```java
package com.sporya.match.domain;

public class MatchAccessDeniedException extends RuntimeException {

  public MatchAccessDeniedException(String message) {
    super(message);
  }
}
```

- [ ] **Step 3: Write `MatchRepository`**

`backend/api/src/main/java/com/sporya/match/infrastructure/persistence/MatchRepository.java`:
```java
package com.sporya.match.infrastructure.persistence;

import com.sporya.match.domain.Match;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MatchRepository extends JpaRepository<Match, UUID> {}
```

- [ ] **Step 4: Write the DTOs**

`backend/api/src/main/java/com/sporya/match/controller/dto/MatchResponse.java`:
```java
package com.sporya.match.controller.dto;

import com.sporya.match.domain.Match;
import com.sporya.match.domain.MatchStatus;
import java.time.Instant;
import java.util.UUID;

public record MatchResponse(
    UUID id,
    UUID seasonId,
    UUID homeTeamId,
    UUID awayTeamId,
    MatchStatus status,
    Instant kickoffAt,
    Instant createdAt) {

  public static MatchResponse from(Match match) {
    return new MatchResponse(
        match.getId(),
        match.getSeasonId(),
        match.getHomeTeamId(),
        match.getAwayTeamId(),
        match.getStatus(),
        match.getKickoffAt(),
        match.getCreatedAt());
  }
}
```

`backend/api/src/main/java/com/sporya/match/controller/dto/CreateMatchRequest.java`:
```java
package com.sporya.match.controller.dto;

import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.UUID;

public record CreateMatchRequest(
    @NotNull UUID seasonId, @NotNull UUID homeTeamId, @NotNull UUID awayTeamId, @NotNull Instant kickoffAt) {}
```

- [ ] **Step 5: Add the `Match` exception handlers**

In `backend/api/src/main/java/com/sporya/match/controller/MatchApiExceptionHandler.java`, add the imports `com.sporya.match.domain.MatchAccessDeniedException` and `com.sporya.match.domain.MatchNotFoundException` next to the other domain imports, and add these two handlers next to `handleSeasonNotFound`:
```java
  @ExceptionHandler(MatchNotFoundException.class)
  ResponseEntity<ErrorResponse> handleMatchNotFound(MatchNotFoundException ex) {
    return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ErrorResponse(ex.getMessage()));
  }

  @ExceptionHandler(MatchAccessDeniedException.class)
  ResponseEntity<ErrorResponse> handleMatchAccessDenied(MatchAccessDeniedException ex) {
    return ResponseEntity.status(HttpStatus.FORBIDDEN).body(new ErrorResponse(ex.getMessage()));
  }
```

- [ ] **Step 6: Extend `MatchFlowIT` with failing match tests**

Add these imports to `backend/api/src/test/java/com/sporya/match/MatchFlowIT.java`:
```java
import com.sporya.club.controller.dto.ClubResponse;
import com.sporya.club.controller.dto.CreateClubRequest;
import com.sporya.club.controller.dto.CreateTeamRequest;
import com.sporya.club.controller.dto.TeamResponse;
import com.sporya.match.controller.dto.CreateMatchRequest;
import com.sporya.match.controller.dto.MatchResponse;
import com.sporya.match.domain.MatchStatus;
import java.time.Instant;
```
Add these two helpers after `createSeason`:
```java
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
```
Add these two test methods after `createSeasonUnderUnknownCompetitionReturns404`:
```java
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
                new CreateMatchRequest(seasonId, homeTeamId, awayTeamId, Instant.now().plusSeconds(3600)),
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
    String accessToken = register("staff+" + System.nanoTime() + "@sporya.test", "correct-horse-battery");
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
                new CreateMatchRequest(seasonId, homeTeamId, awayTeamId, Instant.now().plusSeconds(3600)),
                authHeaders(accessToken)),
            Void.class);
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
  }
```

- [ ] **Step 7: Run the test to verify it fails**

Run: `cd backend/api && ./mvnw test -Dtest=MatchFlowIT`
Expected: FAIL — compilation error, `MatchService`/`MatchController` don't exist yet.

- [ ] **Step 8: Implement `MatchService`**

`backend/api/src/main/java/com/sporya/match/application/MatchService.java`:
```java
package com.sporya.match.application;

import com.sporya.auth.domain.Role;
import com.sporya.auth.infrastructure.security.AuthenticatedUser;
import com.sporya.club.application.TeamService;
import com.sporya.match.controller.dto.CreateMatchRequest;
import com.sporya.match.controller.dto.MatchResponse;
import com.sporya.match.domain.Match;
import com.sporya.match.domain.MatchAccessDeniedException;
import com.sporya.match.domain.MatchNotFoundException;
import com.sporya.match.domain.SeasonNotFoundException;
import com.sporya.match.infrastructure.persistence.MatchRepository;
import com.sporya.match.infrastructure.persistence.SeasonRepository;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MatchService {

  private final MatchRepository matchRepository;
  private final SeasonRepository seasonRepository;
  private final TeamService teamService;

  public MatchService(
      MatchRepository matchRepository, SeasonRepository seasonRepository, TeamService teamService) {
    this.matchRepository = matchRepository;
    this.seasonRepository = seasonRepository;
    this.teamService = teamService;
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
        new Match(request.seasonId(), request.homeTeamId(), request.awayTeamId(), request.kickoffAt());
    return MatchResponse.from(matchRepository.save(match));
  }

  @Transactional(readOnly = true)
  public List<MatchResponse> list() {
    return matchRepository.findAll().stream().map(MatchResponse::from).toList();
  }

  @Transactional(readOnly = true)
  public MatchResponse get(UUID matchId) {
    return MatchResponse.from(findMatch(matchId));
  }

  Match findMatch(UUID matchId) {
    return matchRepository.findById(matchId).orElseThrow(() -> new MatchNotFoundException(matchId));
  }
}
```

Note: `findMatch` is package-private (no modifier) rather than `private` — Task 3 adds transition methods to this same class that reuse it, and Task 5 adds a package-private `toResponse` helper too, so this stays an internal-to-the-package convenience, not a public API.

- [ ] **Step 9: Implement `MatchController`**

`backend/api/src/main/java/com/sporya/match/controller/MatchController.java`:
```java
package com.sporya.match.controller;

import com.sporya.auth.infrastructure.security.AuthenticatedUser;
import com.sporya.match.application.MatchService;
import com.sporya.match.controller.dto.CreateMatchRequest;
import com.sporya.match.controller.dto.MatchResponse;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/matches")
public class MatchController {

  private final MatchService matchService;

  public MatchController(MatchService matchService) {
    this.matchService = matchService;
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  public MatchResponse create(
      @AuthenticationPrincipal AuthenticatedUser caller, @Valid @RequestBody CreateMatchRequest request) {
    return matchService.create(caller, request);
  }

  @GetMapping
  public List<MatchResponse> list() {
    return matchService.list();
  }

  @GetMapping("/{matchId}")
  public MatchResponse get(@PathVariable UUID matchId) {
    return matchService.get(matchId);
  }
}
```

- [ ] **Step 10: Run the test to verify it passes**

Run: `cd backend/api && ./mvnw test -Dtest=MatchFlowIT`
Expected: PASS (4 tests).

- [ ] **Step 11: Commit**

```bash
git add backend/api/src/main/java/com/sporya/match backend/api/src/test/java/com/sporya/match
git commit -m "feat(match): add Match entity, create/get/list endpoints"
```

---

## Task 3: Match state transitions

**Files:**
- Create: `backend/api/src/main/java/com/sporya/match/domain/InvalidMatchStateException.java`
- Modify: `backend/api/src/main/java/com/sporya/match/controller/MatchApiExceptionHandler.java`
- Modify: `backend/api/src/main/java/com/sporya/match/application/MatchService.java`
- Modify: `backend/api/src/main/java/com/sporya/match/controller/MatchController.java`
- Modify: `backend/api/src/test/java/com/sporya/match/MatchFlowIT.java`

**Interfaces:**
- Consumes: `Match.getStatus/setStatus` (Task 2), `MatchService.findMatch` (Task 2).
- Produces: `MatchService.start/halfTime/resume/finish(AuthenticatedUser, UUID): MatchResponse`, `MatchService.requireHomeOrAwayStaff(AuthenticatedUser, Match)` (package-private, reused conceptually — not called cross-class — by Task 4's `MatchEventService`, which implements its own copy since it lives in a different class), routes `POST /api/v1/matches/{id}/start|half-time|resume|finish`. Task 4 depends on `InvalidMatchStateException` (reused for "event added while not LIVE").

- [ ] **Step 1: Write `InvalidMatchStateException`**

`backend/api/src/main/java/com/sporya/match/domain/InvalidMatchStateException.java`:
```java
package com.sporya.match.domain;

public class InvalidMatchStateException extends RuntimeException {

  public InvalidMatchStateException(String message) {
    super(message);
  }
}
```

- [ ] **Step 2: Add the `InvalidMatchStateException` handler**

In `backend/api/src/main/java/com/sporya/match/controller/MatchApiExceptionHandler.java`, add the import `com.sporya.match.domain.InvalidMatchStateException` and this handler next to `handleMatchAccessDenied`:
```java
  @ExceptionHandler(InvalidMatchStateException.class)
  ResponseEntity<ErrorResponse> handleInvalidMatchState(InvalidMatchStateException ex) {
    return ResponseEntity.status(HttpStatus.CONFLICT).body(new ErrorResponse(ex.getMessage()));
  }
```

- [ ] **Step 3: Extend `MatchFlowIT` with failing transition tests**

Add these helpers to `MatchFlowIT` after `createTeam`:
```java
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

  private ResponseEntity<MatchResponse> transition(String accessToken, UUID matchId, String transition) {
    return restTemplate.exchange(
        "/api/v1/matches/" + matchId + "/" + transition,
        HttpMethod.POST,
        new HttpEntity<>(authHeaders(accessToken)),
        MatchResponse.class);
  }
```
Add these two test methods after `createMatchWithoutHomeClubRoleReturns403`:
```java
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

    assertThat(transition(accessToken, matchId, "start").getBody().status()).isEqualTo(MatchStatus.LIVE);
    assertThat(transition(accessToken, matchId, "half-time").getBody().status())
        .isEqualTo(MatchStatus.HALF_TIME);
    assertThat(transition(accessToken, matchId, "resume").getBody().status()).isEqualTo(MatchStatus.LIVE);
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
```

- [ ] **Step 4: Run the test to verify it fails**

Run: `cd backend/api && ./mvnw test -Dtest=MatchFlowIT`
Expected: FAIL — 404, the transition routes don't exist yet.

- [ ] **Step 5: Add the transition methods to `MatchService`**

In `backend/api/src/main/java/com/sporya/match/application/MatchService.java`, add these methods after `create`:
```java
  @Transactional
  public MatchResponse start(AuthenticatedUser caller, UUID matchId) {
    Match match = findMatch(matchId);
    requireHomeOrAwayStaff(caller, match);
    if (match.getStatus() != MatchStatus.SCHEDULED) {
      throw new InvalidMatchStateException("Match must be SCHEDULED to start, was: " + match.getStatus());
    }
    match.setStatus(MatchStatus.LIVE);
    return MatchResponse.from(matchRepository.save(match));
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
    return MatchResponse.from(matchRepository.save(match));
  }

  @Transactional
  public MatchResponse resume(AuthenticatedUser caller, UUID matchId) {
    Match match = findMatch(matchId);
    requireHomeOrAwayStaff(caller, match);
    if (match.getStatus() != MatchStatus.HALF_TIME) {
      throw new InvalidMatchStateException("Match must be HALF_TIME to resume, was: " + match.getStatus());
    }
    match.setStatus(MatchStatus.LIVE);
    return MatchResponse.from(matchRepository.save(match));
  }

  @Transactional
  public MatchResponse finish(AuthenticatedUser caller, UUID matchId) {
    Match match = findMatch(matchId);
    requireHomeOrAwayStaff(caller, match);
    if (match.getStatus() != MatchStatus.LIVE) {
      throw new InvalidMatchStateException("Match must be LIVE to finish, was: " + match.getStatus());
    }
    match.setStatus(MatchStatus.FINISHED);
    return MatchResponse.from(matchRepository.save(match));
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
```
Add the imports `com.sporya.match.domain.InvalidMatchStateException` and `com.sporya.match.domain.MatchStatus` next to the other domain imports.

- [ ] **Step 6: Add the transition endpoints to `MatchController`**

In `backend/api/src/main/java/com/sporya/match/controller/MatchController.java`, add these methods after `create`:
```java
  @PostMapping("/{matchId}/start")
  public MatchResponse start(
      @AuthenticationPrincipal AuthenticatedUser caller, @PathVariable UUID matchId) {
    return matchService.start(caller, matchId);
  }

  @PostMapping("/{matchId}/half-time")
  public MatchResponse halfTime(
      @AuthenticationPrincipal AuthenticatedUser caller, @PathVariable UUID matchId) {
    return matchService.halfTime(caller, matchId);
  }

  @PostMapping("/{matchId}/resume")
  public MatchResponse resume(
      @AuthenticationPrincipal AuthenticatedUser caller, @PathVariable UUID matchId) {
    return matchService.resume(caller, matchId);
  }

  @PostMapping("/{matchId}/finish")
  public MatchResponse finish(
      @AuthenticationPrincipal AuthenticatedUser caller, @PathVariable UUID matchId) {
    return matchService.finish(caller, matchId);
  }
```

- [ ] **Step 7: Run the test to verify it passes**

Run: `cd backend/api && ./mvnw test -Dtest=MatchFlowIT`
Expected: PASS (6 tests).

- [ ] **Step 8: Commit**

```bash
git add backend/api/src/main/java/com/sporya/match backend/api/src/test/java/com/sporya/match
git commit -m "feat(match): add match state transitions (start/half-time/resume/finish)"
```

---

## Task 4: MatchEvent creation and listing

**Files:**
- Create: `backend/api/src/main/java/com/sporya/club/domain/PlayerNotFoundException.java`
- Modify: `backend/api/src/main/java/com/sporya/club/application/PlayerService.java`
- Modify: `backend/api/src/main/java/com/sporya/club/controller/ClubApiExceptionHandler.java`
- Create: `backend/api/src/main/java/com/sporya/match/domain/MatchEventType.java`
- Create: `backend/api/src/main/java/com/sporya/match/domain/MatchEvent.java`
- Create: `backend/api/src/main/java/com/sporya/match/domain/PlayerNotInMatchException.java`
- Create: `backend/api/src/main/java/com/sporya/match/infrastructure/persistence/MatchEventRepository.java`
- Create: `backend/api/src/main/java/com/sporya/match/controller/dto/MatchEventResponse.java`
- Create: `backend/api/src/main/java/com/sporya/match/controller/dto/CreateMatchEventRequest.java`
- Modify: `backend/api/src/main/java/com/sporya/match/controller/MatchApiExceptionHandler.java`
- Create: `backend/api/src/main/java/com/sporya/match/application/MatchEventService.java`
- Create: `backend/api/src/main/java/com/sporya/match/controller/MatchEventController.java`
- Modify: `backend/api/src/test/java/com/sporya/match/MatchFlowIT.java`

**Interfaces:**
- Consumes: `MatchService.findMatch`/`requireHomeOrAwayStaff` are NOT reused directly (different class — `MatchEventService` implements its own inline checks, duplication accepted per ADR-004 within the same module); `com.sporya.club.application.TeamService.get` (existing); new `com.sporya.club.application.PlayerService.get(UUID): PlayerResponse` (added in this task); `InvalidMatchStateException` (Task 3).
- Produces: `MatchEventType` enum, `MatchEvent` entity, `MatchEventRepository.findByMatchIdOrderByMinuteAsc`, `MatchEventResponse(UUID id, UUID matchId, MatchEventType type, int minute, UUID playerId, UUID teamId, Instant createdAt)`, `MatchEventService.add/listForMatch`, routes `POST/GET /api/v1/matches/{matchId}/events`. Task 5 extends `MatchEventRepository`/`MatchEventService` and depends on `MatchEventType.GOAL_SCORED`/`RED_CARD`.

- [ ] **Step 1: Add `PlayerService.get` to the Club module**

`backend/api/src/main/java/com/sporya/club/domain/PlayerNotFoundException.java`:
```java
package com.sporya.club.domain;

import java.util.UUID;

public class PlayerNotFoundException extends RuntimeException {

  public PlayerNotFoundException(UUID id) {
    super("Player not found: " + id);
  }
}
```

In `backend/api/src/main/java/com/sporya/club/application/PlayerService.java`, add the import `com.sporya.club.domain.PlayerNotFoundException` and this method after `listByTeam`:
```java
  @Transactional(readOnly = true)
  public PlayerResponse get(UUID playerId) {
    Player player =
        playerRepository.findById(playerId).orElseThrow(() -> new PlayerNotFoundException(playerId));
    return PlayerResponse.from(player);
  }
```

In `backend/api/src/main/java/com/sporya/club/controller/ClubApiExceptionHandler.java`, add the import `com.sporya.club.domain.PlayerNotFoundException` and this handler next to `handleTeamNotFound`:
```java
  @ExceptionHandler(PlayerNotFoundException.class)
  ResponseEntity<ErrorResponse> handlePlayerNotFound(PlayerNotFoundException ex) {
    return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ErrorResponse(ex.getMessage()));
  }
```

- [ ] **Step 2: Write `MatchEventType` and `MatchEvent`**

`backend/api/src/main/java/com/sporya/match/domain/MatchEventType.java`:
```java
package com.sporya.match.domain;

public enum MatchEventType {
  GOAL_SCORED,
  YELLOW_CARD,
  RED_CARD,
  SUBSTITUTION
}
```

`backend/api/src/main/java/com/sporya/match/domain/MatchEvent.java`:
```java
package com.sporya.match.domain;

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
@Table(name = "match_events", schema = "match")
public class MatchEvent {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(name = "match_id", nullable = false)
  private UUID matchId;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private MatchEventType type;

  @Column(nullable = false)
  private int minute;

  @Column(name = "player_id", nullable = false)
  private UUID playerId;

  @Column(name = "team_id", nullable = false)
  private UUID teamId;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  protected MatchEvent() {}

  public MatchEvent(UUID matchId, MatchEventType type, int minute, UUID playerId, UUID teamId) {
    this.matchId = matchId;
    this.type = type;
    this.minute = minute;
    this.playerId = playerId;
    this.teamId = teamId;
    this.createdAt = Instant.now();
  }

  public UUID getId() {
    return id;
  }

  public UUID getMatchId() {
    return matchId;
  }

  public MatchEventType getType() {
    return type;
  }

  public int getMinute() {
    return minute;
  }

  public UUID getPlayerId() {
    return playerId;
  }

  public UUID getTeamId() {
    return teamId;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }
}
```

`backend/api/src/main/java/com/sporya/match/domain/PlayerNotInMatchException.java`:
```java
package com.sporya.match.domain;

import java.util.UUID;

public class PlayerNotInMatchException extends RuntimeException {

  public PlayerNotInMatchException(UUID playerId, UUID matchId) {
    super("Player " + playerId + " is not part of match " + matchId);
  }
}
```

- [ ] **Step 3: Write `MatchEventRepository`**

`backend/api/src/main/java/com/sporya/match/infrastructure/persistence/MatchEventRepository.java`:
```java
package com.sporya.match.infrastructure.persistence;

import com.sporya.match.domain.MatchEvent;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MatchEventRepository extends JpaRepository<MatchEvent, UUID> {

  List<MatchEvent> findByMatchIdOrderByMinuteAsc(UUID matchId);
}
```

- [ ] **Step 4: Write the DTOs**

`backend/api/src/main/java/com/sporya/match/controller/dto/MatchEventResponse.java`:
```java
package com.sporya.match.controller.dto;

import com.sporya.match.domain.MatchEvent;
import com.sporya.match.domain.MatchEventType;
import java.time.Instant;
import java.util.UUID;

public record MatchEventResponse(
    UUID id, UUID matchId, MatchEventType type, int minute, UUID playerId, UUID teamId, Instant createdAt) {

  public static MatchEventResponse from(MatchEvent event) {
    return new MatchEventResponse(
        event.getId(),
        event.getMatchId(),
        event.getType(),
        event.getMinute(),
        event.getPlayerId(),
        event.getTeamId(),
        event.getCreatedAt());
  }
}
```

`backend/api/src/main/java/com/sporya/match/controller/dto/CreateMatchEventRequest.java`:
```java
package com.sporya.match.controller.dto;

import com.sporya.match.domain.MatchEventType;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record CreateMatchEventRequest(
    @NotNull MatchEventType type, @Min(0) int minute, @NotNull UUID playerId) {}
```

- [ ] **Step 5: Add the `PlayerNotInMatchException` handler**

In `backend/api/src/main/java/com/sporya/match/controller/MatchApiExceptionHandler.java`, add the import `com.sporya.match.domain.PlayerNotInMatchException` and this handler next to `handleInvalidMatchState`:
```java
  @ExceptionHandler(PlayerNotInMatchException.class)
  ResponseEntity<ErrorResponse> handlePlayerNotInMatch(PlayerNotInMatchException ex) {
    return ResponseEntity.badRequest().body(new ErrorResponse(ex.getMessage()));
  }
```

- [ ] **Step 6: Extend `MatchFlowIT` with failing event tests**

Add these imports:
```java
import com.sporya.club.controller.dto.CreatePlayerRequest;
import com.sporya.club.controller.dto.PlayerResponse;
import com.sporya.match.controller.dto.CreateMatchEventRequest;
import com.sporya.match.controller.dto.MatchEventResponse;
import com.sporya.match.domain.MatchEventType;
import java.time.LocalDate;
```
Add this helper after `transition`:
```java
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
        new HttpEntity<>(new CreateMatchEventRequest(type, minute, playerId), authHeaders(accessToken)),
        MatchEventResponse.class);
  }
```
Add these three test methods after `finishingAScheduledMatchReturns409`:
```java
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
```

- [ ] **Step 7: Run the test to verify it fails**

Run: `cd backend/api && ./mvnw test -Dtest=MatchFlowIT`
Expected: FAIL — compilation error, `MatchEventService`/`MatchEventController` don't exist yet.

- [ ] **Step 8: Implement `MatchEventService`**

`backend/api/src/main/java/com/sporya/match/application/MatchEventService.java`:
```java
package com.sporya.match.application;

import com.sporya.auth.domain.Role;
import com.sporya.auth.infrastructure.security.AuthenticatedUser;
import com.sporya.club.application.PlayerService;
import com.sporya.club.application.TeamService;
import com.sporya.match.controller.dto.CreateMatchEventRequest;
import com.sporya.match.controller.dto.MatchEventResponse;
import com.sporya.match.domain.InvalidMatchStateException;
import com.sporya.match.domain.Match;
import com.sporya.match.domain.MatchAccessDeniedException;
import com.sporya.match.domain.MatchEvent;
import com.sporya.match.domain.MatchNotFoundException;
import com.sporya.match.domain.MatchStatus;
import com.sporya.match.domain.PlayerNotInMatchException;
import com.sporya.match.infrastructure.persistence.MatchEventRepository;
import com.sporya.match.infrastructure.persistence.MatchRepository;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MatchEventService {

  private final MatchRepository matchRepository;
  private final MatchEventRepository matchEventRepository;
  private final TeamService teamService;
  private final PlayerService playerService;

  public MatchEventService(
      MatchRepository matchRepository,
      MatchEventRepository matchEventRepository,
      TeamService teamService,
      PlayerService playerService) {
    this.matchRepository = matchRepository;
    this.matchEventRepository = matchEventRepository;
    this.teamService = teamService;
    this.playerService = playerService;
  }

  @Transactional
  public MatchEventResponse add(AuthenticatedUser caller, UUID matchId, CreateMatchEventRequest request) {
    Match match =
        matchRepository.findById(matchId).orElseThrow(() -> new MatchNotFoundException(matchId));
    if (match.getStatus() != MatchStatus.LIVE) {
      throw new InvalidMatchStateException("Match must be LIVE to add an event, was: " + match.getStatus());
    }
    UUID homeClubId = teamService.get(match.getHomeTeamId()).clubId();
    UUID awayClubId = teamService.get(match.getAwayTeamId()).clubId();
    boolean authorized =
        caller.hasAnyRole(homeClubId, Role.ADMIN, Role.COACH)
            || caller.hasAnyRole(awayClubId, Role.ADMIN, Role.COACH);
    if (!authorized) {
      throw new MatchAccessDeniedException("Not authorized to manage this match");
    }
    UUID playerTeamId = playerService.get(request.playerId()).teamId();
    if (!playerTeamId.equals(match.getHomeTeamId()) && !playerTeamId.equals(match.getAwayTeamId())) {
      throw new PlayerNotInMatchException(request.playerId(), matchId);
    }
    MatchEvent event =
        new MatchEvent(matchId, request.type(), request.minute(), request.playerId(), playerTeamId);
    return MatchEventResponse.from(matchEventRepository.save(event));
  }

  @Transactional(readOnly = true)
  public List<MatchEventResponse> listForMatch(UUID matchId) {
    if (!matchRepository.existsById(matchId)) {
      throw new MatchNotFoundException(matchId);
    }
    return matchEventRepository.findByMatchIdOrderByMinuteAsc(matchId).stream()
        .map(MatchEventResponse::from)
        .toList();
  }
}
```

- [ ] **Step 9: Implement `MatchEventController`**

`backend/api/src/main/java/com/sporya/match/controller/MatchEventController.java`:
```java
package com.sporya.match.controller;

import com.sporya.auth.infrastructure.security.AuthenticatedUser;
import com.sporya.match.application.MatchEventService;
import com.sporya.match.controller.dto.CreateMatchEventRequest;
import com.sporya.match.controller.dto.MatchEventResponse;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/matches/{matchId}/events")
public class MatchEventController {

  private final MatchEventService matchEventService;

  public MatchEventController(MatchEventService matchEventService) {
    this.matchEventService = matchEventService;
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  public MatchEventResponse add(
      @AuthenticationPrincipal AuthenticatedUser caller,
      @PathVariable UUID matchId,
      @Valid @RequestBody CreateMatchEventRequest request) {
    return matchEventService.add(caller, matchId, request);
  }

  @GetMapping
  public List<MatchEventResponse> list(@PathVariable UUID matchId) {
    return matchEventService.listForMatch(matchId);
  }
}
```

- [ ] **Step 10: Run the test to verify it passes**

Run: `cd backend/api && ./mvnw test -Dtest=MatchFlowIT`
Expected: PASS (9 tests).

- [ ] **Step 11: Run the full backend test suite**

Run: `cd backend/api && ./mvnw verify`
Expected: PASS — every `*IT`/`*Test` green, including the existing `ClubFlowIT`/`ClubMembershipRbacIT` (unaffected by the `PlayerService.get` addition).

- [ ] **Step 12: Commit**

```bash
git add backend/api/src/main/java/com/sporya/club/domain/PlayerNotFoundException.java \
        backend/api/src/main/java/com/sporya/club/application/PlayerService.java \
        backend/api/src/main/java/com/sporya/club/controller/ClubApiExceptionHandler.java \
        backend/api/src/main/java/com/sporya/match \
        backend/api/src/test/java/com/sporya/match
git commit -m "feat(match): add MatchEvent create/list, roster validation"
```

---

## Task 5: Red card rule + score derivation

**Files:**
- Modify: `backend/api/src/main/java/com/sporya/match/infrastructure/persistence/MatchEventRepository.java`
- Create: `backend/api/src/main/java/com/sporya/match/domain/RedCardViolationException.java`
- Modify: `backend/api/src/main/java/com/sporya/match/controller/MatchApiExceptionHandler.java`
- Modify: `backend/api/src/main/java/com/sporya/match/application/MatchEventService.java`
- Modify: `backend/api/src/main/java/com/sporya/match/controller/dto/MatchResponse.java`
- Modify: `backend/api/src/main/java/com/sporya/match/application/MatchService.java`
- Modify: `backend/api/src/test/java/com/sporya/match/MatchFlowIT.java`

**Interfaces:**
- Consumes: `MatchEventType.GOAL_SCORED`/`RED_CARD` (Task 4).
- Produces: `MatchResponse` now carries `homeScore`/`awayScore` — **breaking change to the Task 2 shape**, every existing caller (`MatchService.create/get/list/start/halfTime/resume/finish`) is updated in this task. Task 6 (frontend) consumes this final shape directly.

- [ ] **Step 1: Add the red-card and score repository methods**

In `backend/api/src/main/java/com/sporya/match/infrastructure/persistence/MatchEventRepository.java`, add the import `com.sporya.match.domain.MatchEventType` and these two methods inside the interface:
```java
  boolean existsByMatchIdAndPlayerIdAndType(UUID matchId, UUID playerId, MatchEventType type);

  long countByMatchIdAndTeamIdAndType(UUID matchId, UUID teamId, MatchEventType type);
```

- [ ] **Step 2: Write `RedCardViolationException`**

`backend/api/src/main/java/com/sporya/match/domain/RedCardViolationException.java`:
```java
package com.sporya.match.domain;

import java.util.UUID;

public class RedCardViolationException extends RuntimeException {

  public RedCardViolationException(UUID playerId, UUID matchId) {
    super("Player " + playerId + " already has a red card in match " + matchId + ", cannot score");
  }
}
```

- [ ] **Step 3: Add the `RedCardViolationException` handler**

In `backend/api/src/main/java/com/sporya/match/controller/MatchApiExceptionHandler.java`, add the import `com.sporya.match.domain.RedCardViolationException` and this handler next to `handlePlayerNotInMatch`:
```java
  @ExceptionHandler(RedCardViolationException.class)
  ResponseEntity<ErrorResponse> handleRedCardViolation(RedCardViolationException ex) {
    return ResponseEntity.status(HttpStatus.CONFLICT).body(new ErrorResponse(ex.getMessage()));
  }
```

- [ ] **Step 4: Extend `MatchFlowIT` with failing red-card and score tests**

Add these test methods to `MatchFlowIT` after `addEventForPlayerNotInMatchReturns400`:
```java
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
```

- [ ] **Step 5: Run the test to verify it fails**

Run: `cd backend/api && ./mvnw test -Dtest=MatchFlowIT`
Expected: FAIL — compilation error, `MatchResponse` has no `homeScore()`/`awayScore()` yet.

- [ ] **Step 6: Add the red-card check to `MatchEventService`**

In `backend/api/src/main/java/com/sporya/match/application/MatchEventService.java`, add the imports `com.sporya.match.domain.MatchEventType` and `com.sporya.match.domain.RedCardViolationException` next to the other domain imports, and add this check right after the `PlayerNotInMatchException` check and before building the `MatchEvent`:
```java
    if (request.type() == MatchEventType.GOAL_SCORED
        && matchEventRepository.existsByMatchIdAndPlayerIdAndType(
            matchId, request.playerId(), MatchEventType.RED_CARD)) {
      throw new RedCardViolationException(request.playerId(), matchId);
    }
```

- [ ] **Step 7: Rewrite `MatchResponse` with the derived score**

Replace the full contents of `backend/api/src/main/java/com/sporya/match/controller/dto/MatchResponse.java`:
```java
package com.sporya.match.controller.dto;

import com.sporya.match.domain.Match;
import com.sporya.match.domain.MatchStatus;
import java.time.Instant;
import java.util.UUID;

public record MatchResponse(
    UUID id,
    UUID seasonId,
    UUID homeTeamId,
    UUID awayTeamId,
    MatchStatus status,
    Instant kickoffAt,
    int homeScore,
    int awayScore,
    Instant createdAt) {

  public static MatchResponse from(Match match, int homeScore, int awayScore) {
    return new MatchResponse(
        match.getId(),
        match.getSeasonId(),
        match.getHomeTeamId(),
        match.getAwayTeamId(),
        match.getStatus(),
        match.getKickoffAt(),
        homeScore,
        awayScore,
        match.getCreatedAt());
  }
}
```

- [ ] **Step 8: Rewrite `MatchService` to compute the score on every response**

Replace the full contents of `backend/api/src/main/java/com/sporya/match/application/MatchService.java`:
```java
package com.sporya.match.application;

import com.sporya.auth.domain.Role;
import com.sporya.auth.infrastructure.security.AuthenticatedUser;
import com.sporya.club.application.TeamService;
import com.sporya.match.controller.dto.CreateMatchRequest;
import com.sporya.match.controller.dto.MatchResponse;
import com.sporya.match.domain.InvalidMatchStateException;
import com.sporya.match.domain.Match;
import com.sporya.match.domain.MatchAccessDeniedException;
import com.sporya.match.domain.MatchEventType;
import com.sporya.match.domain.MatchNotFoundException;
import com.sporya.match.domain.MatchStatus;
import com.sporya.match.domain.SeasonNotFoundException;
import com.sporya.match.infrastructure.persistence.MatchEventRepository;
import com.sporya.match.infrastructure.persistence.MatchRepository;
import com.sporya.match.infrastructure.persistence.SeasonRepository;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MatchService {

  private final MatchRepository matchRepository;
  private final SeasonRepository seasonRepository;
  private final MatchEventRepository matchEventRepository;
  private final TeamService teamService;

  public MatchService(
      MatchRepository matchRepository,
      SeasonRepository seasonRepository,
      MatchEventRepository matchEventRepository,
      TeamService teamService) {
    this.matchRepository = matchRepository;
    this.seasonRepository = seasonRepository;
    this.matchEventRepository = matchEventRepository;
    this.teamService = teamService;
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
        new Match(request.seasonId(), request.homeTeamId(), request.awayTeamId(), request.kickoffAt());
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
      throw new InvalidMatchStateException("Match must be SCHEDULED to start, was: " + match.getStatus());
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
      throw new InvalidMatchStateException("Match must be HALF_TIME to resume, was: " + match.getStatus());
    }
    match.setStatus(MatchStatus.LIVE);
    return toResponse(matchRepository.save(match));
  }

  @Transactional
  public MatchResponse finish(AuthenticatedUser caller, UUID matchId) {
    Match match = findMatch(matchId);
    requireHomeOrAwayStaff(caller, match);
    if (match.getStatus() != MatchStatus.LIVE) {
      throw new InvalidMatchStateException("Match must be LIVE to finish, was: " + match.getStatus());
    }
    match.setStatus(MatchStatus.FINISHED);
    return toResponse(matchRepository.save(match));
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

- [ ] **Step 9: Run the test to verify it passes**

Run: `cd backend/api && ./mvnw test -Dtest=MatchFlowIT`
Expected: PASS (11 tests).

- [ ] **Step 10: Run the full backend test suite**

Run: `cd backend/api && ./mvnw verify`
Expected: PASS — every test green, spotless clean.

- [ ] **Step 11: Commit**

```bash
git add backend/api/src/main/java/com/sporya/match backend/api/src/test/java/com/sporya/match
git commit -m "feat(match): derive score from events, red card blocks further goals"
```

---

## Task 6: Frontend API client — Competition/Season/Match/MatchEvent functions

**Files:**
- Modify: `frontend/src/lib/api.ts`

**Interfaces:**
- Consumes: nothing new (this task only touches the fetch wrapper's callers).
- Produces: `listCompetitions`, `createCompetition`, `listSeasons`, `createSeason`, `listMatches`, `getMatch`, `createMatch`, `startMatch`, `halfTimeMatch`, `resumeMatch`, `finishMatch`, `listMatchEvents`, `addMatchEvent` — all `(accessToken: string, ...) => Promise<T>` — and the interfaces `CompetitionResponse`, `SeasonResponse`, `MatchResponse` (with `homeScore`/`awayScore`), `MatchEventResponse`. Tasks 7–9 (the three new pages) import these directly.

- [ ] **Step 1: Append the Competition/Season/Match/MatchEvent API functions**

Append this to the end of `frontend/src/lib/api.ts`:
```ts
export interface CompetitionResponse {
  id: string
  name: string
  createdAt: string
}

export interface SeasonResponse {
  id: string
  label: string
  competitionId: string
  createdAt: string
}

export interface MatchResponse {
  id: string
  seasonId: string
  homeTeamId: string
  awayTeamId: string
  status: string
  kickoffAt: string
  homeScore: number
  awayScore: number
  createdAt: string
}

export interface MatchEventResponse {
  id: string
  matchId: string
  type: string
  minute: number
  playerId: string
  teamId: string
  createdAt: string
}

export function listCompetitions(accessToken: string): Promise<CompetitionResponse[]> {
  return request<CompetitionResponse[]>('/api/v1/competitions', { headers: authHeaders(accessToken) })
}

export function createCompetition(accessToken: string, name: string): Promise<CompetitionResponse> {
  return request<CompetitionResponse>('/api/v1/competitions', {
    method: 'POST',
    headers: authHeaders(accessToken),
    body: JSON.stringify({ name }),
  })
}

export function listSeasons(accessToken: string, competitionId: string): Promise<SeasonResponse[]> {
  return request<SeasonResponse[]>(`/api/v1/competitions/${competitionId}/seasons`, {
    headers: authHeaders(accessToken),
  })
}

export function createSeason(
  accessToken: string,
  competitionId: string,
  label: string,
): Promise<SeasonResponse> {
  return request<SeasonResponse>(`/api/v1/competitions/${competitionId}/seasons`, {
    method: 'POST',
    headers: authHeaders(accessToken),
    body: JSON.stringify({ label }),
  })
}

export function listMatches(accessToken: string): Promise<MatchResponse[]> {
  return request<MatchResponse[]>('/api/v1/matches', { headers: authHeaders(accessToken) })
}

export function getMatch(accessToken: string, matchId: string): Promise<MatchResponse> {
  return request<MatchResponse>(`/api/v1/matches/${matchId}`, { headers: authHeaders(accessToken) })
}

export function createMatch(
  accessToken: string,
  seasonId: string,
  homeTeamId: string,
  awayTeamId: string,
  kickoffAt: string,
): Promise<MatchResponse> {
  return request<MatchResponse>('/api/v1/matches', {
    method: 'POST',
    headers: authHeaders(accessToken),
    body: JSON.stringify({ seasonId, homeTeamId, awayTeamId, kickoffAt }),
  })
}

function transitionMatch(accessToken: string, matchId: string, transition: string): Promise<MatchResponse> {
  return request<MatchResponse>(`/api/v1/matches/${matchId}/${transition}`, {
    method: 'POST',
    headers: authHeaders(accessToken),
  })
}

export function startMatch(accessToken: string, matchId: string): Promise<MatchResponse> {
  return transitionMatch(accessToken, matchId, 'start')
}

export function halfTimeMatch(accessToken: string, matchId: string): Promise<MatchResponse> {
  return transitionMatch(accessToken, matchId, 'half-time')
}

export function resumeMatch(accessToken: string, matchId: string): Promise<MatchResponse> {
  return transitionMatch(accessToken, matchId, 'resume')
}

export function finishMatch(accessToken: string, matchId: string): Promise<MatchResponse> {
  return transitionMatch(accessToken, matchId, 'finish')
}

export function listMatchEvents(accessToken: string, matchId: string): Promise<MatchEventResponse[]> {
  return request<MatchEventResponse[]>(`/api/v1/matches/${matchId}/events`, {
    headers: authHeaders(accessToken),
  })
}

export function addMatchEvent(
  accessToken: string,
  matchId: string,
  type: string,
  minute: number,
  playerId: string,
): Promise<MatchEventResponse> {
  return request<MatchEventResponse>(`/api/v1/matches/${matchId}/events`, {
    method: 'POST',
    headers: authHeaders(accessToken),
    body: JSON.stringify({ type, minute, playerId }),
  })
}
```

- [ ] **Step 2: Verify it builds**

Run: `cd frontend && npm run lint && npm run build`
Expected: both succeed (0 errors).

- [ ] **Step 3: Commit**

```bash
git add frontend/src/lib/api.ts
git commit -m "feat(frontend): add Competition/Season/Match/MatchEvent API client"
```

---

## Task 7: `CompetitionsPage` — list competitions, create competition + season

**Files:**
- Create: `frontend/src/pages/CompetitionsPage.tsx`
- Modify: `frontend/src/App.tsx`

**Interfaces:**
- Consumes: `listCompetitions`, `createCompetition`, `listSeasons`, `createSeason`, `CompetitionResponse`, `SeasonResponse` (Task 6); `ProtectedRoute`, `useAuth` (existing).
- Produces: route `/competitions`, used by Task 8's season selector.

- [ ] **Step 1: Create `CompetitionsPage`**

`frontend/src/pages/CompetitionsPage.tsx`:
```tsx
import { useState, type FormEvent } from 'react'
import { Link } from 'react-router-dom'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'

import { createCompetition, createSeason, listCompetitions, listSeasons } from '@/lib/api'
import { useAuth } from '@/lib/auth-context'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'

export function CompetitionsPage() {
  const { accessToken } = useAuth()
  const queryClient = useQueryClient()
  const [name, setName] = useState('')
  const [seasonLabels, setSeasonLabels] = useState<Record<string, string>>({})

  const competitionsQuery = useQuery({
    queryKey: ['competitions', accessToken],
    queryFn: () => listCompetitions(accessToken as string),
    enabled: Boolean(accessToken),
  })

  const createCompetitionMutation = useMutation({
    mutationFn: () => createCompetition(accessToken as string, name),
    onSuccess: () => {
      setName('')
      queryClient.invalidateQueries({ queryKey: ['competitions'] })
    },
  })

  const createSeasonMutation = useMutation({
    mutationFn: (competitionId: string) =>
      createSeason(accessToken as string, competitionId, seasonLabels[competitionId] ?? ''),
    onSuccess: (_data, competitionId) => {
      setSeasonLabels((labels) => ({ ...labels, [competitionId]: '' }))
      queryClient.invalidateQueries({ queryKey: ['seasons', competitionId] })
    },
  })

  function handleCreateCompetition(event: FormEvent) {
    event.preventDefault()
    createCompetitionMutation.mutate()
  }

  return (
    <div className="mx-auto flex min-h-svh max-w-2xl flex-col gap-6 p-6">
      <Link to="/dashboard" className="text-sm text-muted-foreground underline underline-offset-4">
        ← Retour au tableau de bord
      </Link>

      <Card>
        <CardHeader>
          <CardTitle>Compétitions</CardTitle>
          <CardDescription>Liste des compétitions et de leurs saisons.</CardDescription>
        </CardHeader>
        <CardContent className="flex flex-col gap-4">
          {competitionsQuery.isLoading && (
            <p className="text-sm text-muted-foreground">Chargement…</p>
          )}
          {competitionsQuery.data?.length === 0 && (
            <p className="text-sm text-muted-foreground">Aucune compétition pour l'instant.</p>
          )}
          {competitionsQuery.data?.map((competition) => (
            <CompetitionRow
              key={competition.id}
              competitionId={competition.id}
              competitionName={competition.name}
              accessToken={accessToken as string}
              seasonLabel={seasonLabels[competition.id] ?? ''}
              onSeasonLabelChange={(label) =>
                setSeasonLabels((labels) => ({ ...labels, [competition.id]: label }))
              }
              onCreateSeason={() => createSeasonMutation.mutate(competition.id)}
              creatingSeason={createSeasonMutation.isPending}
            />
          ))}
        </CardContent>
      </Card>

      <Card>
        <CardHeader>
          <CardTitle>Créer une compétition</CardTitle>
        </CardHeader>
        <CardContent>
          <form onSubmit={handleCreateCompetition} className="flex flex-col gap-4">
            <div className="flex flex-col gap-2">
              <Label htmlFor="competition-name">Nom</Label>
              <Input
                id="competition-name"
                required
                value={name}
                onChange={(e) => setName(e.target.value)}
              />
            </div>
            <Button type="submit" disabled={createCompetitionMutation.isPending}>
              {createCompetitionMutation.isPending ? 'Création…' : 'Créer'}
            </Button>
          </form>
        </CardContent>
      </Card>
    </div>
  )
}

function CompetitionRow({
  competitionId,
  competitionName,
  accessToken,
  seasonLabel,
  onSeasonLabelChange,
  onCreateSeason,
  creatingSeason,
}: {
  competitionId: string
  competitionName: string
  accessToken: string
  seasonLabel: string
  onSeasonLabelChange: (label: string) => void
  onCreateSeason: () => void
  creatingSeason: boolean
}) {
  const seasonsQuery = useQuery({
    queryKey: ['seasons', competitionId],
    queryFn: () => listSeasons(accessToken, competitionId),
    enabled: Boolean(accessToken),
  })

  function handleSubmit(event: FormEvent) {
    event.preventDefault()
    onCreateSeason()
  }

  return (
    <div className="rounded-md border border-border p-3">
      <p className="font-medium">{competitionName}</p>
      <div className="mt-2 flex flex-col gap-1">
        {seasonsQuery.data?.map((season) => (
          <p key={season.id} className="text-sm text-muted-foreground">
            {season.label}
          </p>
        ))}
      </div>
      <form onSubmit={handleSubmit} className="mt-2 flex gap-2">
        <Input
          placeholder="Saison (ex: 2026)"
          value={seasonLabel}
          onChange={(e) => onSeasonLabelChange(e.target.value)}
          required
        />
        <Button type="submit" size="sm" disabled={creatingSeason}>
          Ajouter
        </Button>
      </form>
    </div>
  )
}
```

- [ ] **Step 2: Add the `/competitions` route**

In `frontend/src/App.tsx`, add the import `import { CompetitionsPage } from '@/pages/CompetitionsPage'` next to the other page imports, and add this route inside `<Routes>`, after the `/teams/:teamId` route:
```tsx
      <Route
        path="/competitions"
        element={
          <ProtectedRoute>
            <CompetitionsPage />
          </ProtectedRoute>
        }
      />
```

- [ ] **Step 3: Verify it builds**

Run: `cd frontend && npm run lint && npm run build`
Expected: both succeed.

- [ ] **Step 4: Commit**

```bash
git add frontend/src/pages/CompetitionsPage.tsx frontend/src/App.tsx
git commit -m "feat(frontend): add CompetitionsPage (list + create competition/season)"
```

---

## Task 8: `MatchesPage` — list matches, create match

**Files:**
- Create: `frontend/src/pages/MatchesPage.tsx`
- Modify: `frontend/src/App.tsx`

**Interfaces:**
- Consumes: `listMatches`, `createMatch`, `listCompetitions`, `listSeasons`, `MatchResponse` (Task 6); `listClubs`, `listTeams`, `ClubResponse`, `TeamResponse` (existing, Club module).
- Produces: route `/matches`, linked from Task 9 (`MatchDetailPage` links back here) and to it (`/matches/:matchId`).

- [ ] **Step 1: Create `MatchesPage`**

`frontend/src/pages/MatchesPage.tsx`:
```tsx
import { useState, type FormEvent } from 'react'
import { Link } from 'react-router-dom'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'

import {
  createMatch,
  listClubs,
  listCompetitions,
  listMatches,
  listSeasons,
  listTeams,
} from '@/lib/api'
import { useAuth } from '@/lib/auth-context'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'

const selectClassName =
  'flex h-9 w-full rounded-md border border-input bg-transparent px-3 py-1 text-sm shadow-sm focus-visible:outline-none focus-visible:ring-1 focus-visible:ring-ring'

export function MatchesPage() {
  const { accessToken } = useAuth()
  const queryClient = useQueryClient()

  const [competitionId, setCompetitionId] = useState('')
  const [seasonId, setSeasonId] = useState('')
  const [homeClubId, setHomeClubId] = useState('')
  const [homeTeamId, setHomeTeamId] = useState('')
  const [awayClubId, setAwayClubId] = useState('')
  const [awayTeamId, setAwayTeamId] = useState('')
  const [kickoffAt, setKickoffAt] = useState('')

  const matchesQuery = useQuery({
    queryKey: ['matches', accessToken],
    queryFn: () => listMatches(accessToken as string),
    enabled: Boolean(accessToken),
  })

  const competitionsQuery = useQuery({
    queryKey: ['competitions', accessToken],
    queryFn: () => listCompetitions(accessToken as string),
    enabled: Boolean(accessToken),
  })

  const seasonsQuery = useQuery({
    queryKey: ['seasons', competitionId],
    queryFn: () => listSeasons(accessToken as string, competitionId),
    enabled: Boolean(accessToken && competitionId),
  })

  const clubsQuery = useQuery({
    queryKey: ['clubs', accessToken],
    queryFn: () => listClubs(accessToken as string),
    enabled: Boolean(accessToken),
  })

  const homeTeamsQuery = useQuery({
    queryKey: ['teams', homeClubId],
    queryFn: () => listTeams(accessToken as string, homeClubId),
    enabled: Boolean(accessToken && homeClubId),
  })

  const awayTeamsQuery = useQuery({
    queryKey: ['teams', awayClubId],
    queryFn: () => listTeams(accessToken as string, awayClubId),
    enabled: Boolean(accessToken && awayClubId),
  })

  const createMutation = useMutation({
    mutationFn: () =>
      createMatch(
        accessToken as string,
        seasonId,
        homeTeamId,
        awayTeamId,
        new Date(kickoffAt).toISOString(),
      ),
    onSuccess: () => {
      setKickoffAt('')
      queryClient.invalidateQueries({ queryKey: ['matches'] })
    },
  })

  function handleSubmit(event: FormEvent) {
    event.preventDefault()
    createMutation.mutate()
  }

  return (
    <div className="mx-auto flex min-h-svh max-w-2xl flex-col gap-6 p-6">
      <Link to="/dashboard" className="text-sm text-muted-foreground underline underline-offset-4">
        ← Retour au tableau de bord
      </Link>

      <Card>
        <CardHeader>
          <CardTitle>Matchs</CardTitle>
          <CardDescription>Liste des matchs planifiés et en cours.</CardDescription>
        </CardHeader>
        <CardContent className="flex flex-col gap-2">
          {matchesQuery.isLoading && <p className="text-sm text-muted-foreground">Chargement…</p>}
          {matchesQuery.data?.length === 0 && (
            <p className="text-sm text-muted-foreground">Aucun match pour l'instant.</p>
          )}
          {matchesQuery.data?.map((match) => (
            <Link
              key={match.id}
              to={`/matches/${match.id}`}
              className="flex items-center justify-between rounded-md border border-border px-3 py-2 text-sm hover:bg-accent"
            >
              <span>{new Date(match.kickoffAt).toLocaleString()}</span>
              <span className="text-muted-foreground">
                {match.status} — {match.homeScore} : {match.awayScore}
              </span>
            </Link>
          ))}
        </CardContent>
      </Card>

      <Card>
        <CardHeader>
          <CardTitle>Créer un match</CardTitle>
        </CardHeader>
        <CardContent>
          <form onSubmit={handleSubmit} className="flex flex-col gap-4">
            <div className="flex flex-col gap-2">
              <Label htmlFor="competition">Compétition</Label>
              <select
                id="competition"
                className={selectClassName}
                required
                value={competitionId}
                onChange={(e) => {
                  setCompetitionId(e.target.value)
                  setSeasonId('')
                }}
              >
                <option value="">Choisir…</option>
                {competitionsQuery.data?.map((competition) => (
                  <option key={competition.id} value={competition.id}>
                    {competition.name}
                  </option>
                ))}
              </select>
            </div>
            <div className="flex flex-col gap-2">
              <Label htmlFor="season">Saison</Label>
              <select
                id="season"
                className={selectClassName}
                required
                value={seasonId}
                onChange={(e) => setSeasonId(e.target.value)}
                disabled={!competitionId}
              >
                <option value="">Choisir…</option>
                {seasonsQuery.data?.map((season) => (
                  <option key={season.id} value={season.id}>
                    {season.label}
                  </option>
                ))}
              </select>
            </div>
            <div className="flex flex-col gap-2">
              <Label htmlFor="home-club">Club domicile</Label>
              <select
                id="home-club"
                className={selectClassName}
                required
                value={homeClubId}
                onChange={(e) => {
                  setHomeClubId(e.target.value)
                  setHomeTeamId('')
                }}
              >
                <option value="">Choisir…</option>
                {clubsQuery.data?.map((club) => (
                  <option key={club.id} value={club.id}>
                    {club.name}
                  </option>
                ))}
              </select>
            </div>
            <div className="flex flex-col gap-2">
              <Label htmlFor="home-team">Équipe domicile</Label>
              <select
                id="home-team"
                className={selectClassName}
                required
                value={homeTeamId}
                onChange={(e) => setHomeTeamId(e.target.value)}
                disabled={!homeClubId}
              >
                <option value="">Choisir…</option>
                {homeTeamsQuery.data?.map((team) => (
                  <option key={team.id} value={team.id}>
                    {team.name}
                  </option>
                ))}
              </select>
            </div>
            <div className="flex flex-col gap-2">
              <Label htmlFor="away-club">Club extérieur</Label>
              <select
                id="away-club"
                className={selectClassName}
                required
                value={awayClubId}
                onChange={(e) => {
                  setAwayClubId(e.target.value)
                  setAwayTeamId('')
                }}
              >
                <option value="">Choisir…</option>
                {clubsQuery.data?.map((club) => (
                  <option key={club.id} value={club.id}>
                    {club.name}
                  </option>
                ))}
              </select>
            </div>
            <div className="flex flex-col gap-2">
              <Label htmlFor="away-team">Équipe extérieure</Label>
              <select
                id="away-team"
                className={selectClassName}
                required
                value={awayTeamId}
                onChange={(e) => setAwayTeamId(e.target.value)}
                disabled={!awayClubId}
              >
                <option value="">Choisir…</option>
                {awayTeamsQuery.data?.map((team) => (
                  <option key={team.id} value={team.id}>
                    {team.name}
                  </option>
                ))}
              </select>
            </div>
            <div className="flex flex-col gap-2">
              <Label htmlFor="kickoff">Coup d'envoi</Label>
              <Input
                id="kickoff"
                type="datetime-local"
                required
                value={kickoffAt}
                onChange={(e) => setKickoffAt(e.target.value)}
              />
            </div>
            <Button type="submit" disabled={createMutation.isPending}>
              {createMutation.isPending ? 'Création…' : 'Créer'}
            </Button>
          </form>
        </CardContent>
      </Card>
    </div>
  )
}
```

- [ ] **Step 2: Add the `/matches` route**

In `frontend/src/App.tsx`, add the import `import { MatchesPage } from '@/pages/MatchesPage'` next to the other page imports, and add this route after the `/competitions` route:
```tsx
      <Route
        path="/matches"
        element={
          <ProtectedRoute>
            <MatchesPage />
          </ProtectedRoute>
        }
      />
```

- [ ] **Step 3: Verify it builds**

Run: `cd frontend && npm run lint && npm run build`
Expected: both succeed.

- [ ] **Step 4: Commit**

```bash
git add frontend/src/pages/MatchesPage.tsx frontend/src/App.tsx
git commit -m "feat(frontend): add MatchesPage (list + create match)"
```

---

## Task 9: `MatchDetailPage` — transitions, events, score; link from Dashboard

**Files:**
- Create: `frontend/src/pages/MatchDetailPage.tsx`
- Modify: `frontend/src/App.tsx`
- Modify: `frontend/src/pages/DashboardPage.tsx`

**Interfaces:**
- Consumes: `getMatch`, `listMatchEvents`, `addMatchEvent`, `startMatch`, `halfTimeMatch`, `resumeMatch`, `finishMatch`, `MatchResponse`, `MatchEventResponse` (Task 6); `listPlayers`, `PlayerResponse` (existing, Club module).
- Produces: route `/matches/:matchId`. Last task in this plan.

- [ ] **Step 1: Create `MatchDetailPage`**

`frontend/src/pages/MatchDetailPage.tsx`:
```tsx
import { useState, type FormEvent } from 'react'
import { Link, useParams } from 'react-router-dom'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'

import {
  addMatchEvent,
  finishMatch,
  getMatch,
  halfTimeMatch,
  listMatchEvents,
  listPlayers,
  resumeMatch,
  startMatch,
} from '@/lib/api'
import { useAuth } from '@/lib/auth-context'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'

const EVENT_TYPES = ['GOAL_SCORED', 'YELLOW_CARD', 'RED_CARD', 'SUBSTITUTION'] as const

const selectClassName =
  'flex h-9 w-full rounded-md border border-input bg-transparent px-3 py-1 text-sm shadow-sm focus-visible:outline-none focus-visible:ring-1 focus-visible:ring-ring'

export function MatchDetailPage() {
  const { matchId } = useParams<{ matchId: string }>()
  const { accessToken } = useAuth()
  const queryClient = useQueryClient()
  const [eventType, setEventType] = useState<string>(EVENT_TYPES[0])
  const [minute, setMinute] = useState('')
  const [playerId, setPlayerId] = useState('')

  const matchQuery = useQuery({
    queryKey: ['match', matchId],
    queryFn: () => getMatch(accessToken as string, matchId as string),
    enabled: Boolean(accessToken && matchId),
  })

  const eventsQuery = useQuery({
    queryKey: ['match-events', matchId],
    queryFn: () => listMatchEvents(accessToken as string, matchId as string),
    enabled: Boolean(accessToken && matchId),
  })

  const homePlayersQuery = useQuery({
    queryKey: ['players', matchQuery.data?.homeTeamId],
    queryFn: () => listPlayers(accessToken as string, matchQuery.data!.homeTeamId),
    enabled: Boolean(accessToken && matchQuery.data),
  })

  const awayPlayersQuery = useQuery({
    queryKey: ['players', matchQuery.data?.awayTeamId],
    queryFn: () => listPlayers(accessToken as string, matchQuery.data!.awayTeamId),
    enabled: Boolean(accessToken && matchQuery.data),
  })

  const roster = [...(homePlayersQuery.data ?? []), ...(awayPlayersQuery.data ?? [])]

  function invalidateMatch() {
    queryClient.invalidateQueries({ queryKey: ['match', matchId] })
    queryClient.invalidateQueries({ queryKey: ['match-events', matchId] })
  }

  const startMutation = useMutation({
    mutationFn: () => startMatch(accessToken as string, matchId as string),
    onSuccess: invalidateMatch,
  })
  const halfTimeMutation = useMutation({
    mutationFn: () => halfTimeMatch(accessToken as string, matchId as string),
    onSuccess: invalidateMatch,
  })
  const resumeMutation = useMutation({
    mutationFn: () => resumeMatch(accessToken as string, matchId as string),
    onSuccess: invalidateMatch,
  })
  const finishMutation = useMutation({
    mutationFn: () => finishMatch(accessToken as string, matchId as string),
    onSuccess: invalidateMatch,
  })

  const addEventMutation = useMutation({
    mutationFn: () =>
      addMatchEvent(accessToken as string, matchId as string, eventType, Number(minute), playerId),
    onSuccess: () => {
      setMinute('')
      setPlayerId('')
      invalidateMatch()
    },
  })

  function handleAddEvent(event: FormEvent) {
    event.preventDefault()
    addEventMutation.mutate()
  }

  const status = matchQuery.data?.status

  return (
    <div className="mx-auto flex min-h-svh max-w-2xl flex-col gap-6 p-6">
      <Link to="/matches" className="text-sm text-muted-foreground underline underline-offset-4">
        ← Retour aux matchs
      </Link>

      <Card>
        <CardHeader>
          <CardTitle>
            {matchQuery.data
              ? `${matchQuery.data.homeScore} : ${matchQuery.data.awayScore}`
              : 'Match'}
          </CardTitle>
          <CardDescription>{status}</CardDescription>
        </CardHeader>
        <CardContent className="flex flex-wrap gap-2">
          {status === 'SCHEDULED' && (
            <Button onClick={() => startMutation.mutate()} disabled={startMutation.isPending}>
              Démarrer
            </Button>
          )}
          {status === 'LIVE' && (
            <>
              <Button onClick={() => halfTimeMutation.mutate()} disabled={halfTimeMutation.isPending}>
                Mi-temps
              </Button>
              <Button onClick={() => finishMutation.mutate()} disabled={finishMutation.isPending}>
                Terminer
              </Button>
            </>
          )}
          {status === 'HALF_TIME' && (
            <Button onClick={() => resumeMutation.mutate()} disabled={resumeMutation.isPending}>
              Reprendre
            </Button>
          )}
        </CardContent>
      </Card>

      <Card>
        <CardHeader>
          <CardTitle>Événements</CardTitle>
        </CardHeader>
        <CardContent className="flex flex-col gap-2">
          {eventsQuery.data?.length === 0 && (
            <p className="text-sm text-muted-foreground">Aucun événement pour l'instant.</p>
          )}
          {eventsQuery.data?.map((event) => (
            <div key={event.id} className="rounded-md border border-border px-3 py-2 text-sm">
              {event.minute}&apos; — {event.type}
            </div>
          ))}
        </CardContent>
      </Card>

      {status === 'LIVE' && (
        <Card>
          <CardHeader>
            <CardTitle>Ajouter un événement</CardTitle>
          </CardHeader>
          <CardContent>
            <form onSubmit={handleAddEvent} className="flex flex-col gap-4">
              <div className="flex flex-col gap-2">
                <Label htmlFor="event-type">Type</Label>
                <select
                  id="event-type"
                  className={selectClassName}
                  value={eventType}
                  onChange={(e) => setEventType(e.target.value)}
                >
                  {EVENT_TYPES.map((type) => (
                    <option key={type} value={type}>
                      {type}
                    </option>
                  ))}
                </select>
              </div>
              <div className="flex flex-col gap-2">
                <Label htmlFor="event-minute">Minute</Label>
                <Input
                  id="event-minute"
                  type="number"
                  min={0}
                  required
                  value={minute}
                  onChange={(e) => setMinute(e.target.value)}
                />
              </div>
              <div className="flex flex-col gap-2">
                <Label htmlFor="event-player">Joueur</Label>
                <select
                  id="event-player"
                  className={selectClassName}
                  required
                  value={playerId}
                  onChange={(e) => setPlayerId(e.target.value)}
                >
                  <option value="">Choisir…</option>
                  {roster.map((player) => (
                    <option key={player.id} value={player.id}>
                      {player.name}
                    </option>
                  ))}
                </select>
              </div>
              <Button type="submit" disabled={addEventMutation.isPending}>
                {addEventMutation.isPending ? 'Ajout…' : 'Ajouter'}
              </Button>
            </form>
          </CardContent>
        </Card>
      )}
    </div>
  )
}
```

- [ ] **Step 2: Add the `/matches/:matchId` route**

In `frontend/src/App.tsx`, add the import `import { MatchDetailPage } from '@/pages/MatchDetailPage'` next to the other page imports, and add this route after the `/matches` route:
```tsx
      <Route
        path="/matches/:matchId"
        element={
          <ProtectedRoute>
            <MatchDetailPage />
          </ProtectedRoute>
        }
      />
```

- [ ] **Step 3: Link `/competitions` and `/matches` from the Dashboard**

In `frontend/src/pages/DashboardPage.tsx`, add these two buttons right before the existing "Voir les clubs" button (inside `CardContent`, same `flex flex-col gap-4`):
```tsx
          <Button asChild variant="secondary">
            <Link to="/competitions">Voir les compétitions</Link>
          </Button>
          <Button asChild variant="secondary">
            <Link to="/matches">Voir les matchs</Link>
          </Button>
```

- [ ] **Step 4: Verify it builds**

Run: `cd frontend && npm run lint && npm run build`
Expected: both succeed.

- [ ] **Step 5: Commit**

```bash
git add frontend/src/pages/MatchDetailPage.tsx frontend/src/App.tsx frontend/src/pages/DashboardPage.tsx
git commit -m "feat(frontend): add MatchDetailPage (transitions, events, score), link from Dashboard"
```
