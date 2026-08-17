# Notification Module Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build the Notification module (`com.sporya.notification`) — a `Notification` row per club member when a match finishes (populated via the same in-process `MatchFinishedEvent` Statistics already listens to), plus endpoints to list the caller's notifications and mark one read.

**Architecture:** Same layered structure as the other modules (`controller/application/domain/infrastructure`), new Postgres schema `notification`. A `@TransactionalEventListener(phase = AFTER_COMMIT)` listener resolves the two clubs via `TeamService` (Club), the recipient set via `MembershipService.listForClub` (Auth, already used by `ClubMemberService`), and the final score via `MatchEventService.listForMatch` (Match) — the same score-counting duplication already accepted in Statistics (ADR-004). No Redis, no WebSocket — plain Postgres, read on demand. Built as two vertical slices (persistence + listener → read/mark-read endpoints), each proven by extending one growing integration test (`NotificationFlowIT`), mirroring `StatisticsFlowIT`.

**Tech Stack:** Spring Boot 3.5 / Java 21 (existing `backend/api`), Spring Data JPA, Flyway, Spring's `ApplicationEventPublisher`/`@TransactionalEventListener` (already used by Statistics), Testcontainers + `TestRestTemplate`.

**Spec:** `docs/superpowers/specs/2026-08-17-notification-module-design.md`

## Global Constraints

- No Redis, no WebSocket, no push — `GET /api/v1/notifications` is pull-based, the frontend refreshes manually if it wants to.
- Recipients: every `ClubMembership` (any role) of the home club **and** the away club — deduplicated by `userId`, a member of both clubs gets exactly one notification.
- No user preferences/opt-out in this increment — every member always gets notified.
- Single trigger in this increment: `MatchFinishedEvent` (already published by `Match.finish()`, already consumed by Statistics). No live `GOAL_SCORED` notifications.
- `Notification` keeps a `type` field (`NotificationType.MATCH_FINISHED`, the only value for now) and match-specific fields directly on the row (`matchId`, `homeTeamId`, `awayTeamId`, `homeScore`, `awayScore`) — no generic JSON payload, same "specialize first, generalize later" convention as the rest of this codebase.
- `read` is a boolean on the row, settable only by the notification's own recipient — marking someone else's (or a nonexistent) notification returns 404, indistinguishable on purpose.
- **Known gotcha (already hit and documented in the Statistics plan): `@TransactionalEventListener(phase = AFTER_COMMIT)` alone does not reliably commit writes.** The listener method also needs `@Transactional(propagation = Propagation.REQUIRES_NEW)` — both annotations together, every time.
- Migration file: `backend/api/src/main/resources/db/migration/V6__create_notification_tables.sql`, flat folder, `CREATE SCHEMA IF NOT EXISTS notification;` explicit as the first statement (same convention as `V4`/`V5`).

---

## Task 1: Notification persistence + `MatchFinishedNotifier`

**Files:**
- Create: `backend/api/src/main/resources/db/migration/V6__create_notification_tables.sql`
- Modify: `backend/api/src/main/resources/application.yml`
- Create: `backend/api/src/main/java/com/sporya/notification/domain/NotificationType.java`
- Create: `backend/api/src/main/java/com/sporya/notification/domain/Notification.java`
- Create: `backend/api/src/main/java/com/sporya/notification/infrastructure/persistence/NotificationRepository.java`
- Create: `backend/api/src/main/java/com/sporya/notification/application/MatchFinishedNotifier.java`
- Test: `backend/api/src/test/java/com/sporya/notification/NotificationFlowIT.java`

**Interfaces:**
- Consumes: `com.sporya.match.domain.MatchFinishedEvent` (existing), `com.sporya.club.application.TeamService.get(UUID): TeamResponse` (existing), `com.sporya.auth.application.MembershipService.listForClub(UUID): List<ClubMembership>` (existing, `ClubMembership.getUserId()`), `com.sporya.match.application.MatchEventService.listForMatch(UUID): List<MatchEventResponse>` (existing).
- Produces: `NotificationType` enum, `Notification` entity (`getId/getUserId/getType/getMatchId/getHomeTeamId/getAwayTeamId/getHomeScore/getAwayScore/isRead/markRead/getCreatedAt`), `NotificationRepository.findByUserIdOrderByCreatedAtDesc(UUID): List<Notification>` + `.findByIdAndUserId(UUID, UUID): Optional<Notification>`. Task 2 depends on both repository methods and the entity's getters/`markRead()`.

