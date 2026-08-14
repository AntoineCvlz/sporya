# Club Module Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build the Club module (Club/Team/Player CRUD, no RBAC yet) as a new `com.sporya.club` package in the monolith, plus the minimal frontend pages to use it — the second module after Auth, confirming the module convention before Match.

**Architecture:** Same layered structure as `com.sporya.auth` (`controller/application/domain/infrastructure`), new Postgres schema `club`, all routes behind the existing Bearer-JWT security filter (no changes to `SecurityConfig`). Backend built as three vertical slices (Club → Team → Player), each proven by extending one growing integration test (`ClubFlowIT`), mirroring `AuthFlowIT`. Frontend adds three pages reusing existing shadcn components and the dark theme already in place.

**Tech Stack:** Spring Boot 3.5 / Java 21 (existing `backend/api`), Spring Data JPA, Flyway, Testcontainers + `TestRestTemplate` for integration tests, React 19 + TanStack Query + React Router (existing `frontend`).

**Spec:** `docs/superpowers/specs/2026-08-14-club-module-design.md`

## Global Constraints

- No RBAC/`ClubMembership` — every route just requires a valid Bearer JWT (`SecurityConfig`'s existing `anyRequest().authenticated()` default), no role checks.
- `Team` has no `season_id` — not until the Match module introduces `Season`.
- No `StaffMember` — out of scope for this pass.
- No PUT/DELETE on any entity — creation + reads only.
- Each module's persistence stays private to that module — Club never queries `auth.*` tables directly, and vice versa (ADR-012/ADR-017). `created_by` on `Club` is a bare `UUID` column, no FK to `auth.users`.
- `club` module's `ApiExceptionHandler`/`ErrorResponse` are its own copies, not shared with `auth`'s (ADR-004 "duplicate before sharing" convention).

---

## Task 1: Club schema + Club entity/service/controller (create, get, list)

**Files:**
- Create: `backend/api/src/main/resources/db/migration/V2__create_club_tables.sql`
- Modify: `backend/api/src/main/resources/application.yml`
- Create: `backend/api/src/main/java/com/sporya/club/domain/Club.java`
- Create: `backend/api/src/main/java/com/sporya/club/domain/ClubNotFoundException.java`
- Create: `backend/api/src/main/java/com/sporya/club/infrastructure/persistence/ClubRepository.java`
- Create: `backend/api/src/main/java/com/sporya/club/controller/dto/ClubResponse.java`
- Create: `backend/api/src/main/java/com/sporya/club/controller/dto/CreateClubRequest.java`
- Create: `backend/api/src/main/java/com/sporya/club/controller/dto/ErrorResponse.java`
- Create: `backend/api/src/main/java/com/sporya/club/controller/ApiExceptionHandler.java`
- Create: `backend/api/src/main/java/com/sporya/club/application/ClubService.java`
- Create: `backend/api/src/main/java/com/sporya/club/controller/ClubController.java`
- Test: `backend/api/src/test/java/com/sporya/club/ClubFlowIT.java`

**Interfaces:**
- Consumes: `com.sporya.auth.controller.dto.{RegisterRequest,LoginRequest,AuthResponse}` (test only, to obtain a token — same pattern as `AuthFlowIT`).
- Produces: `ClubResponse(UUID id, String name, String country, UUID createdBy, Instant createdAt)`, `CreateClubRequest(String name, String country)`, `ClubService.create/list/get`, routes `POST/GET /api/v1/clubs`, `GET /api/v1/clubs/{clubId}`. Task 2 depends on the `club` schema existing (this task's migration) and on `ClubRepository`/`ClubNotFoundException` to check the parent club when creating a team.

- [ ] **Step 1: Create the migration for all three Club-module tables**

The three tables are created together (they're FK-linked) even though `Team`/`Player` entities arrive in later tasks — Flyway migrations represent one atomic schema change, splitting interdependent `CREATE TABLE`s across versions is unusual. Schema-qualify every statement explicitly: `spring.flyway.default-schema` stays `auth` (see Step 2), so an unqualified `CREATE TABLE clubs (...)` here would land in the wrong schema.

`backend/api/src/main/resources/db/migration/V2__create_club_tables.sql`:
```sql
CREATE TABLE club.clubs (
    id UUID PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    country VARCHAR(255) NOT NULL,
    created_by UUID NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE club.teams (
    id UUID PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    club_id UUID NOT NULL REFERENCES club.clubs(id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE club.players (
    id UUID PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    birthdate DATE NOT NULL,
    position VARCHAR(50) NOT NULL,
    team_id UUID NOT NULL REFERENCES club.teams(id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
```

- [ ] **Step 2: Add the `club` schema to Flyway's managed schemas**

In `backend/api/src/main/resources/application.yml`, find:
```yaml
  flyway:
    schemas: auth
    default-schema: auth
    create-schemas: true
```
Replace with:
```yaml
  flyway:
    schemas: auth,club
    default-schema: auth
    create-schemas: true
```
Leave `spring.jpa.properties.hibernate.default_schema: auth` untouched — new entities will declare `schema = "club"` explicitly on their own `@Table` (Step 3), so the global default stays correct for the existing `User` entity.

- [ ] **Step 3: Write the Club domain entity, exception, and repository**

`backend/api/src/main/java/com/sporya/club/domain/Club.java`:
```java
package com.sporya.club.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "clubs", schema = "club")
public class Club {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(nullable = false)
  private String name;

  @Column(nullable = false)
  private String country;

  @Column(name = "created_by", nullable = false)
  private UUID createdBy;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  protected Club() {}

  public Club(String name, String country, UUID createdBy) {
    this.name = name;
    this.country = country;
    this.createdBy = createdBy;
    this.createdAt = Instant.now();
  }

  public UUID getId() {
    return id;
  }

  public String getName() {
    return name;
  }

  public String getCountry() {
    return country;
  }

  public UUID getCreatedBy() {
    return createdBy;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }
}
```

`backend/api/src/main/java/com/sporya/club/domain/ClubNotFoundException.java`:
```java
package com.sporya.club.domain;

import java.util.UUID;

public class ClubNotFoundException extends RuntimeException {

  public ClubNotFoundException(UUID id) {
    super("Club not found: " + id);
  }
}
```

`backend/api/src/main/java/com/sporya/club/infrastructure/persistence/ClubRepository.java`:
```java
package com.sporya.club.infrastructure.persistence;

import com.sporya.club.domain.Club;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ClubRepository extends JpaRepository<Club, UUID> {}
```

- [ ] **Step 4: Write the DTOs and exception handler**

`backend/api/src/main/java/com/sporya/club/controller/dto/ClubResponse.java`:
```java
package com.sporya.club.controller.dto;

import com.sporya.club.domain.Club;
import java.time.Instant;
import java.util.UUID;

public record ClubResponse(UUID id, String name, String country, UUID createdBy, Instant createdAt) {

  public static ClubResponse from(Club club) {
    return new ClubResponse(
        club.getId(), club.getName(), club.getCountry(), club.getCreatedBy(), club.getCreatedAt());
  }
}
```

`backend/api/src/main/java/com/sporya/club/controller/dto/CreateClubRequest.java`:
```java
package com.sporya.club.controller.dto;

import jakarta.validation.constraints.NotBlank;

public record CreateClubRequest(@NotBlank String name, @NotBlank String country) {}
```

`backend/api/src/main/java/com/sporya/club/controller/dto/ErrorResponse.java`:
```java
package com.sporya.club.controller.dto;

public record ErrorResponse(String message) {}
```

`backend/api/src/main/java/com/sporya/club/controller/ApiExceptionHandler.java`:
```java
package com.sporya.club.controller;

import com.sporya.club.controller.dto.ErrorResponse;
import com.sporya.club.domain.ClubNotFoundException;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
class ApiExceptionHandler {

  @ExceptionHandler(ClubNotFoundException.class)
  ResponseEntity<ErrorResponse> handleClubNotFound(ClubNotFoundException ex) {
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

- [ ] **Step 5: Write the failing integration test**

`backend/api/src/test/java/com/sporya/club/ClubFlowIT.java`:
```java
package com.sporya.club;

import static org.assertj.core.api.Assertions.assertThat;

import com.sporya.auth.controller.dto.AuthResponse;
import com.sporya.auth.controller.dto.LoginRequest;
import com.sporya.auth.controller.dto.RegisterRequest;
import com.sporya.club.controller.dto.ClubResponse;
import com.sporya.club.controller.dto.CreateClubRequest;
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
            new HttpEntity<>(new CreateClubRequest("FC Sporya", "France"), authHeaders(accessToken)),
            ClubResponse.class);
    return response.getBody().id();
  }

  @Test
  void createClubThenGetThenList() {
    String accessToken = registerAndLogin();

    ResponseEntity<ClubResponse> createResponse =
        restTemplate.exchange(
            "/api/v1/clubs",
            HttpMethod.POST,
            new HttpEntity<>(new CreateClubRequest("FC Sporya", "France"), authHeaders(accessToken)),
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
}
```

Note the unused-looking `createClub` helper — it is used starting in Task 2, added now so Task 2's diff is a pure addition, not a mix of "add helper" + "use helper".

- [ ] **Step 6: Run the test to verify it fails**

Run: `cd backend/api && ./mvnw test -Dtest=ClubFlowIT`
Expected: FAIL — compilation error, `ClubService`/`ClubController` don't exist yet.

- [ ] **Step 7: Implement `ClubService`**

`backend/api/src/main/java/com/sporya/club/application/ClubService.java`:
```java
package com.sporya.club.application;

import com.sporya.club.controller.dto.ClubResponse;
import com.sporya.club.controller.dto.CreateClubRequest;
import com.sporya.club.domain.Club;
import com.sporya.club.domain.ClubNotFoundException;
import com.sporya.club.infrastructure.persistence.ClubRepository;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ClubService {

  private final ClubRepository clubRepository;

  public ClubService(ClubRepository clubRepository) {
    this.clubRepository = clubRepository;
  }

  @Transactional
  public ClubResponse create(UUID createdBy, CreateClubRequest request) {
    Club club = new Club(request.name(), request.country(), createdBy);
    return ClubResponse.from(clubRepository.save(club));
  }

  @Transactional(readOnly = true)
  public List<ClubResponse> list() {
    return clubRepository.findAll().stream().map(ClubResponse::from).toList();
  }

  @Transactional(readOnly = true)
  public ClubResponse get(UUID clubId) {
    Club club =
        clubRepository.findById(clubId).orElseThrow(() -> new ClubNotFoundException(clubId));
    return ClubResponse.from(club);
  }
}
```

- [ ] **Step 8: Implement `ClubController`**

`backend/api/src/main/java/com/sporya/club/controller/ClubController.java`:
```java
package com.sporya.club.controller;

import com.sporya.club.application.ClubService;
import com.sporya.club.controller.dto.ClubResponse;
import com.sporya.club.controller.dto.CreateClubRequest;
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
@RequestMapping("/api/v1/clubs")
public class ClubController {

  private final ClubService clubService;

  public ClubController(ClubService clubService) {
    this.clubService = clubService;
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  public ClubResponse create(
      @AuthenticationPrincipal UUID userId, @Valid @RequestBody CreateClubRequest request) {
    return clubService.create(userId, request);
  }

  @GetMapping
  public List<ClubResponse> list() {
    return clubService.list();
  }

  @GetMapping("/{clubId}")
  public ClubResponse get(@PathVariable UUID clubId) {
    return clubService.get(clubId);
  }
}
```

- [ ] **Step 9: Run the test to verify it passes**

Run: `cd backend/api && ./mvnw test -Dtest=ClubFlowIT`
Expected: PASS (3 tests: `createClubThenGetThenList`, `getUnknownClubReturns404`, `createClubWithoutAuthReturns401`).

- [ ] **Step 10: Commit**

```bash
git add backend/api/src/main/resources/db/migration/V2__create_club_tables.sql \
        backend/api/src/main/resources/application.yml \
        backend/api/src/main/java/com/sporya/club \
        backend/api/src/test/java/com/sporya/club
git commit -m "feat(club): add Club entity, create/get/list endpoints"
```

---

## Task 2: Team entity/service/controller (create, list by club, get)

**Files:**
- Create: `backend/api/src/main/java/com/sporya/club/domain/Team.java`
- Create: `backend/api/src/main/java/com/sporya/club/domain/TeamNotFoundException.java`
- Create: `backend/api/src/main/java/com/sporya/club/infrastructure/persistence/TeamRepository.java`
- Create: `backend/api/src/main/java/com/sporya/club/controller/dto/TeamResponse.java`
- Create: `backend/api/src/main/java/com/sporya/club/controller/dto/CreateTeamRequest.java`
- Modify: `backend/api/src/main/java/com/sporya/club/controller/ApiExceptionHandler.java`
- Create: `backend/api/src/main/java/com/sporya/club/application/TeamService.java`
- Create: `backend/api/src/main/java/com/sporya/club/controller/TeamController.java`
- Modify: `backend/api/src/test/java/com/sporya/club/ClubFlowIT.java`

**Interfaces:**
- Consumes: `ClubRepository.existsById` (Task 1) to validate the parent club before creating/listing a team.
- Produces: `TeamResponse(UUID id, String name, UUID clubId, Instant createdAt)`, `CreateTeamRequest(String name)`, routes `POST/GET /api/v1/clubs/{clubId}/teams`, `GET /api/v1/teams/{teamId}`. Task 3 depends on `TeamRepository`/`TeamNotFoundException` to validate the parent team when creating a player.

- [ ] **Step 1: Write the Team domain entity, exception, and repository**

`backend/api/src/main/java/com/sporya/club/domain/Team.java`:
```java
package com.sporya.club.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "teams", schema = "club")
public class Team {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(nullable = false)
  private String name;

  @Column(name = "club_id", nullable = false)
  private UUID clubId;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  protected Team() {}

  public Team(String name, UUID clubId) {
    this.name = name;
    this.clubId = clubId;
    this.createdAt = Instant.now();
  }

  public UUID getId() {
    return id;
  }

  public String getName() {
    return name;
  }

  public UUID getClubId() {
    return clubId;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }
}
```

`backend/api/src/main/java/com/sporya/club/domain/TeamNotFoundException.java`:
```java
package com.sporya.club.domain;

import java.util.UUID;

public class TeamNotFoundException extends RuntimeException {

  public TeamNotFoundException(UUID id) {
    super("Team not found: " + id);
  }
}
```

`backend/api/src/main/java/com/sporya/club/infrastructure/persistence/TeamRepository.java`:
```java
package com.sporya.club.infrastructure.persistence;

import com.sporya.club.domain.Team;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TeamRepository extends JpaRepository<Team, UUID> {

  List<Team> findByClubId(UUID clubId);
}
```

- [ ] **Step 2: Write the DTOs**

`backend/api/src/main/java/com/sporya/club/controller/dto/TeamResponse.java`:
```java
package com.sporya.club.controller.dto;

import com.sporya.club.domain.Team;
import java.time.Instant;
import java.util.UUID;

public record TeamResponse(UUID id, String name, UUID clubId, Instant createdAt) {

  public static TeamResponse from(Team team) {
    return new TeamResponse(team.getId(), team.getName(), team.getClubId(), team.getCreatedAt());
  }
}
```

`backend/api/src/main/java/com/sporya/club/controller/dto/CreateTeamRequest.java`:
```java
package com.sporya.club.controller.dto;

import jakarta.validation.constraints.NotBlank;

public record CreateTeamRequest(@NotBlank String name) {}
```

- [ ] **Step 3: Add the `TeamNotFoundException` handler**

In `backend/api/src/main/java/com/sporya/club/controller/ApiExceptionHandler.java`, add the import `com.sporya.club.domain.TeamNotFoundException` next to the `ClubNotFoundException` import, and add this handler method next to `handleClubNotFound`:
```java
  @ExceptionHandler(TeamNotFoundException.class)
  ResponseEntity<ErrorResponse> handleTeamNotFound(TeamNotFoundException ex) {
    return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ErrorResponse(ex.getMessage()));
  }
```

- [ ] **Step 4: Extend `ClubFlowIT` with failing team tests**

In `backend/api/src/test/java/com/sporya/club/ClubFlowIT.java`, add the import `com.sporya.club.controller.dto.TeamResponse` and `com.sporya.club.controller.dto.CreateTeamRequest`, then add these two test methods after `createClubWithoutAuthReturns401`:
```java
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
```

- [ ] **Step 5: Run the test to verify it fails**

Run: `cd backend/api && ./mvnw test -Dtest=ClubFlowIT`
Expected: FAIL — compilation error, `TeamService`/`TeamController` don't exist yet.

- [ ] **Step 6: Implement `TeamService`**

`backend/api/src/main/java/com/sporya/club/application/TeamService.java`:
```java
package com.sporya.club.application;

import com.sporya.club.controller.dto.CreateTeamRequest;
import com.sporya.club.controller.dto.TeamResponse;
import com.sporya.club.domain.ClubNotFoundException;
import com.sporya.club.domain.Team;
import com.sporya.club.domain.TeamNotFoundException;
import com.sporya.club.infrastructure.persistence.ClubRepository;
import com.sporya.club.infrastructure.persistence.TeamRepository;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TeamService {

  private final TeamRepository teamRepository;
  private final ClubRepository clubRepository;

  public TeamService(TeamRepository teamRepository, ClubRepository clubRepository) {
    this.teamRepository = teamRepository;
    this.clubRepository = clubRepository;
  }

  @Transactional
  public TeamResponse create(UUID clubId, CreateTeamRequest request) {
    if (!clubRepository.existsById(clubId)) {
      throw new ClubNotFoundException(clubId);
    }
    Team team = new Team(request.name(), clubId);
    return TeamResponse.from(teamRepository.save(team));
  }

  @Transactional(readOnly = true)
  public List<TeamResponse> listByClub(UUID clubId) {
    if (!clubRepository.existsById(clubId)) {
      throw new ClubNotFoundException(clubId);
    }
    return teamRepository.findByClubId(clubId).stream().map(TeamResponse::from).toList();
  }

  @Transactional(readOnly = true)
  public TeamResponse get(UUID teamId) {
    Team team =
        teamRepository.findById(teamId).orElseThrow(() -> new TeamNotFoundException(teamId));
    return TeamResponse.from(team);
  }
}
```

- [ ] **Step 7: Implement `TeamController`**

Routes are split across two path prefixes (`/api/v1/clubs/{clubId}/teams` for create/list, `/api/v1/teams/{teamId}` for get-by-id) — no class-level `@RequestMapping`, full paths on each method instead.

`backend/api/src/main/java/com/sporya/club/controller/TeamController.java`:
```java
package com.sporya.club.controller;

import com.sporya.club.application.TeamService;
import com.sporya.club.controller.dto.CreateTeamRequest;
import com.sporya.club.controller.dto.TeamResponse;
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
public class TeamController {

  private final TeamService teamService;

  public TeamController(TeamService teamService) {
    this.teamService = teamService;
  }

  @PostMapping("/api/v1/clubs/{clubId}/teams")
  @ResponseStatus(HttpStatus.CREATED)
  public TeamResponse create(
      @PathVariable UUID clubId, @Valid @RequestBody CreateTeamRequest request) {
    return teamService.create(clubId, request);
  }

  @GetMapping("/api/v1/clubs/{clubId}/teams")
  public List<TeamResponse> listByClub(@PathVariable UUID clubId) {
    return teamService.listByClub(clubId);
  }

  @GetMapping("/api/v1/teams/{teamId}")
  public TeamResponse get(@PathVariable UUID teamId) {
    return teamService.get(teamId);
  }
}
```

- [ ] **Step 8: Run the test to verify it passes**

Run: `cd backend/api && ./mvnw test -Dtest=ClubFlowIT`
Expected: PASS (5 tests total).

- [ ] **Step 9: Commit**

```bash
git add backend/api/src/main/java/com/sporya/club backend/api/src/test/java/com/sporya/club
git commit -m "feat(club): add Team entity, create/list/get endpoints"
```

---

## Task 3: Player entity/service/controller (create, list by team)

**Files:**
- Create: `backend/api/src/main/java/com/sporya/club/domain/Player.java`
- Create: `backend/api/src/main/java/com/sporya/club/infrastructure/persistence/PlayerRepository.java`
- Create: `backend/api/src/main/java/com/sporya/club/controller/dto/PlayerResponse.java`
- Create: `backend/api/src/main/java/com/sporya/club/controller/dto/CreatePlayerRequest.java`
- Create: `backend/api/src/main/java/com/sporya/club/application/PlayerService.java`
- Create: `backend/api/src/main/java/com/sporya/club/controller/PlayerController.java`
- Modify: `backend/api/src/test/java/com/sporya/club/ClubFlowIT.java`

**Interfaces:**
- Consumes: `TeamRepository.existsById`, `TeamNotFoundException` (Task 2) to validate the parent team.
- Produces: `PlayerResponse(UUID id, String name, LocalDate birthdate, String position, UUID teamId, Instant createdAt)`, routes `POST/GET /api/v1/teams/{teamId}/players`. Nothing later in this plan depends on this task — it's the last backend slice.

- [ ] **Step 1: Write the Player domain entity and repository**

`backend/api/src/main/java/com/sporya/club/domain/Player.java`:
```java
package com.sporya.club.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "players", schema = "club")
public class Player {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(nullable = false)
  private String name;

