# RBAC par club (ClubMembership) — design

## Contexte

Le module Club (`com.sporya.club`) est construit et déployé sans RBAC — décision de périmètre explicite prise à l'époque (voir `docs/superpowers/specs/2026-08-14-club-module-design.md`), reportant `ClubMembership` au moment où le module Match en aurait réellement besoin (`docs/architecture/overview.md#règles-métier-principales` : "Seuls ADMIN/COACH d'un club créent un match ou ajoutent des événements pour ses équipes").

Ce moment est venu : Match est le prochain module du roadmap. Cet incrément construit le RBAC minimal nécessaire — pas une gestion complète des memberships (pas d'invitation par lien, pas de changement de rôle, pas de retrait) — pour que Match puisse s'appuyer dessus dès sa construction.

Le modèle (`ClubMembership` = utilisateur + club + rôle, JWT stateless porteur des memberships) est déjà décidé par [ADR-011](../../adr/ADR-011-rbac-par-club.md) et [ADR-013](../../adr/ADR-013-jwt-stateless.md). Cet incrément est la première mise en œuvre concrète de ces deux ADR — jusqu'ici le claim `memberships` du JWT était une liste vide codée en dur (`JwtService`, commentaire "Club Service n'existe pas encore").

Décisions de périmètre validées avant ce document :

- **Le créateur d'un club devient automatiquement `ADMIN`** de ce club, dans la même transaction que la création.
- **Un endpoint permet à un `ADMIN` d'ajouter un membre existant avec un rôle** — nécessaire pour pouvoir tester le contrôle d'accès du futur module Match avec un compte non-ADMIN sans passer par la base de données à la main.
- **Pas de gestion au-delà de l'ajout** : pas de retrait de membre, pas de changement de rôle, pas d'invitation par email/lien. Le rôle est fixé à l'ajout ; le corriger pour l'instant = base de données directe.
- **Le JWT n'est pas rafraîchi en direct** : un membership accordé après connexion n'apparaît qu'à la prochaine connexion de l'utilisateur concerné — limitation acceptée, cohérente avec la contrepartie déjà documentée par ADR-013 ("révocation immédiate d'un token plus complexe").

## Modèle de données

Schéma Postgres `auth` (existant) — `ClubMembership` appartient à Identity & Access d'après `docs/architecture/overview.md#bounded-contexts` et `docs/database/README.md` (déjà référencé, jamais construit) :

```sql
auth.club_memberships {
  id         UUID PK,
  user_id    UUID NOT NULL REFERENCES auth.users(id),
  club_id    UUID NOT NULL,   -- pas de FK cross-schéma vers club.clubs (ADR-012)
  role       VARCHAR(20) NOT NULL,  -- ADMIN | COACH | ANALYST | PLAYER | VIEWER
  created_at TIMESTAMPTZ NOT NULL,
  UNIQUE (user_id, club_id)
}
```

Un utilisateur a **un seul rôle par club** (contrainte unique) — pas de cumul de rôles sur un même club dans ce périmètre ; YAGNI tant qu'aucun cas d'usage réel ne l'exige.

**Migration** : `backend/api/src/main/resources/db/migration/V3__create_club_memberships_table.sql`, dossier plat existant.

## Architecture backend

### Module Auth — nouveau sous-domaine `ClubMembership`

```
com.sporya.auth/
├── domain/
│   ├── ClubMembership.java       (entité : id, userId, clubId, role, createdAt)
│   ├── Role.java                 (enum ADMIN, COACH, ANALYST, PLAYER, VIEWER)
│   └── ClubRole.java             (record clubId + role, forme portée par le JWT et par AuthenticatedUser)
├── application/
│   └── MembershipService.java    (grant(userId, clubId, role), membershipsFor(userId), listForClub(clubId))
└── infrastructure/
    ├── persistence/ClubMembershipRepository.java
    └── security/
        ├── AuthenticatedUser.java   (record userId + List<ClubRole>, remplace le UUID brut comme principal)
        └── JwtAuthenticationFilter.java (modifié)
```

`AuthenticatedUser.hasAnyRole(UUID clubId, Role... roles)` — méthode utilitaire utilisée par tout module ayant besoin de vérifier un accès (Club aujourd'hui, Match ensuite), pas de dépendance à Spring Security SpEL/`@PreAuthorize` (rôles dynamiques par ressource, pas adaptés à une expression statique).

### JwtService — claim `memberships` réel

`generateAccessToken` interroge désormais `MembershipService.membershipsFor(user.getId())` et sérialise chaque `ClubRole` en `{"clubId": "...", "role": "..."}` au lieu de la liste vide codée en dur.

### JwtAuthenticationFilter — principal enrichi

Parse le claim `memberships` en `List<ClubRole>`, construit un `AuthenticatedUser` et le pose comme principal de l'`Authentication` (au lieu du seul `UUID`).

**Effet de bord mécanique** : les deux `@AuthenticationPrincipal UUID userId` existants passent à `@AuthenticationPrincipal AuthenticatedUser user` (`.userId()` remplace l'usage direct) — `AuthController#me` et `ClubController#create`. `TeamController`/`PlayerController` n'utilisent pas le principal aujourd'hui, aucun changement requis de leur côté.

### Module Club — création de club + gestion des membres

`ClubService.create()` : après `clubRepository.save(club)`, appelle `membershipService.grant(createdBy, club.getId(), Role.ADMIN)` — même transaction.

Nouveau `ClubMemberController` (`com.sporya.club.controller`), routes sous `/api/v1/clubs/{clubId}/members` :

| Méthode | Route | Body | Réponse | Erreurs |
|---|---|---|---|---|
| POST | `/api/v1/clubs/{clubId}/members` | `{email, role}` | 201, `MemberResponse` | 400 validation, 403 appelant non-ADMIN de ce club, 404 club inconnu, 404 email inconnu |
| GET | `/api/v1/clubs/{clubId}/members` | — | 200, `MemberResponse[]` | 404 club inconnu |

`ClubMemberService` (nouveau, `com.sporya.club.application`) : vérifie `AuthenticatedUser.hasAnyRole(clubId, Role.ADMIN)` avant `POST`, résout l'email en `userId` via un nouveau point d'entrée exposé par Auth (`AuthenticationService.findUserIdByEmail(String)`, lève `UserNotFoundException` existante si absent), puis délègue à `MembershipService.grant(...)`.

Nouvelle exception `ClubAccessDeniedException` (403), gérée par `ClubApiExceptionHandler` existant.

## Tests

`ClubMembershipRbacIT` (nouveau, même pattern Testcontainers que `AuthFlowIT`/`ClubFlowIT`) :
- Créer un club → vérifier que le créateur apparaît en `ADMIN` dans `GET /clubs/{id}/members`.
- ADMIN ajoute un second utilisateur en `COACH` → `201`, apparaît dans la liste.
- Un utilisateur non-ADMIN tente d'ajouter un membre → `403`.
- Ajouter un membre par un email inconnu → `404`.
- Le JWT émis après l'ajout (nouvelle connexion) porte bien le nouveau membership dans son claim `memberships` (décodage direct du token dans le test, pas d'appel HTTP dédié).

Complète `ClubFlowIT` existant si besoin (le créateur de club devient ADMIN — peut aussi être vérifié là si plus naturel).

## Frontend

Bloc "Membres" ajouté à `ClubDetailPage.tsx`, même pattern que le bloc "Équipes" existant :
- Liste des membres (`GET /clubs/{id}/members`), affiche email + rôle (nécessite que `MemberResponse` inclue l'email, pas juste `userId` — jointure côté `ClubMemberService` vers Auth pour l'affichage).
- Formulaire d'ajout (email + select de rôle) → `POST /clubs/{id}/members`.

`frontend/src/lib/api.ts` : nouvelles fonctions `listMembers`, `addMember`, interface `MemberResponse { userId, email, role, createdAt }`.

## Hors périmètre (explicite)

Retrait d'un membre, changement de rôle, invitation par email/lien, rafraîchissement de JWT en direct, vérification d'accès dans Match (viendra avec le module Match lui-même, cet incrément ne fait que poser `AuthenticatedUser.hasAnyRole` comme brique réutilisable).