- [ ] **Step 1: Create the migration**

`backend/api/src/main/resources/db/migration/V6__create_notification_tables.sql`:
```sql
CREATE SCHEMA IF NOT EXISTS notification;

CREATE TABLE notification.notifications (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL,
    type VARCHAR(30) NOT NULL,
    match_id UUID NOT NULL,
    home_team_id UUID NOT NULL,
    away_team_id UUID NOT NULL,
    home_score INT NOT NULL,
    away_score INT NOT NULL,
    read BOOLEAN NOT NULL DEFAULT false,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
```

- [ ] **Step 2: Add the `notification` schema to Flyway's managed schemas**

In `backend/api/src/main/resources/application.yml`, find:
```yaml
  flyway:
    schemas: auth,club,match,statistics
    default-schema: auth
    create-schemas: true
```
Replace with:
```yaml
  flyway:
    schemas: auth,club,match,statistics,notification
    default-schema: auth
    create-schemas: true
```

- [ ] **Step 3: Write the domain**

`backend/api/src/main/java/com/sporya/notification/domain/NotificationType.java`:
```java
package com.sporya.notification.domain;

public enum NotificationType {
  MATCH_FINISHED
}
```

`backend/api/src/main/java/com/sporya/notification/domain/Notification.java`:
```java
package com.sporya.notification.domain;

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
@Table(name = "notifications", schema = "notification")
public class Notification {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(name = "user_id", nullable = false)
  private UUID userId;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 30)
  private NotificationType type;

  @Column(name = "match_id", nullable = false)
  private UUID matchId;

  @Column(name = "home_team_id", nullable = false)
  private UUID homeTeamId;

  @Column(name = "away_team_id", nullable = false)
  private UUID awayTeamId;

  @Column(name = "home_score", nullable = false)
  private int homeScore;

  @Column(name = "away_score", nullable = false)
  private int awayScore;

  @Column(nullable = false)
  private boolean read;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  protected Notification() {}

  public Notification(
      UUID userId,
      NotificationType type,
      UUID matchId,
      UUID homeTeamId,
      UUID awayTeamId,
      int homeScore,
      int awayScore) {
    this.userId = userId;
    this.type = type;
    this.matchId = matchId;
    this.homeTeamId = homeTeamId;
    this.awayTeamId = awayTeamId;
    this.homeScore = homeScore;
    this.awayScore = awayScore;
    this.read = false;
    this.createdAt = Instant.now();
  }

  public void markRead() {
    this.read = true;
  }

  public UUID getId() {
    return id;
  }

  public UUID getUserId() {
    return userId;
  }

  public NotificationType getType() {
    return type;
  }

  public UUID getMatchId() {
    return matchId;
  }

  public UUID getHomeTeamId() {
    return homeTeamId;
  }

  public UUID getAwayTeamId() {
    return awayTeamId;
  }

  public int getHomeScore() {
    return homeScore;
  }

  public int getAwayScore() {
    return awayScore;
  }

  public boolean isRead() {
    return read;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }
}
```

- [ ] **Step 4: Write `NotificationRepository`**

`backend/api/src/main/java/com/sporya/notification/infrastructure/persistence/NotificationRepository.java`:
```java
package com.sporya.notification.infrastructure.persistence;

import com.sporya.notification.domain.Notification;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NotificationRepository extends JpaRepository<Notification, UUID> {

  List<Notification> findByUserIdOrderByCreatedAtDesc(UUID userId);

  Optional<Notification> findByIdAndUserId(UUID id, UUID userId);
}
```

- [ ] **Step 5: Write the failing test**

`backend/api/src/test/java/com/sporya/notification/NotificationFlowIT.java`:
```java
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

/** Preuve de bout en bout du module Notification : notifications déclenchées par la fin d'un match. */
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
```

- [ ] **Step 6: Run the test to verify it fails**

Run: `cd backend/api && ./mvnw test -Dtest=NotificationFlowIT`
Expected: FAIL — `finishingAMatchNotifiesMembersOfBothClubs`/`aMemberOfBothClubsReceivesOneNotification` fail because no listener exists yet to create rows.

- [ ] **Step 7: Implement `MatchFinishedNotifier`**