  @Column(nullable = false)
  private LocalDate birthdate;

  @Column(nullable = false)
  private String position;

  @Column(name = "team_id", nullable = false)
  private UUID teamId;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  protected Player() {}

  public Player(String name, LocalDate birthdate, String position, UUID teamId) {
    this.name = name;
    this.birthdate = birthdate;
    this.position = position;
    this.teamId = teamId;
    this.createdAt = Instant.now();
  }

  public UUID getId() {
    return id;
  }

  public String getName() {
    return name;
  }

  public LocalDate getBirthdate() {
    return birthdate;
  }

  public String getPosition() {
    return position;
  }

  public UUID getTeamId() {
    return teamId;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }
}
```

`backend/api/src/main/java/com/sporya/club/infrastructure/persistence/PlayerRepository.java`:
```java
package com.sporya.club.infrastructure.persistence;

import com.sporya.club.domain.Player;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PlayerRepository extends JpaRepository<Player, UUID> {

  List<Player> findByTeamId(UUID teamId);
}
```

- [ ] **Step 2: Write the DTOs**

`backend/api/src/main/java/com/sporya/club/controller/dto/PlayerResponse.java`:
```java
package com.sporya.club.controller.dto;

import com.sporya.club.domain.Player;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record PlayerResponse(
    UUID id, String name, LocalDate birthdate, String position, UUID teamId, Instant createdAt) {

  public static PlayerResponse from(Player player) {
    return new PlayerResponse(
        player.getId(),
        player.getName(),
        player.getBirthdate(),
        player.getPosition(),
        player.getTeamId(),
        player.getCreatedAt());
  }
}
```

`backend/api/src/main/java/com/sporya/club/controller/dto/CreatePlayerRequest.java`:
```java
package com.sporya.club.controller.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;

