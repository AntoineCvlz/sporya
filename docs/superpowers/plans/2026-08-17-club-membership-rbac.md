# Club-Membership RBAC Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build the minimal `ClubMembership` RBAC increment — the club creator becomes `ADMIN` automatically, an `ADMIN` can add an existing user to their club with a role, and the JWT's `memberships` claim carries real data instead of the hardcoded empty list — so the upcoming Match module has `AuthenticatedUser.hasAnyRole` to build on.

**Architecture:** `ClubMembership` (entity, `Role` enum, `ClubRole` record, `MembershipService`) is a new sub-domain inside the existing `com.sporya.auth` package (Identity & Access), not the `club` package — matches `docs/architecture/overview.md#bounded-contexts`. The Spring Security principal changes app-wide from a bare `UUID` to a new `AuthenticatedUser(UUID userId, List<ClubRole> memberships)` record, carried through `JwtAuthenticationFilter`. `com.sporya.club` consumes `AuthenticatedUser`, `Role`, and `MembershipService` directly (cross-module domain/application references are already the norm in this monolith — see `ClubService`/`AuthController` today). Backend built as two vertical slices proven by IT tests (Testcontainers + `TestRestTemplate`, same pattern as `AuthFlowIT`/`ClubFlowIT`), frontend adds one "Membres" block to the existing `ClubDetailPage.tsx`.

**Tech Stack:** Spring Boot 3.5 / Java 21 (existing `backend/api`), Spring Data JPA, Flyway, jjwt 0.13.0 (RS256, `jjwt-jackson` for claim (de)serialization), Testcontainers + `TestRestTemplate`, React 19 + TanStack Query (existing `frontend`).

**Spec:** `docs/superpowers/specs/2026-08-14-club-membership-rbac-design.md`

## Global Constraints

- The club creator automatically becomes `ADMIN` of that club, in the same transaction as club creation.
- One role per user per club (`UNIQUE (user_id, club_id)`) — no role stacking on the same club in this pass.
- No member removal, no role change, no email/link invitation — adding a member is the only management operation.
- The JWT is not refreshed live: a membership granted after login only shows up on that user's next login — accepted limitation (ADR-013).
- `ClubMembership` lives in the `auth` Postgres schema (Identity & Access), not `club` — no cross-schema FK to `club.clubs` (ADR-012), `club_id` is a bare `UUID` column.
- No Spring Security SpEL/`@PreAuthorize` — `AuthenticatedUser.hasAnyRole(UUID clubId, Role... roles)` is the one access-control primitive (roles are dynamic per-resource, not static).
- Migration file: `backend/api/src/main/resources/db/migration/V3__create_club_memberships_table.sql`, flat folder, unqualified table name (same convention as `V1__create_users_table.sql` — default Flyway schema is already `auth`).

---

## Task 1: `ClubMembership` domain, `MembershipService`, `AuthenticatedUser` principal, JWT wiring

**Files:**
- Create: `backend/api/src/main/resources/db/migration/V3__create_club_memberships_table.sql`
- Create: `backend/api/src/main/java/com/sporya/auth/domain/Role.java`
- Create: `backend/api/src/main/java/com/sporya/auth/domain/ClubRole.java`
- Create: `backend/api/src/main/java/com/sporya/auth/domain/ClubMembership.java`
- Create: `backend/api/src/main/java/com/sporya/auth/infrastructure/persistence/ClubMembershipRepository.java`
- Create: `backend/api/src/main/java/com/sporya/auth/application/MembershipService.java`
- Create: `backend/api/src/main/java/com/sporya/auth/infrastructure/security/AuthenticatedUser.java`
- Modify: `backend/api/src/main/java/com/sporya/auth/infrastructure/security/JwtService.java`
- Modify: `backend/api/src/main/java/com/sporya/auth/infrastructure/security/JwtAuthenticationFilter.java`
- Modify: `backend/api/src/main/java/com/sporya/auth/controller/AuthController.java`
- Modify: `backend/api/src/main/java/com/sporya/club/application/ClubService.java`
- Modify: `backend/api/src/main/java/com/sporya/club/controller/ClubController.java`
- Modify: `backend/api/src/test/java/com/sporya/auth/AuthFlowIT.java`
- Modify: `backend/api/src/test/java/com/sporya/club/ClubFlowIT.java`