`backend/api/src/main/java/com/sporya/notification/application/MatchFinishedNotifier.java`:
```java
package com.sporya.notification.application;

import com.sporya.auth.application.MembershipService;
import com.sporya.club.application.TeamService;
import com.sporya.match.application.MatchEventService;
import com.sporya.match.controller.dto.MatchEventResponse;
import com.sporya.match.domain.MatchEventType;
import com.sporya.match.domain.MatchFinishedEvent;
import com.sporya.notification.domain.Notification;
import com.sporya.notification.domain.NotificationType;
import com.sporya.notification.infrastructure.persistence.NotificationRepository;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class MatchFinishedNotifier {

  private final TeamService teamService;
  private final MembershipService membershipService;
  private final MatchEventService matchEventService;
  private final NotificationRepository notificationRepository;

  public MatchFinishedNotifier(
      TeamService teamService,
      MembershipService membershipService,
      MatchEventService matchEventService,
      NotificationRepository notificationRepository) {
    this.teamService = teamService;
    this.membershipService = membershipService;
    this.matchEventService = matchEventService;
    this.notificationRepository = notificationRepository;
  }

  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void onMatchFinished(MatchFinishedEvent event) {
    UUID homeClubId = teamService.get(event.homeTeamId()).clubId();
    UUID awayClubId = teamService.get(event.awayTeamId()).clubId();

    Set<UUID> recipientIds = new HashSet<>();
    membershipService.listForClub(homeClubId).forEach(m -> recipientIds.add(m.getUserId()));
    membershipService.listForClub(awayClubId).forEach(m -> recipientIds.add(m.getUserId()));

    List<MatchEventResponse> events = matchEventService.listForMatch(event.matchId());
    int homeScore = countGoalsForTeam(events, event.homeTeamId());
    int awayScore = countGoalsForTeam(events, event.awayTeamId());

    for (UUID userId : recipientIds) {
      notificationRepository.save(
          new Notification(
              userId,
              NotificationType.MATCH_FINISHED,
              event.matchId(),
              event.homeTeamId(),
              event.awayTeamId(),
              homeScore,
              awayScore));
    }
  }

  private static int countGoalsForTeam(List<MatchEventResponse> events, UUID teamId) {
    return (int)
        events.stream()
            .filter(e -> e.type() == MatchEventType.GOAL_SCORED && e.teamId().equals(teamId))
            .count();
  }
}
```

- [ ] **Step 8: Run the test to verify it passes**

Run: `cd backend/api && ./mvnw test -Dtest=NotificationFlowIT`
Expected: PASS (3 tests).

- [ ] **Step 9: Run the full backend test suite**

Run: `cd backend/api && ./mvnw verify`
Expected: PASS — every existing test still green.

- [ ] **Step 10: Commit**

```bash
git add backend/api/src/main/resources/db/migration/V6__create_notification_tables.sql \
        backend/api/src/main/resources/application.yml \
        backend/api/src/main/java/com/sporya/notification \
        backend/api/src/test/java/com/sporya/notification
git commit -m "feat(notification): notify club members when a match finishes"
```

---

## Task 2: List and mark-read endpoints

**Files:**
- Create: `backend/api/src/main/java/com/sporya/notification/controller/dto/NotificationResponse.java`
- Create: `backend/api/src/main/java/com/sporya/notification/domain/NotificationNotFoundException.java`
- Create: `backend/api/src/main/java/com/sporya/notification/controller/NotificationApiExceptionHandler.java`
- Create: `backend/api/src/main/java/com/sporya/notification/application/NotificationService.java`
- Create: `backend/api/src/main/java/com/sporya/notification/controller/NotificationController.java`
- Modify: `backend/api/src/test/java/com/sporya/notification/NotificationFlowIT.java`

**Interfaces:**
- Consumes: `NotificationRepository.findByUserIdOrderByCreatedAtDesc`/`findByIdAndUserId`, `Notification.markRead()` (Task 1); `com.sporya.auth.infrastructure.security.AuthenticatedUser` (existing).
- Produces: `NotificationResponse(UUID id, NotificationType type, UUID matchId, UUID homeTeamId, UUID awayTeamId, int homeScore, int awayScore, boolean read, Instant createdAt)`, routes `GET /api/v1/notifications`, `POST /api/v1/notifications/{notificationId}/read`. Last task in this plan.

- [ ] **Step 1: Write the DTO and exception**