public record CreatePlayerRequest(
    @NotBlank String name, @NotNull LocalDate birthdate, @NotBlank String position) {}
```

- [ ] **Step 3: Extend `ClubFlowIT` with failing player tests**

In `backend/api/src/test/java/com/sporya/club/ClubFlowIT.java`, add the imports `com.sporya.club.controller.dto.PlayerResponse`, `com.sporya.club.controller.dto.CreatePlayerRequest`, and `java.time.LocalDate`. Add a `createTeam` helper next to `createClub` (same pattern — used by the new tests):
```java
  private UUID createTeam(String accessToken, UUID clubId) {
    ResponseEntity<TeamResponse> response =
        restTemplate.exchange(
            "/api/v1/clubs/" + clubId + "/teams",
            HttpMethod.POST,
            new HttpEntity<>(new CreateTeamRequest("U19"), authHeaders(accessToken)),
            TeamResponse.class);
    return response.getBody().id();
  }
```
Then add these two test methods after `createTeamUnderUnknownClubReturns404`:
```java
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
```

- [ ] **Step 4: Run the test to verify it fails**

Run: `cd backend/api && ./mvnw test -Dtest=ClubFlowIT`
Expected: FAIL — compilation error, `PlayerService`/`PlayerController` don't exist yet.

- [ ] **Step 5: Implement `PlayerService`**

`backend/api/src/main/java/com/sporya/club/application/PlayerService.java`:
```java
package com.sporya.club.application;