**Interfaces:**
- Consumes: nothing new from outside this task — it builds the foundational plumbing itself, and touches `ClubService`/`ClubController` only because the Spring Security principal type change is a breaking change for both existing `@AuthenticationPrincipal UUID` usages (`AuthController#me`, `ClubController#create`) and must land together with the fix or `ClubFlowIT` breaks.
- Produces: `Role` enum (`ADMIN, COACH, ANALYST, PLAYER, VIEWER`), `ClubRole(UUID clubId, Role role)`, `ClubMembership` entity (`getUserId()/getClubId()/getRole()/getCreatedAt()`), `MembershipService.grant(UUID userId, UUID clubId, Role role): ClubMembership`, `MembershipService.membershipsFor(UUID userId): List<ClubRole>`, `MembershipService.listForClub(UUID clubId): List<ClubMembership>`, `AuthenticatedUser(UUID userId, List<ClubRole> memberships)` with `hasAnyRole(UUID clubId, Role... roles): boolean` — now the app-wide Spring Security principal type. Task 2 depends on all of the above plus `AuthenticationService.currentUser` (existing).

- [ ] **Step 1: Create the migration for `club_memberships`**

`backend/api/src/main/resources/db/migration/V3__create_club_memberships_table.sql`:
```sql
CREATE TABLE club_memberships (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id),
    club_id UUID NOT NULL,
    role VARCHAR(20) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (user_id, club_id)
);
```

- [ ] **Step 2: Write the `Role` enum**

`backend/api/src/main/java/com/sporya/auth/domain/Role.java`:
```java
package com.sporya.auth.domain;

public enum Role {
  ADMIN,
  COACH,
  ANALYST,
  PLAYER,
  VIEWER
}
```

- [ ] **Step 3: Write the `ClubRole` record**

`backend/api/src/main/java/com/sporya/auth/domain/ClubRole.java`:
```java
package com.sporya.auth.domain;

import java.util.UUID;

public record ClubRole(UUID clubId, Role role) {}
```

- [ ] **Step 4: Write the `ClubMembership` entity**

`backend/api/src/main/java/com/sporya/auth/domain/ClubMembership.java`:
```java
package com.sporya.auth.domain;

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
@Table(name = "club_memberships")
public class ClubMembership {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(name = "user_id", nullable = false)
  private UUID userId;

  @Column(name = "club_id", nullable = false)
  private UUID clubId;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private Role role;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  protected ClubMembership() {}

  public ClubMembership(UUID userId, UUID clubId, Role role) {
    this.userId = userId;
    this.clubId = clubId;
    this.role = role;
    this.createdAt = Instant.now();
  }

  public UUID getId() {
    return id;
  }

  public UUID getUserId() {
    return userId;
  }

  public UUID getClubId() {
    return clubId;
  }

  public Role getRole() {
    return role;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }
}
```

- [ ] **Step 5: Write the `ClubMembershipRepository`**

`backend/api/src/main/java/com/sporya/auth/infrastructure/persistence/ClubMembershipRepository.java`:
```java
package com.sporya.auth.infrastructure.persistence;

import com.sporya.auth.domain.ClubMembership;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ClubMembershipRepository extends JpaRepository<ClubMembership, UUID> {

  List<ClubMembership> findByUserId(UUID userId);

  List<ClubMembership> findByClubId(UUID clubId);
}
```

- [ ] **Step 6: Write `MembershipService`**

`backend/api/src/main/java/com/sporya/auth/application/MembershipService.java`:
```java
package com.sporya.auth.application;

import com.sporya.auth.domain.ClubMembership;
import com.sporya.auth.domain.ClubRole;
import com.sporya.auth.domain.Role;
import com.sporya.auth.infrastructure.persistence.ClubMembershipRepository;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MembershipService {

  private final ClubMembershipRepository clubMembershipRepository;

  public MembershipService(ClubMembershipRepository clubMembershipRepository) {
    this.clubMembershipRepository = clubMembershipRepository;
  }

  @Transactional
  public ClubMembership grant(UUID userId, UUID clubId, Role role) {
    return clubMembershipRepository.save(new ClubMembership(userId, clubId, role));
  }

  @Transactional(readOnly = true)
  public List<ClubRole> membershipsFor(UUID userId) {
    return clubMembershipRepository.findByUserId(userId).stream()
        .map(membership -> new ClubRole(membership.getClubId(), membership.getRole()))
        .toList();
  }

  @Transactional(readOnly = true)
  public List<ClubMembership> listForClub(UUID clubId) {
    return clubMembershipRepository.findByClubId(clubId);
  }
}
```

- [ ] **Step 7: Write `AuthenticatedUser`**