`backend/api/src/main/java/com/sporya/notification/controller/dto/NotificationResponse.java`:
```java
package com.sporya.notification.controller.dto;

import com.sporya.notification.domain.Notification;
import com.sporya.notification.domain.NotificationType;
import java.time.Instant;
import java.util.UUID;

public record NotificationResponse(
    UUID id,
    NotificationType type,
    UUID matchId,
    UUID homeTeamId,
    UUID awayTeamId,
    int homeScore,
    int awayScore,
    boolean read,
    Instant createdAt) {

  public static NotificationResponse from(Notification notification) {
    return new NotificationResponse(
        notification.getId(),
        notification.getType(),
        notification.getMatchId(),
        notification.getHomeTeamId(),
        notification.getAwayTeamId(),
        notification.getHomeScore(),
        notification.getAwayScore(),
        notification.isRead(),
        notification.getCreatedAt());
  }
}
```

`backend/api/src/main/java/com/sporya/notification/domain/NotificationNotFoundException.java`:
```java
package com.sporya.notification.domain;

import java.util.UUID;

public class NotificationNotFoundException extends RuntimeException {

  public NotificationNotFoundException(UUID id) {
    super("Notification not found: " + id);
  }
}
```

- [ ] **Step 2: Write `NotificationApiExceptionHandler`**

`backend/api/src/main/java/com/sporya/notification/controller/NotificationApiExceptionHandler.java`:
```java
package com.sporya.notification.controller;

import com.sporya.notification.domain.NotificationNotFoundException;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
class NotificationApiExceptionHandler {

  @ExceptionHandler(NotificationNotFoundException.class)
  ResponseEntity<Map<String, String>> handleNotificationNotFound(NotificationNotFoundException ex) {
    return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", ex.getMessage()));
  }
}
```

Note: this module has no `@Valid @RequestBody` endpoints (no request bodies at all), so unlike every other module's `ApiExceptionHandler` there is no `MethodArgumentNotValidException` handler to add, and no `ErrorResponse` record — a `Map.of("message", ...)` is enough for this one error case.

- [ ] **Step 3: Extend `NotificationFlowIT` with failing tests**

Add these imports to `NotificationFlowIT`:
```java
import com.sporya.notification.controller.dto.NotificationResponse;
import org.springframework.http.HttpStatus;
```
Add these test methods after `aMatchThatNeverFinishesGeneratesNoNotifications`:
```java
  @Test
  void listingAndMarkingNotificationsRead() {
    String homeEmail = "home+" + System.nanoTime() + "@sporya.test";
    String homePassword = "correct-horse-battery";
    String homeAccessToken = register(homeEmail, homePassword);
    UUID competitionId = createCompetition(homeAccessToken);
    UUID seasonId = createSeason(homeAccessToken, competitionId);
    UUID homeClubId = createClub(homeAccessToken);
    UUID homeTeamId = createTeam(homeAccessToken, homeClubId);
    homeAccessToken = login(homeEmail, homePassword);
    UUID awayClubId = createClub(homeAccessToken);
    UUID awayTeamId = createTeam(homeAccessToken, awayClubId);

    UUID matchId = createMatch(homeAccessToken, seasonId, homeTeamId, awayTeamId);
    transition(homeAccessToken, matchId, "start");
    transition(homeAccessToken, matchId, "finish");

    ResponseEntity<NotificationResponse[]> listResponse =
        restTemplate.exchange(
            "/api/v1/notifications",
            HttpMethod.GET,
            new HttpEntity<>(authHeaders(homeAccessToken)),
            NotificationResponse[].class);
    assertThat(listResponse.getBody()).isNotNull();
    assertThat(listResponse.getBody()).hasSize(1);
    assertThat(listResponse.getBody()[0].read()).isFalse();
    UUID notificationId = listResponse.getBody()[0].id();

    ResponseEntity<NotificationResponse> markReadResponse =
        restTemplate.exchange(
            "/api/v1/notifications/" + notificationId + "/read",
            HttpMethod.POST,
            new HttpEntity<>(authHeaders(homeAccessToken)),
            NotificationResponse.class);
    assertThat(markReadResponse.getBody()).isNotNull();
    assertThat(markReadResponse.getBody().read()).isTrue();

    ResponseEntity<NotificationResponse[]> listAfterResponse =
        restTemplate.exchange(
            "/api/v1/notifications",
            HttpMethod.GET,
            new HttpEntity<>(authHeaders(homeAccessToken)),
            NotificationResponse[].class);
    assertThat(listAfterResponse.getBody()).isNotNull();
    assertThat(listAfterResponse.getBody()[0].read()).isTrue();
  }

  @Test
  void markingSomeoneElsesNotificationReturns404() {
    String homeEmail = "home+" + System.nanoTime() + "@sporya.test";
    String homePassword = "correct-horse-battery";
    String homeAccessToken = register(homeEmail, homePassword);
    UUID competitionId = createCompetition(homeAccessToken);
    UUID seasonId = createSeason(homeAccessToken, competitionId);
    UUID homeClubId = createClub(homeAccessToken);
    UUID homeTeamId = createTeam(homeAccessToken, homeClubId);
    homeAccessToken = login(homeEmail, homePassword);
    UUID awayClubId = createClub(homeAccessToken);
    UUID awayTeamId = createTeam(homeAccessToken, awayClubId);

    UUID matchId = createMatch(homeAccessToken, seasonId, homeTeamId, awayTeamId);
    transition(homeAccessToken, matchId, "start");
    transition(homeAccessToken, matchId, "finish");

    ResponseEntity<NotificationResponse[]> listResponse =
        restTemplate.exchange(
            "/api/v1/notifications",
            HttpMethod.GET,
            new HttpEntity<>(authHeaders(homeAccessToken)),
            NotificationResponse[].class);
    UUID notificationId = listResponse.getBody()[0].id();

    String outsiderAccessToken =
        register("outsider+" + System.nanoTime() + "@sporya.test", "correct-horse-battery");

    ResponseEntity<Void> response =
        restTemplate.exchange(
            "/api/v1/notifications/" + notificationId + "/read",
            HttpMethod.POST,
            new HttpEntity<>(authHeaders(outsiderAccessToken)),
            Void.class);
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
  }
```