import com.sporya.club.controller.dto.CreatePlayerRequest;
import com.sporya.club.controller.dto.PlayerResponse;
import com.sporya.club.domain.Player;
import com.sporya.club.domain.TeamNotFoundException;
import com.sporya.club.infrastructure.persistence.PlayerRepository;
import com.sporya.club.infrastructure.persistence.TeamRepository;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PlayerService {

  private final PlayerRepository playerRepository;
  private final TeamRepository teamRepository;

  public PlayerService(PlayerRepository playerRepository, TeamRepository teamRepository) {
    this.playerRepository = playerRepository;
    this.teamRepository = teamRepository;
  }

  @Transactional
  public PlayerResponse create(UUID teamId, CreatePlayerRequest request) {
    if (!teamRepository.existsById(teamId)) {
      throw new TeamNotFoundException(teamId);
    }
    Player player = new Player(request.name(), request.birthdate(), request.position(), teamId);
    return PlayerResponse.from(playerRepository.save(player));
  }

  @Transactional(readOnly = true)
  public List<PlayerResponse> listByTeam(UUID teamId) {
    if (!teamRepository.existsById(teamId)) {
      throw new TeamNotFoundException(teamId);
    }
    return playerRepository.findByTeamId(teamId).stream().map(PlayerResponse::from).toList();
  }
}
```

- [ ] **Step 6: Implement `PlayerController`**

`backend/api/src/main/java/com/sporya/club/controller/PlayerController.java`:
```java
package com.sporya.club.controller;