`backend/api/src/main/java/com/sporya/auth/infrastructure/security/AuthenticatedUser.java`:
```java
package com.sporya.auth.infrastructure.security;

import com.sporya.auth.domain.ClubRole;
import com.sporya.auth.domain.Role;
import java.util.List;
import java.util.UUID;

public record AuthenticatedUser(UUID userId, List<ClubRole> memberships) {

  public boolean hasAnyRole(UUID clubId, Role... roles) {
    List<Role> wanted = List.of(roles);
    return memberships.stream()
        .anyMatch(
            membership -> membership.clubId().equals(clubId) && wanted.contains(membership.role()));
  }
}
```

- [ ] **Step 8: Extend `AuthFlowIT` with a failing test proving the JWT claim is real**

In `backend/api/src/test/java/com/sporya/auth/AuthFlowIT.java`, add these imports next to the existing ones:
```java
import com.sporya.auth.application.MembershipService;
import com.sporya.auth.domain.Role;
import com.sporya.auth.infrastructure.security.JwtService;
import io.jsonwebtoken.Claims;
import java.util.List;
import java.util.Map;
import java.util.UUID;
```
Add these two fields next to `restTemplate`:
```java
  @Autowired private MembershipService membershipService;
  @Autowired private JwtService jwtService;
```
Add this test method after `registerThenLoginThenFetchProtectedProfile`:
```java
  @Test
  void loginTokenCarriesGrantedMemberships() {
    String email = "coach+" + System.nanoTime() + "@sporya.test";
    String password = "correct-horse-battery";

    ResponseEntity<UserResponse> registerResponse =
        restTemplate.postForEntity(
            "/api/v1/auth/register", new RegisterRequest(email, password), UserResponse.class);
    UUID userId = registerResponse.getBody().id();
    UUID clubId = UUID.randomUUID();
    membershipService.grant(userId, clubId, Role.COACH);

    ResponseEntity<AuthResponse> loginResponse =
        restTemplate.postForEntity(
            "/api/v1/auth/login", new LoginRequest(email, password), AuthResponse.class);
    Claims claims = jwtService.parseAndValidate(loginResponse.getBody().accessToken());

    @SuppressWarnings("unchecked")
    List<Map<String, Object>> memberships = claims.get("memberships", List.class);
    assertThat(memberships).hasSize(1);
    assertThat(memberships.get(0).get("clubId")).isEqualTo(clubId.toString());
    assertThat(memberships.get(0).get("role")).isEqualTo("COACH");
  }
```

- [ ] **Step 9: Extend `ClubFlowIT` with a failing test proving club creation grants ADMIN**

In `backend/api/src/test/java/com/sporya/club/ClubFlowIT.java`, add these imports next to the existing ones:
```java
import com.sporya.auth.application.MembershipService;
import com.sporya.auth.domain.ClubRole;
import com.sporya.auth.domain.Role;
import com.sporya.auth.infrastructure.security.JwtService;
```
Add these two fields next to `restTemplate`:
```java
  @Autowired private MembershipService membershipService;
  @Autowired private JwtService jwtService;
```
Add this test method after `createClubWithoutAuthReturns401`:
```java
  @Test
  void createClubGrantsCreatorAdminMembership() {
    String accessToken = registerAndLogin();
    UUID clubId = createClub(accessToken);
    UUID userId = UUID.fromString(jwtService.parseAndValidate(accessToken).getSubject());

    assertThat(membershipService.membershipsFor(userId)).contains(new ClubRole(clubId, Role.ADMIN));
  }
```

- [ ] **Step 10: Run both new tests to verify they fail**

Run: `cd backend/api && ./mvnw test -Dtest=AuthFlowIT,ClubFlowIT`
Expected: FAIL — `loginTokenCarriesGrantedMemberships` fails because the claim is still the hardcoded empty list; `createClubGrantsCreatorAdminMembership` fails because nothing calls `MembershipService.grant` yet.

- [ ] **Step 11: Wire `MembershipService` into `JwtService`**