- [ ] **Step 4: Run the test to verify it fails**

Run: `cd backend/api && ./mvnw test -Dtest=NotificationFlowIT`
Expected: FAIL — 401/404, no route mapped yet for `/api/v1/notifications`.

- [ ] **Step 5: Implement `NotificationService`**

`backend/api/src/main/java/com/sporya/notification/application/NotificationService.java`:
```java
package com.sporya.notification.application;

import com.sporya.notification.controller.dto.NotificationResponse;
import com.sporya.notification.domain.Notification;
import com.sporya.notification.domain.NotificationNotFoundException;
import com.sporya.notification.infrastructure.persistence.NotificationRepository;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class NotificationService {

  private final NotificationRepository notificationRepository;

  public NotificationService(NotificationRepository notificationRepository) {
    this.notificationRepository = notificationRepository;
  }

  @Transactional(readOnly = true)
  public List<NotificationResponse> listForUser(UUID userId) {
    return notificationRepository.findByUserIdOrderByCreatedAtDesc(userId).stream()
        .map(NotificationResponse::from)
        .toList();
  }

  @Transactional
  public NotificationResponse markRead(UUID userId, UUID notificationId) {
    Notification notification =
        notificationRepository
            .findByIdAndUserId(notificationId, userId)
            .orElseThrow(() -> new NotificationNotFoundException(notificationId));
    notification.markRead();
    return NotificationResponse.from(notificationRepository.save(notification));
  }
}
```

- [ ] **Step 6: Implement `NotificationController`**

`backend/api/src/main/java/com/sporya/notification/controller/NotificationController.java`:
```java
package com.sporya.notification.controller;

import com.sporya.auth.infrastructure.security.AuthenticatedUser;
import com.sporya.notification.application.NotificationService;
import com.sporya.notification.controller.dto.NotificationResponse;
import java.util.List;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/notifications")
public class NotificationController {

  private final NotificationService notificationService;

  public NotificationController(NotificationService notificationService) {
    this.notificationService = notificationService;
  }

  @GetMapping
  public List<NotificationResponse> list(@AuthenticationPrincipal AuthenticatedUser caller) {
    return notificationService.listForUser(caller.userId());
  }

  @PostMapping("/{notificationId}/read")
  public NotificationResponse markRead(
      @AuthenticationPrincipal AuthenticatedUser caller, @PathVariable UUID notificationId) {
    return notificationService.markRead(caller.userId(), notificationId);
  }
}
```

- [ ] **Step 7: Run the test to verify it passes**

Run: `cd backend/api && ./mvnw test -Dtest=NotificationFlowIT`
Expected: PASS (5 tests).

- [ ] **Step 8: Run the full backend test suite**

Run: `cd backend/api && ./mvnw verify`
Expected: PASS — every test green, spotless clean.

- [ ] **Step 9: Commit**

```bash
git add backend/api/src/main/java/com/sporya/notification backend/api/src/test/java/com/sporya/notification
git commit -m "feat(notification): add list and mark-read endpoints"
```