import com.sporya.club.application.PlayerService;
import com.sporya.club.controller.dto.CreatePlayerRequest;
import com.sporya.club.controller.dto.PlayerResponse;
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
@RequestMapping("/api/v1/teams/{teamId}/players")
public class PlayerController {

  private final PlayerService playerService;

  public PlayerController(PlayerService playerService) {
    this.playerService = playerService;
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  public PlayerResponse create(
      @PathVariable UUID teamId, @Valid @RequestBody CreatePlayerRequest request) {
    return playerService.create(teamId, request);
  }

  @GetMapping
  public List<PlayerResponse> list(@PathVariable UUID teamId) {
    return playerService.listByTeam(teamId);
  }
}
```

- [ ] **Step 7: Run the full backend test suite**

Run: `cd backend/api && ./mvnw verify`
Expected: PASS — all of `AuthModuleSmokeTest`, `AuthFlowIT`, `ClubFlowIT` (7 tests) green. `verify` (not just `test`) also runs spotless/dependency-check same as CI (`.github/workflows/service-ci.yml`).

- [ ] **Step 8: Commit**

```bash
git add backend/api/src/main/java/com/sporya/club backend/api/src/test/java/com/sporya/club
git commit -m "feat(club): add Player entity, create/list endpoints"
```

---

## Task 4: Frontend API client — generalize `request()`, add Club/Team/Player functions

**Files:**
- Modify: `frontend/src/lib/api.ts`

**Interfaces:**
- Consumes: nothing new (this task only touches the fetch wrapper).
- Produces: `listClubs`, `createClub`, `getClub`, `listTeams`, `createTeam`, `getTeam`, `listPlayers`, `createPlayer` — all `(accessToken: string, ...) => Promise<T>`, and the interfaces `ClubResponse`, `TeamResponse`, `PlayerResponse`. Tasks 5–7 (the three new pages) import these directly.

- [ ] **Step 1: Generalize `request()` to take a full path, update existing auth functions**

Replace the full contents of `frontend/src/lib/api.ts`:
```ts
// Chemin relatif — même valeur en local (proxy Vite, voir vite.config.ts) et
// en prod (routage Ingress, voir infrastructure/kubernetes/ingress/). Pas de
// configuration d'URL de base ni de CORS à gérer.

export class ApiError extends Error {
  status: number