Replace the full contents of `backend/api/src/main/java/com/sporya/auth/infrastructure/security/JwtService.java`:
```java
package com.sporya.auth.infrastructure.security;

import com.sporya.auth.application.MembershipService;
import com.sporya.auth.domain.ClubRole;
import com.sporya.auth.domain.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.Jwts;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Émission et validation de JWT signés RS256 (voir ADR-013) — vérifiables localement par tout
 * service détenant la clé publique, sans appel réseau à Auth Service.
 */
@Service
public class JwtService {

  private final RSAPrivateKey privateKey;
  private final RSAPublicKey publicKey;
  private final String issuer;
  private final long accessTokenTtlMinutes;
  private final MembershipService membershipService;

  JwtService(
      RSAPrivateKey privateKey,
      RSAPublicKey publicKey,
      @Value("${sporya.jwt.issuer}") String issuer,
      @Value("${sporya.jwt.access-token-ttl-minutes}") long accessTokenTtlMinutes,
      MembershipService membershipService) {
    this.privateKey = privateKey;
    this.publicKey = publicKey;
    this.issuer = issuer;
    this.accessTokenTtlMinutes = accessTokenTtlMinutes;
    this.membershipService = membershipService;
  }

  public long accessTokenTtlSeconds() {
    return accessTokenTtlMinutes * 60;
  }

  public String generateAccessToken(User user) {
    Instant now = Instant.now();
    List<Map<String, Object>> memberships =
        membershipService.membershipsFor(user.getId()).stream().map(JwtService::toClaim).toList();
    return Jwts.builder()
        .subject(user.getId().toString())
        .claim("email", user.getEmail())
        .claim("memberships", memberships)
        .issuer(issuer)
        .issuedAt(Date.from(now))
        .expiration(Date.from(now.plusSeconds(accessTokenTtlSeconds())))
        .signWith(privateKey, Jwts.SIG.RS256)
        .compact();
  }

  private static Map<String, Object> toClaim(ClubRole clubRole) {
    return Map.of("clubId", clubRole.clubId().toString(), "role", clubRole.role().name());
  }

  public Claims parseAndValidate(String token) {
    Jws<Claims> jws = Jwts.parser().verifyWith(publicKey).build().parseSignedClaims(token);
    return jws.getPayload();
  }
}
```

- [ ] **Step 12: Build `AuthenticatedUser` as the principal in `JwtAuthenticationFilter`**

Replace the full contents of `backend/api/src/main/java/com/sporya/auth/infrastructure/security/JwtAuthenticationFilter.java`:
```java
package com.sporya.auth.infrastructure.security;

import com.sporya.auth.domain.ClubRole;
import com.sporya.auth.domain.Role;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/** Authentifie la requête à partir d'un Bearer token JWT valide, sinon la laisse anonyme. */
@Component
class JwtAuthenticationFilter extends OncePerRequestFilter {

  private final JwtService jwtService;

  JwtAuthenticationFilter(JwtService jwtService) {
    this.jwtService = jwtService;
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {
    String header = request.getHeader("Authorization");
    if (header != null && header.startsWith("Bearer ")) {
      try {
        Claims claims = jwtService.parseAndValidate(header.substring("Bearer ".length()));
        UUID userId = UUID.fromString(claims.getSubject());
        AuthenticatedUser authenticatedUser = new AuthenticatedUser(userId, parseMemberships(claims));
        var authentication =
            new UsernamePasswordAuthenticationToken(authenticatedUser, null, List.of());
        SecurityContextHolder.getContext().setAuthentication(authentication);
      } catch (JwtException | IllegalArgumentException ignored) {
        // Token absent/invalide/expiré -> requête traitée comme anonyme, rejetée plus loin
        // par la configuration Spring Security si la route l'exige.
      }
    }
    filterChain.doFilter(request, response);
  }

  @SuppressWarnings("unchecked")
  private static List<ClubRole> parseMemberships(Claims claims) {
    List<Map<String, Object>> raw = claims.get("memberships", List.class);
    return raw.stream()
        .map(
            entry ->
                new ClubRole(
                    UUID.fromString((String) entry.get("clubId")),
                    Role.valueOf((String) entry.get("role"))))
        .toList();
  }
}
```

- [ ] **Step 13: Migrate `AuthController` to the `AuthenticatedUser` principal**

Replace the full contents of `backend/api/src/main/java/com/sporya/auth/controller/AuthController.java`:
```java
package com.sporya.auth.controller;

import com.sporya.auth.application.AuthenticationService;
import com.sporya.auth.controller.dto.AuthResponse;
import com.sporya.auth.controller.dto.LoginRequest;
import com.sporya.auth.controller.dto.RegisterRequest;
import com.sporya.auth.controller.dto.UserResponse;
import com.sporya.auth.infrastructure.security.AuthenticatedUser;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

  private final AuthenticationService authenticationService;

  public AuthController(AuthenticationService authenticationService) {
    this.authenticationService = authenticationService;
  }

  @PostMapping("/register")
  @ResponseStatus(HttpStatus.CREATED)
  public UserResponse register(@Valid @RequestBody RegisterRequest request) {
    return authenticationService.register(request);
  }

  @PostMapping("/login")
  public AuthResponse login(@Valid @RequestBody LoginRequest request) {
    return authenticationService.login(request);
  }

  @GetMapping("/me")
  public UserResponse me(@AuthenticationPrincipal AuthenticatedUser user) {
    return authenticationService.currentUser(user.userId());
  }
}
```