  constructor(message: string, status: number) {
    super(message)
    this.status = status
  }
}

async function parseErrorMessage(response: Response): Promise<string> {
  try {
    const body = (await response.json()) as { message?: string }
    return body.message ?? response.statusText
  } catch {
    return response.statusText
  }
}

async function request<T>(path: string, init?: RequestInit): Promise<T> {
  const response = await fetch(path, {
    ...init,
    headers: {
      'Content-Type': 'application/json',
      ...init?.headers,
    },
  })
  if (!response.ok) {
    throw new ApiError(await parseErrorMessage(response), response.status)
  }
  return (await response.json()) as T
}

function authHeaders(accessToken: string): HeadersInit {
  return { Authorization: `Bearer ${accessToken}` }
}

export interface UserResponse {
  id: string
  email: string
}

export interface AuthResponse {
  accessToken: string
  tokenType: string
  expiresInSeconds: number
}

export function register(email: string, password: string): Promise<UserResponse> {
  return request<UserResponse>('/api/v1/auth/register', {
    method: 'POST',
    body: JSON.stringify({ email, password }),
  })
}

export function login(email: string, password: string): Promise<AuthResponse> {
  return request<AuthResponse>('/api/v1/auth/login', {
    method: 'POST',
    body: JSON.stringify({ email, password }),
  })
}

export function me(accessToken: string): Promise<UserResponse> {
  return request<UserResponse>('/api/v1/auth/me', { headers: authHeaders(accessToken) })
}

export interface ClubResponse {
  id: string
  name: string
  country: string
  createdBy: string
  createdAt: string
}

export interface TeamResponse {
  id: string
  name: string
  clubId: string
  createdAt: string
}

export interface PlayerResponse {
  id: string
  name: string
  birthdate: string
  position: string
  teamId: string
  createdAt: string
}

export function listClubs(accessToken: string): Promise<ClubResponse[]> {
  return request<ClubResponse[]>('/api/v1/clubs', { headers: authHeaders(accessToken) })
}

export function createClub(
  accessToken: string,
  name: string,
  country: string,
): Promise<ClubResponse> {
  return request<ClubResponse>('/api/v1/clubs', {
    method: 'POST',
    headers: authHeaders(accessToken),
    body: JSON.stringify({ name, country }),
  })
}

export function getClub(accessToken: string, clubId: string): Promise<ClubResponse> {
  return request<ClubResponse>(`/api/v1/clubs/${clubId}`, { headers: authHeaders(accessToken) })
}

export function listTeams(accessToken: string, clubId: string): Promise<TeamResponse[]> {
  return request<TeamResponse[]>(`/api/v1/clubs/${clubId}/teams`, {
    headers: authHeaders(accessToken),
  })
}

export function createTeam(
  accessToken: string,
  clubId: string,
  name: string,
): Promise<TeamResponse> {
  return request<TeamResponse>(`/api/v1/clubs/${clubId}/teams`, {
    method: 'POST',
    headers: authHeaders(accessToken),
    body: JSON.stringify({ name }),
  })
}

export function getTeam(accessToken: string, teamId: string): Promise<TeamResponse> {
  return request<TeamResponse>(`/api/v1/teams/${teamId}`, { headers: authHeaders(accessToken) })
}

export function listPlayers(accessToken: string, teamId: string): Promise<PlayerResponse[]> {
  return request<PlayerResponse[]>(`/api/v1/teams/${teamId}/players`, {
    headers: authHeaders(accessToken),
  })
}

export function createPlayer(
  accessToken: string,
  teamId: string,
  name: string,
  birthdate: string,
  position: string,
): Promise<PlayerResponse> {
  return request<PlayerResponse>(`/api/v1/teams/${teamId}/players`, {
    method: 'POST',
    headers: authHeaders(accessToken),
    body: JSON.stringify({ name, birthdate, position }),
  })
}
```

- [ ] **Step 2: Verify it builds**

Run: `cd frontend && npm run lint && npm run build`
Expected: both succeed (0 errors). `LoginPage.tsx`/`RegisterPage.tsx`/`DashboardPage.tsx` still compile since `register`/`login`/`me` keep the same signatures, only their internals changed.

- [ ] **Step 3: Commit**

```bash
git add frontend/src/lib/api.ts
git commit -m "refactor(frontend): generalize request() path, add Club/Team/Player API client"
```

---

## Task 5: `ClubsPage` — list clubs, create club, link from Dashboard

**Files:**
- Create: `frontend/src/pages/ClubsPage.tsx`
- Modify: `frontend/src/App.tsx`
- Modify: `frontend/src/pages/DashboardPage.tsx`

**Interfaces:**
- Consumes: `listClubs`, `createClub`, `ClubResponse` (Task 4); `ProtectedRoute` (existing, `frontend/src/components/ProtectedRoute.tsx`); `useAuth` (existing, `frontend/src/lib/auth-context.tsx`).
- Produces: route `/clubs`. Task 6 (`ClubDetailPage`) is linked from here (`/clubs/:clubId`).

- [ ] **Step 1: Create `ClubsPage`**

`frontend/src/pages/ClubsPage.tsx`:
```tsx
import { useState, type FormEvent } from 'react'
import { Link } from 'react-router-dom'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'

import { createClub, listClubs } from '@/lib/api'
import { useAuth } from '@/lib/auth-context'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'

export function ClubsPage() {
  const { accessToken } = useAuth()
  const queryClient = useQueryClient()
  const [name, setName] = useState('')
  const [country, setCountry] = useState('')

  const clubsQuery = useQuery({
    queryKey: ['clubs', accessToken],
    queryFn: () => listClubs(accessToken as string),
    enabled: Boolean(accessToken),
  })

  const createMutation = useMutation({
    mutationFn: () => createClub(accessToken as string, name, country),
    onSuccess: () => {
      setName('')
      setCountry('')
      queryClient.invalidateQueries({ queryKey: ['clubs'] })
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
          <CardTitle>Clubs</CardTitle>
          <CardDescription>Liste des clubs existants.</CardDescription>
        </CardHeader>
        <CardContent className="flex flex-col gap-2">
          {clubsQuery.isLoading && <p className="text-sm text-muted-foreground">Chargement…</p>}
          {clubsQuery.data?.length === 0 && (
            <p className="text-sm text-muted-foreground">Aucun club pour l'instant.</p>
          )}
          {clubsQuery.data?.map((club) => (
            <Link
              key={club.id}
              to={`/clubs/${club.id}`}
              className="rounded-md border border-border px-3 py-2 text-sm hover:bg-accent"
            >
              {club.name} — {club.country}
            </Link>
          ))}
        </CardContent>
      </Card>

      <Card>
        <CardHeader>
          <CardTitle>Créer un club</CardTitle>
        </CardHeader>
        <CardContent>
          <form onSubmit={handleSubmit} className="flex flex-col gap-4">
            <div className="flex flex-col gap-2">
              <Label htmlFor="name">Nom</Label>
              <Input id="name" required value={name} onChange={(e) => setName(e.target.value)} />
            </div>
            <div className="flex flex-col gap-2">
              <Label htmlFor="country">Pays</Label>
              <Input
                id="country"
                required
                value={country}
                onChange={(e) => setCountry(e.target.value)}
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

- [ ] **Step 2: Add the `/clubs` route**

In `frontend/src/App.tsx`, add the import `import { ClubsPage } from '@/pages/ClubsPage'` next to the other page imports, and add this route inside `<Routes>`, after the `/dashboard` route:
```tsx
      <Route
        path="/clubs"
        element={
          <ProtectedRoute>
            <ClubsPage />
          </ProtectedRoute>
        }
      />
```

- [ ] **Step 3: Link to `/clubs` from the Dashboard**

In `frontend/src/pages/DashboardPage.tsx`, add the import `import { Link } from 'react-router-dom'` next to the existing `useNavigate` import (they're both from `react-router-dom`, combine into one import line). Add this button right before the existing "Se déconnecter" button (inside `CardContent`, same `flex flex-col gap-4`):
```tsx
          <Button asChild variant="secondary">
            <Link to="/clubs">Voir les clubs</Link>
          </Button>
```

- [ ] **Step 4: Verify it builds**

Run: `cd frontend && npm run lint && npm run build`
Expected: both succeed.

- [ ] **Step 5: Commit**

```bash
git add frontend/src/pages/ClubsPage.tsx frontend/src/App.tsx frontend/src/pages/DashboardPage.tsx
git commit -m "feat(frontend): add ClubsPage (list + create), link from Dashboard"
```

---

## Task 6: `ClubDetailPage` — club info, list/create teams

**Files:**
- Create: `frontend/src/pages/ClubDetailPage.tsx`
- Modify: `frontend/src/App.tsx`

**Interfaces:**
- Consumes: `getClub`, `listTeams`, `createTeam`, `ClubResponse`, `TeamResponse` (Task 4).
- Produces: route `/clubs/:clubId`. Task 7 (`TeamDetailPage`) is linked from here (`/teams/:teamId`).

- [ ] **Step 1: Create `ClubDetailPage`**

`frontend/src/pages/ClubDetailPage.tsx`:
```tsx
import { useState, type FormEvent } from 'react'
import { Link, useParams } from 'react-router-dom'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'

import { createTeam, getClub, listTeams } from '@/lib/api'
import { useAuth } from '@/lib/auth-context'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'

export function ClubDetailPage() {
  const { clubId } = useParams<{ clubId: string }>()
  const { accessToken } = useAuth()
  const queryClient = useQueryClient()
  const [name, setName] = useState('')

  const clubQuery = useQuery({
    queryKey: ['club', clubId],
    queryFn: () => getClub(accessToken as string, clubId as string),
    enabled: Boolean(accessToken && clubId),
  })

  const teamsQuery = useQuery({
    queryKey: ['teams', clubId],
    queryFn: () => listTeams(accessToken as string, clubId as string),
    enabled: Boolean(accessToken && clubId),
  })

  const createMutation = useMutation({
    mutationFn: () => createTeam(accessToken as string, clubId as string, name),
    onSuccess: () => {
      setName('')
      queryClient.invalidateQueries({ queryKey: ['teams', clubId] })
    },
  })

  function handleSubmit(event: FormEvent) {
    event.preventDefault()
    createMutation.mutate()
  }

  return (
    <div className="mx-auto flex min-h-svh max-w-2xl flex-col gap-6 p-6">
      <Link to="/clubs" className="text-sm text-muted-foreground underline underline-offset-4">
        ← Retour aux clubs
      </Link>

      <Card>
        <CardHeader>
          <CardTitle>{clubQuery.data?.name ?? 'Club'}</CardTitle>
          <CardDescription>{clubQuery.data?.country}</CardDescription>
        </CardHeader>
        <CardContent className="flex flex-col gap-2">
          {teamsQuery.isLoading && <p className="text-sm text-muted-foreground">Chargement…</p>}
          {teamsQuery.data?.length === 0 && (
            <p className="text-sm text-muted-foreground">Aucune équipe pour l'instant.</p>
          )}
          {teamsQuery.data?.map((team) => (
            <Link
              key={team.id}
              to={`/teams/${team.id}`}
              className="rounded-md border border-border px-3 py-2 text-sm hover:bg-accent"
            >
              {team.name}
            </Link>
          ))}
        </CardContent>
      </Card>

      <Card>
        <CardHeader>
          <CardTitle>Créer une équipe</CardTitle>
        </CardHeader>
        <CardContent>
          <form onSubmit={handleSubmit} className="flex flex-col gap-4">
            <div className="flex flex-col gap-2">
              <Label htmlFor="team-name">Nom</Label>
              <Input
                id="team-name"
                required
                value={name}
                onChange={(e) => setName(e.target.value)}
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

- [ ] **Step 2: Add the `/clubs/:clubId` route**

In `frontend/src/App.tsx`, add the import `import { ClubDetailPage } from '@/pages/ClubDetailPage'`, and this route after `/clubs`:
```tsx
      <Route
        path="/clubs/:clubId"
        element={
          <ProtectedRoute>
            <ClubDetailPage />
          </ProtectedRoute>
        }
      />
```

- [ ] **Step 3: Verify it builds**

Run: `cd frontend && npm run lint && npm run build`
Expected: both succeed.

- [ ] **Step 4: Commit**

```bash
git add frontend/src/pages/ClubDetailPage.tsx frontend/src/App.tsx
git commit -m "feat(frontend): add ClubDetailPage (club info, list/create teams)"
```

---

## Task 7: `TeamDetailPage` — team info, list/add players

**Files:**
- Create: `frontend/src/pages/TeamDetailPage.tsx`
- Modify: `frontend/src/App.tsx`

**Interfaces:**
- Consumes: `getTeam`, `listPlayers`, `createPlayer`, `TeamResponse`, `PlayerResponse` (Task 4).
- Produces: route `/teams/:teamId`. Last page in this plan — nothing downstream depends on it.

- [ ] **Step 1: Create `TeamDetailPage`**

`frontend/src/pages/TeamDetailPage.tsx`:
```tsx
import { useState, type FormEvent } from 'react'
import { Link, useParams } from 'react-router-dom'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'

import { createPlayer, getTeam, listPlayers } from '@/lib/api'
import { useAuth } from '@/lib/auth-context'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'

export function TeamDetailPage() {
  const { teamId } = useParams<{ teamId: string }>()
  const { accessToken } = useAuth()
  const queryClient = useQueryClient()
  const [name, setName] = useState('')
  const [birthdate, setBirthdate] = useState('')
  const [position, setPosition] = useState('')

  const teamQuery = useQuery({
    queryKey: ['team', teamId],
    queryFn: () => getTeam(accessToken as string, teamId as string),
    enabled: Boolean(accessToken && teamId),
  })

  const playersQuery = useQuery({
    queryKey: ['players', teamId],
    queryFn: () => listPlayers(accessToken as string, teamId as string),
    enabled: Boolean(accessToken && teamId),
  })

  const createMutation = useMutation({
    mutationFn: () =>
      createPlayer(accessToken as string, teamId as string, name, birthdate, position),
    onSuccess: () => {
      setName('')
      setBirthdate('')
      setPosition('')
      queryClient.invalidateQueries({ queryKey: ['players', teamId] })
    },
  })

  function handleSubmit(event: FormEvent) {
    event.preventDefault()
    createMutation.mutate()
  }

  return (
    <div className="mx-auto flex min-h-svh max-w-2xl flex-col gap-6 p-6">
      <Link
        to={teamQuery.data ? `/clubs/${teamQuery.data.clubId}` : '/clubs'}
        className="text-sm text-muted-foreground underline underline-offset-4"
      >
        ← Retour au club
      </Link>

      <Card>
        <CardHeader>
          <CardTitle>{teamQuery.data?.name ?? 'Équipe'}</CardTitle>
          <CardDescription>Effectif</CardDescription>
        </CardHeader>
        <CardContent className="flex flex-col gap-2">
          {playersQuery.isLoading && <p className="text-sm text-muted-foreground">Chargement…</p>}
          {playersQuery.data?.length === 0 && (
            <p className="text-sm text-muted-foreground">Aucun joueur pour l'instant.</p>
          )}
          {playersQuery.data?.map((player) => (
            <div key={player.id} className="rounded-md border border-border px-3 py-2 text-sm">
              {player.name} — {player.position} ({player.birthdate})
            </div>
          ))}
        </CardContent>
      </Card>

      <Card>
        <CardHeader>
          <CardTitle>Ajouter un joueur</CardTitle>
        </CardHeader>
        <CardContent>
          <form onSubmit={handleSubmit} className="flex flex-col gap-4">
            <div className="flex flex-col gap-2">
              <Label htmlFor="player-name">Nom</Label>
              <Input
                id="player-name"
                required
                value={name}
                onChange={(e) => setName(e.target.value)}
              />
            </div>
            <div className="flex flex-col gap-2">
              <Label htmlFor="player-birthdate">Date de naissance</Label>
              <Input
                id="player-birthdate"
                type="date"
                required
                value={birthdate}
                onChange={(e) => setBirthdate(e.target.value)}
              />
            </div>
            <div className="flex flex-col gap-2">
              <Label htmlFor="player-position">Poste</Label>
              <Input
                id="player-position"
                required
                value={position}
                onChange={(e) => setPosition(e.target.value)}
              />
            </div>
            <Button type="submit" disabled={createMutation.isPending}>
              {createMutation.isPending ? 'Ajout…' : 'Ajouter'}
            </Button>
          </form>
        </CardContent>
      </Card>
    </div>
  )
}
```

- [ ] **Step 2: Add the `/teams/:teamId` route**

In `frontend/src/App.tsx`, add the import `import { TeamDetailPage } from '@/pages/TeamDetailPage'`, and this route after `/clubs/:clubId`:
```tsx
      <Route
        path="/teams/:teamId"
        element={
          <ProtectedRoute>
            <TeamDetailPage />
          </ProtectedRoute>
        }
      />
```

- [ ] **Step 3: Verify it builds**

Run: `cd frontend && npm run lint && npm run build`
Expected: both succeed.

- [ ] **Step 4: Commit**

```bash
git add frontend/src/pages/TeamDetailPage.tsx frontend/src/App.tsx
git commit -m "feat(frontend): add TeamDetailPage (team info, list/add players)"
```

---

## Manual verification (after Task 7)

Automated checks cover compilation and the backend flow; the actual UI needs a human look:

```bash
cd frontend && npm run dev
```

Then, logged in, walk through: Dashboard → "Voir les clubs" → create a club → open it → create a team → open it → add a player → confirm it appears in the list. This step is yours to run — no browser automation.

## Self-review notes

- **Spec coverage**: all 7 endpoints from the spec table are implemented (Tasks 1–3); all 3 frontend pages + Dashboard link are implemented (Tasks 5–7); the `api.ts` refactor is Task 4. `created_by`, `flyway.schemas`, schema-qualified migration, and the "no RBAC / no Season / no StaffMember" constraints are all carried through.
- **Placeholder scan**: none — every step has real code, no TBD/TODO.
- **Type consistency**: `ClubResponse`/`TeamResponse`/`PlayerResponse` field names and types match exactly between backend DTOs (Tasks 1–3) and frontend interfaces (Task 4); `getTeam`/`listTeams`/`listPlayers`/etc. signatures used in Tasks 5–7 match their Task 4 definitions.