- [ ] **Step 14: `ClubService` grants ADMIN membership on club creation**

Replace the full contents of `backend/api/src/main/java/com/sporya/club/application/ClubService.java`:
```java
package com.sporya.club.application;

import com.sporya.auth.application.MembershipService;
import com.sporya.auth.domain.Role;
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
  private final MembershipService membershipService;

  public ClubService(ClubRepository clubRepository, MembershipService membershipService) {
    this.clubRepository = clubRepository;
    this.membershipService = membershipService;
  }

  @Transactional
  public ClubResponse create(UUID createdBy, CreateClubRequest request) {
    Club club = new Club(request.name(), request.country(), createdBy);
    Club saved = clubRepository.save(club);
    membershipService.grant(createdBy, saved.getId(), Role.ADMIN);
    return ClubResponse.from(saved);
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

- [ ] **Step 15: Migrate `ClubController` to the `AuthenticatedUser` principal**

Replace the full contents of `backend/api/src/main/java/com/sporya/club/controller/ClubController.java`:
```java
package com.sporya.club.controller;

import com.sporya.auth.infrastructure.security.AuthenticatedUser;
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
      @AuthenticationPrincipal AuthenticatedUser user, @Valid @RequestBody CreateClubRequest request) {
    return clubService.create(user.userId(), request);
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

- [ ] **Step 16: Run both new tests to verify they pass**

Run: `cd backend/api && ./mvnw test -Dtest=AuthFlowIT,ClubFlowIT`
Expected: PASS — all tests in both files green.

- [ ] **Step 17: Run the full backend test suite**

Run: `cd backend/api && ./mvnw verify`
Expected: PASS — `AuthModuleSmokeTest`, `AuthFlowIT`, `ClubFlowIT` all green, spotless clean.

- [ ] **Step 18: Commit**

```bash
git add backend/api/src/main/resources/db/migration/V3__create_club_memberships_table.sql \
        backend/api/src/main/java/com/sporya/auth \
        backend/api/src/main/java/com/sporya/club/application/ClubService.java \
        backend/api/src/main/java/com/sporya/club/controller/ClubController.java \
        backend/api/src/test/java/com/sporya/auth/AuthFlowIT.java \
        backend/api/src/test/java/com/sporya/club/ClubFlowIT.java
git commit -m "feat(auth): wire ClubMembership RBAC into JWT claims and Club creation"
```

---

## Task 2: `ClubMemberController` — add member (ADMIN-only), list members

**Files:**
- Modify: `backend/api/src/main/java/com/sporya/auth/domain/UserNotFoundException.java`
- Modify: `backend/api/src/main/java/com/sporya/auth/application/AuthenticationService.java`
- Create: `backend/api/src/main/java/com/sporya/club/domain/ClubAccessDeniedException.java`
- Create: `backend/api/src/main/java/com/sporya/club/controller/dto/AddMemberRequest.java`
- Create: `backend/api/src/main/java/com/sporya/club/controller/dto/MemberResponse.java`
- Modify: `backend/api/src/main/java/com/sporya/club/controller/ClubApiExceptionHandler.java`
- Create: `backend/api/src/main/java/com/sporya/club/application/ClubMemberService.java`
- Create: `backend/api/src/main/java/com/sporya/club/controller/ClubMemberController.java`
- Test: `backend/api/src/test/java/com/sporya/club/ClubMembershipRbacIT.java`

**Interfaces:**
- Consumes: `MembershipService.grant/listForClub` (Task 1), `Role` (Task 1), `AuthenticatedUser.hasAnyRole` (Task 1), `ClubMembership.getUserId()/getRole()/getCreatedAt()` (Task 1), `ClubRepository.existsById` (existing), `AuthenticationService.currentUser(UUID): UserResponse` (existing).
- Produces: `AuthenticationService.findUserIdByEmail(String): UUID`, `ClubAccessDeniedException`, `AddMemberRequest(String email, Role role)`, `MemberResponse(UUID userId, String email, Role role, Instant createdAt)`, routes `POST/GET /api/v1/clubs/{clubId}/members`. Task 3 depends on the `MemberResponse` JSON shape and both routes.

- [ ] **Step 1: Add the email-lookup constructor to `UserNotFoundException`**

In `backend/api/src/main/java/com/sporya/auth/domain/UserNotFoundException.java`, add this constructor next to the existing `UUID` one:
```java
  public UserNotFoundException(String email) {
    super("User not found: " + email);
  }
```

- [ ] **Step 2: Add `findUserIdByEmail` to `AuthenticationService`**

In `backend/api/src/main/java/com/sporya/auth/application/AuthenticationService.java`, add this method after `currentUser`:
```java
  @Transactional(readOnly = true)
  public UUID findUserIdByEmail(String email) {
    return userRepository
        .findByEmail(email)
        .map(User::getId)
        .orElseThrow(() -> new UserNotFoundException(email));
  }
```

- [ ] **Step 3: Write `ClubAccessDeniedException`**

`backend/api/src/main/java/com/sporya/club/domain/ClubAccessDeniedException.java`:
```java
package com.sporya.club.domain;

import java.util.UUID;

public class ClubAccessDeniedException extends RuntimeException {

  public ClubAccessDeniedException(UUID clubId) {
    super("Not authorized to manage members of club: " + clubId);
  }
}
```

- [ ] **Step 4: Write the member DTOs**

`backend/api/src/main/java/com/sporya/club/controller/dto/AddMemberRequest.java`:
```java
package com.sporya.club.controller.dto;

import com.sporya.auth.domain.Role;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record AddMemberRequest(@NotBlank @Email String email, @NotNull Role role) {}
```

`backend/api/src/main/java/com/sporya/club/controller/dto/MemberResponse.java`:
```java
package com.sporya.club.controller.dto;

import com.sporya.auth.domain.Role;
import java.time.Instant;
import java.util.UUID;

public record MemberResponse(UUID userId, String email, Role role, Instant createdAt) {}
```

- [ ] **Step 5: Add the `ClubAccessDeniedException` handler**

In `backend/api/src/main/java/com/sporya/club/controller/ClubApiExceptionHandler.java`, add the import `com.sporya.club.domain.ClubAccessDeniedException` next to the other domain imports, and add this handler next to `handleTeamNotFound`:
```java
  @ExceptionHandler(ClubAccessDeniedException.class)
  ResponseEntity<ErrorResponse> handleClubAccessDenied(ClubAccessDeniedException ex) {
    return ResponseEntity.status(HttpStatus.FORBIDDEN).body(new ErrorResponse(ex.getMessage()));
  }
```

- [ ] **Step 6: Write the failing test — `ClubMembershipRbacIT`**

`backend/api/src/test/java/com/sporya/club/ClubMembershipRbacIT.java`:
```java
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
```

- [ ] **Step 7: Run the test to verify it fails**

Run: `cd backend/api && ./mvnw test -Dtest=ClubMembershipRbacIT`
Expected: FAIL — compilation error, `ClubMemberService`/`ClubMemberController` don't exist yet.

- [ ] **Step 8: Implement `ClubMemberService`**

`backend/api/src/main/java/com/sporya/club/application/ClubMemberService.java`:
```java
package com.sporya.club.application;

import com.sporya.auth.application.AuthenticationService;
import com.sporya.auth.application.MembershipService;
import com.sporya.auth.domain.ClubMembership;
import com.sporya.auth.domain.Role;
import com.sporya.auth.infrastructure.security.AuthenticatedUser;
import com.sporya.club.controller.dto.AddMemberRequest;
import com.sporya.club.controller.dto.MemberResponse;
import com.sporya.club.domain.ClubAccessDeniedException;
import com.sporya.club.domain.ClubNotFoundException;
import com.sporya.club.infrastructure.persistence.ClubRepository;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ClubMemberService {

  private final ClubRepository clubRepository;
  private final MembershipService membershipService;
  private final AuthenticationService authenticationService;

  public ClubMemberService(
      ClubRepository clubRepository,
      MembershipService membershipService,
      AuthenticationService authenticationService) {
    this.clubRepository = clubRepository;
    this.membershipService = membershipService;
    this.authenticationService = authenticationService;
  }

  @Transactional
  public MemberResponse add(AuthenticatedUser caller, UUID clubId, AddMemberRequest request) {
    if (!clubRepository.existsById(clubId)) {
      throw new ClubNotFoundException(clubId);
    }
    if (!caller.hasAnyRole(clubId, Role.ADMIN)) {
      throw new ClubAccessDeniedException(clubId);
    }
    UUID targetUserId = authenticationService.findUserIdByEmail(request.email());
    ClubMembership membership = membershipService.grant(targetUserId, clubId, request.role());
    return new MemberResponse(
        targetUserId, request.email(), request.role(), membership.getCreatedAt());
  }

  @Transactional(readOnly = true)
  public List<MemberResponse> list(UUID clubId) {
    if (!clubRepository.existsById(clubId)) {
      throw new ClubNotFoundException(clubId);
    }
    return membershipService.listForClub(clubId).stream()
        .map(
            membership ->
                new MemberResponse(
                    membership.getUserId(),
                    authenticationService.currentUser(membership.getUserId()).email(),
                    membership.getRole(),
                    membership.getCreatedAt()))
        .toList();
  }
}
```

- [ ] **Step 9: Implement `ClubMemberController`**

`backend/api/src/main/java/com/sporya/club/controller/ClubMemberController.java`:
```java
package com.sporya.club.controller;

import com.sporya.auth.infrastructure.security.AuthenticatedUser;
import com.sporya.club.application.ClubMemberService;
import com.sporya.club.controller.dto.AddMemberRequest;
import com.sporya.club.controller.dto.MemberResponse;
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
@RequestMapping("/api/v1/clubs/{clubId}/members")
public class ClubMemberController {

  private final ClubMemberService clubMemberService;

  public ClubMemberController(ClubMemberService clubMemberService) {
    this.clubMemberService = clubMemberService;
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  public MemberResponse add(
      @AuthenticationPrincipal AuthenticatedUser caller,
      @PathVariable UUID clubId,
      @Valid @RequestBody AddMemberRequest request) {
    return clubMemberService.add(caller, clubId, request);
  }

  @GetMapping
  public List<MemberResponse> list(@PathVariable UUID clubId) {
    return clubMemberService.list(clubId);
  }
}
```

- [ ] **Step 10: Run the test to verify it passes**

Run: `cd backend/api && ./mvnw test -Dtest=ClubMembershipRbacIT`
Expected: PASS (5 tests: `clubCreatorAppearsAsAdminInMembersList`, `adminAddsMemberWithCoachRole`, `nonAdminCannotAddMember`, `addingUnknownEmailReturns404`, `newLoginTokenCarriesMembershipGrantedAfterFirstLogin`).

- [ ] **Step 11: Run the full backend test suite**

Run: `cd backend/api && ./mvnw verify`
Expected: PASS — all of `AuthModuleSmokeTest`, `AuthFlowIT`, `ClubFlowIT`, `ClubMembershipRbacIT` green, spotless clean.

- [ ] **Step 12: Commit**

```bash
git add backend/api/src/main/java/com/sporya/auth/domain/UserNotFoundException.java \
        backend/api/src/main/java/com/sporya/auth/application/AuthenticationService.java \
        backend/api/src/main/java/com/sporya/club/domain/ClubAccessDeniedException.java \
        backend/api/src/main/java/com/sporya/club/controller \
        backend/api/src/main/java/com/sporya/club/application/ClubMemberService.java \
        backend/api/src/test/java/com/sporya/club/ClubMembershipRbacIT.java
git commit -m "feat(club): add member add/list endpoints, ADMIN-gated"
```

---

## Task 3: Frontend — "Membres" block on `ClubDetailPage`

**Files:**
- Modify: `frontend/src/lib/api.ts`
- Modify: `frontend/src/pages/ClubDetailPage.tsx`

**Interfaces:**
- Consumes: `POST/GET /api/v1/clubs/{clubId}/members` and the `MemberResponse` JSON shape (Task 2).
- Produces: `listMembers`, `addMember` functions and `MemberResponse` interface in `frontend/src/lib/api.ts`; the "Membres" UI block. Nothing later in this plan depends on this — it's the last task.

- [ ] **Step 1: Add `MemberResponse`, `listMembers`, `addMember` to the API client**

Append this to the end of `frontend/src/lib/api.ts`:
```ts
export interface MemberResponse {
  userId: string
  email: string
  role: string
  createdAt: string
}

export function listMembers(accessToken: string, clubId: string): Promise<MemberResponse[]> {
  return request<MemberResponse[]>(`/api/v1/clubs/${clubId}/members`, {
    headers: authHeaders(accessToken),
  })
}

export function addMember(
  accessToken: string,
  clubId: string,
  email: string,
  role: string,
): Promise<MemberResponse> {
  return request<MemberResponse>(`/api/v1/clubs/${clubId}/members`, {
    method: 'POST',
    headers: authHeaders(accessToken),
    body: JSON.stringify({ email, role }),
  })
}
```

- [ ] **Step 2: Add the "Membres" block to `ClubDetailPage`**

Replace the full contents of `frontend/src/pages/ClubDetailPage.tsx`:
```tsx
import { useState, type FormEvent } from 'react'
import { Link, useParams } from 'react-router-dom'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'

import { addMember, createTeam, getClub, listMembers, listTeams } from '@/lib/api'
import { useAuth } from '@/lib/auth-context'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'

const ROLES = ['ADMIN', 'COACH', 'ANALYST', 'PLAYER', 'VIEWER'] as const

export function ClubDetailPage() {
  const { clubId } = useParams<{ clubId: string }>()
  const { accessToken } = useAuth()
  const queryClient = useQueryClient()
  const [name, setName] = useState('')
  const [memberEmail, setMemberEmail] = useState('')
  const [memberRole, setMemberRole] = useState<string>(ROLES[1])

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

  const membersQuery = useQuery({
    queryKey: ['members', clubId],
    queryFn: () => listMembers(accessToken as string, clubId as string),
    enabled: Boolean(accessToken && clubId),
  })

  const createMutation = useMutation({
    mutationFn: () => createTeam(accessToken as string, clubId as string, name),
    onSuccess: () => {
      setName('')
      queryClient.invalidateQueries({ queryKey: ['teams', clubId] })
    },
  })

  const addMemberMutation = useMutation({
    mutationFn: () => addMember(accessToken as string, clubId as string, memberEmail, memberRole),
    onSuccess: () => {
      setMemberEmail('')
      queryClient.invalidateQueries({ queryKey: ['members', clubId] })
    },
  })

  function handleSubmit(event: FormEvent) {
    event.preventDefault()
    createMutation.mutate()
  }

  function handleAddMember(event: FormEvent) {
    event.preventDefault()
    addMemberMutation.mutate()
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

      <Card>
        <CardHeader>
          <CardTitle>Membres</CardTitle>
        </CardHeader>
        <CardContent className="flex flex-col gap-2">
          {membersQuery.isLoading && <p className="text-sm text-muted-foreground">Chargement…</p>}
          {membersQuery.data?.length === 0 && (
            <p className="text-sm text-muted-foreground">Aucun membre pour l'instant.</p>
          )}
          {membersQuery.data?.map((member) => (
            <div
              key={member.userId}
              className="flex items-center justify-between rounded-md border border-border px-3 py-2 text-sm"
            >
              <span>{member.email}</span>
              <span className="text-muted-foreground">{member.role}</span>
            </div>
          ))}
        </CardContent>
      </Card>

      <Card>
        <CardHeader>
          <CardTitle>Ajouter un membre</CardTitle>
        </CardHeader>
        <CardContent>
          <form onSubmit={handleAddMember} className="flex flex-col gap-4">
            <div className="flex flex-col gap-2">
              <Label htmlFor="member-email">Email</Label>
              <Input
                id="member-email"
                type="email"
                required
                value={memberEmail}
                onChange={(e) => setMemberEmail(e.target.value)}
              />
            </div>
            <div className="flex flex-col gap-2">
              <Label htmlFor="member-role">Rôle</Label>
              <select
                id="member-role"
                className="flex h-9 w-full rounded-md border border-input bg-transparent px-3 py-1 text-sm shadow-sm focus-visible:outline-none focus-visible:ring-1 focus-visible:ring-ring"
                value={memberRole}
                onChange={(e) => setMemberRole(e.target.value)}
              >
                {ROLES.map((role) => (
                  <option key={role} value={role}>
                    {role}
                  </option>
                ))}
              </select>
            </div>
            <Button type="submit" disabled={addMemberMutation.isPending}>
              {addMemberMutation.isPending ? 'Ajout…' : 'Ajouter'}
            </Button>
          </form>
        </CardContent>
      </Card>
    </div>
  )
}
```

- [ ] **Step 3: Verify it builds**

Run: `cd frontend && npm run lint && npm run build`
Expected: both succeed (0 errors).

- [ ] **Step 4: Commit**

```bash
git add frontend/src/lib/api.ts frontend/src/pages/ClubDetailPage.tsx
git commit -m "feat(frontend): add Membres block (list + add) to ClubDetailPage"
```
