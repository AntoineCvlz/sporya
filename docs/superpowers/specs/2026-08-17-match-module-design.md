# Match — design

## Contexte

Match est le module #3 de l'ordre de construction (`docs/architecture/overview.md#ordre-de-construction-des-modules`), qualifié de "référence de complexité métier" : premier module avec une state machine, des événements, et un score dérivé plutôt qu'un champ modifiable. Il dépend de Club (appel Java direct) et du RBAC club-membership tout juste livré (`AuthenticatedUser.hasAnyRole`, voir `docs/superpowers/specs/2026-08-14-club-membership-rbac-design.md`).

Cet incrément couvre les points 5, 6 et 7 du MVP (`docs/architecture/overview.md#fonctionnalités-mvp`) : créer un match entre deux équipes, ajouter des événements de match (but, carton, remplacement), score calculé automatiquement à partir des événements — ainsi que les règles métier associées (state machine stricte, carton rouge, contrôle d'accès par club).

Décisions de périmètre validées avant ce document :

- **Tout le MVP Match d'un coup** : `Competition`, `Season`, `Match`, `MatchEvent`, state machine, score dérivé — un seul incrément, un seul spec/plan (contrairement à Club → ClubMembership RBAC qui avaient été scindés).
- **Pas de lien équipe↔saison** (`TeamSeasonRegistration` envisagé puis écarté) : `homeTeamId`/`awayTeamId` référencent `club.teams` directement, de façon permanente, sans notion de saison — comme aujourd'hui. `Season` ne sert qu'à regrouper les matchs (calendrier de compétition), pas à scoper la composition d'une équipe. L'historique "un joueur appartient à une équipe pour une saison donnée" (transferts, `docs/architecture/overview.md#règles-métier-principales`) est **hors périmètre**, reporté à un futur incrément — YAGNI tant qu'aucun cas d'usage réel (changement d'équipe en cours de saison) ne se présente.
- **Un match est créé par un `ADMIN`/`COACH` du club domicile uniquement.** Les transitions d'état et l'ajout d'événements sont ouverts à `ADMIN`/`COACH` du club domicile **ou** extérieur (les deux clubs ont un intérêt à faire progresser le match).
- **Les transitions d'état passent par des endpoints dédiés** (`/start`, `/half-time`, `/resume`, `/finish`), pas par des `MatchEvent` techniques — sépare le pilotage du match des événements métier (but/carton/remplacement).
- **4 types de `MatchEvent`** dans ce périmètre : `GOAL_SCORED`, `YELLOW_CARD`, `RED_CARD`, `SUBSTITUTION`.
- **Un carton rouge invalide uniquement les futurs `GOAL_SCORED`** du joueur concerné sur ce match — `YELLOW_CARD`/`SUBSTITUTION` restent acceptés côté API après un carton rouge (pas de sens métier à les bloquer explicitement, sans impact puisque le joueur ne joue plus).
- **Le score est calculé à la lecture, jamais stocké** — `team_id` est dénormalisé sur `MatchEvent` à l'écriture (résolu une fois via Club) pour éviter un appel cross-module à chaque lecture du score.
- **Le `playerId` d'un événement est validé** contre le roster du match (doit appartenir à l'équipe domicile ou extérieure) via un appel Java vers Club.
- **`Competition`/`Season` : création + liste + lecture uniquement**, pas d'update/delete — même périmètre que Club V1.
- **Frontend inclus** : pages de liste/création de match et de suivi du match en direct (transitions, événements, score).

## Modèle de données

Nouveau schéma Postgres `match` (voir `docs/database/README.md#répartition-par-schéma-v1`) :

```sql
match.competitions {
  id         UUID PK,
  name       VARCHAR(255) NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
}

match.seasons {
  id             UUID PK,
  label          VARCHAR(255) NOT NULL,
  competition_id UUID NOT NULL REFERENCES match.competitions(id),
  created_at     TIMESTAMPTZ NOT NULL DEFAULT now()
}

match.matches {
  id            UUID PK,
  season_id     UUID NOT NULL REFERENCES match.seasons(id),
  home_team_id  UUID NOT NULL,  -- club.teams, pas de FK cross-schéma (ADR-012)
  away_team_id  UUID NOT NULL,  -- club.teams
  status        VARCHAR(20) NOT NULL,  -- SCHEDULED | LIVE | HALF_TIME | FINISHED
  kickoff_at    TIMESTAMPTZ NOT NULL,
  created_at    TIMESTAMPTZ NOT NULL DEFAULT now()
}

match.match_events {
  id         UUID PK,
  match_id   UUID NOT NULL REFERENCES match.matches(id),
  type       VARCHAR(20) NOT NULL,  -- GOAL_SCORED | YELLOW_CARD | RED_CARD | SUBSTITUTION
  minute     INT NOT NULL,
  player_id  UUID NOT NULL,  -- club.players, pas de FK cross-schéma
  team_id    UUID NOT NULL,  -- dénormalisé : home_team_id ou away_team_id du match, résolu à l'écriture
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
}
```

**Migration** : `backend/api/src/main/resources/db/migration/V4__create_match_tables.sql`, dossier plat existant. `spring.flyway.schemas` passe à `auth,club,match`.

## Architecture backend

### Module Match — nouveau package `com.sporya.match`

```
com.sporya.match/
├── domain/
│   ├── Competition.java, Season.java, Match.java, MatchEvent.java   (entités)
│   ├── MatchStatus.java                (enum SCHEDULED, LIVE, HALF_TIME, FINISHED)
│   ├── MatchEventType.java             (enum GOAL_SCORED, YELLOW_CARD, RED_CARD, SUBSTITUTION)
│   ├── CompetitionNotFoundException.java, SeasonNotFoundException.java, MatchNotFoundException.java
│   ├── InvalidMatchStateException.java (409 — mauvaise transition, ou event ajouté hors LIVE)
│   ├── MatchAccessDeniedException.java (403 — appelant non ADMIN/COACH du club domicile ou extérieur)
│   ├── PlayerNotInMatchException.java  (400 — playerId hors roster home/away)
│   └── RedCardViolationException.java  (409 — GOAL_SCORED pour un joueur déjà expulsé)
├── application/
│   ├── CompetitionService.java   (create, list, get)
│   ├── SeasonService.java        (create, list, get — valide competitionId via CompetitionService)
│   ├── MatchService.java         (create, get, list, start, halfTime, resume, finish, score(matchId))
│   └── MatchEventService.java    (add, listForMatch)
├── infrastructure/persistence/
│   ├── CompetitionRepository.java, SeasonRepository.java, MatchRepository.java
│   └── MatchEventRepository.java  (+ countByMatchIdAndTeamIdAndTypeAndPlayerId pour la règle carton rouge, countByMatchIdAndTeamIdAndType pour le score)
└── controller/
    ├── CompetitionController.java, SeasonController.java, MatchController.java, MatchEventController.java
    ├── dto/ (Create*/​*Response records)
    └── MatchApiExceptionHandler.java  (copie propre au module, ADR-004)
```

### Intégration avec Club (appel Java direct, sens de dépendance préservé : Match → Club)

- `MatchService.create` résout `homeTeamId`/`awayTeamId` via `TeamService.get(UUID)` (existant, `com.sporya.club.application.TeamService`) — vérifie leur existence (`TeamNotFoundException`, déjà gérée globalement par `ClubApiExceptionHandler`) et récupère le `clubId` de l'équipe domicile pour l'autorisation.
- `MatchEventService.add` résout le `playerId` via un nouveau `PlayerService.get(UUID playerId): PlayerResponse` (à ajouter côté Club, avec une nouvelle `PlayerNotFoundException` — même pattern que `AuthenticationService.findUserIdByEmail` ajouté pour Club au moment du RBAC). Le `teamId` du joueur détermine son camp (`home` ou `away`) et est dénormalisé sur le `MatchEvent`.
- L'autorisation (`AuthenticatedUser.hasAnyRole(clubId, Role.ADMIN, Role.COACH)`) se fait sur le(s) `clubId` résolu(s) via `TeamService.get(homeTeamId).clubId()` / `.get(awayTeamId).clubId()` — jamais de rôle vérifié directement sur un `teamId`.

### `MatchService` — state machine et score

Transitions valides uniquement dans le sens `SCHEDULED → LIVE → HALF_TIME → LIVE (resume) → FINISHED` ; toute autre demande lève `InvalidMatchStateException` (409). `MatchEventService.add` lève la même exception si `match.status != LIVE`.

`MatchService.score(matchId)` : `homeScore = matchEventRepository.countByMatchIdAndTeamIdAndType(matchId, match.homeTeamId(), GOAL_SCORED)`, symétrique pour `awayScore` — inclus dans `MatchResponse`, jamais un champ stocké sur `Match`.

### `MatchEventService.add` — séquence de validation

1. Le match existe et est `LIVE` (sinon `MatchNotFoundException` / `InvalidMatchStateException`).
2. L'appelant est `ADMIN`/`COACH` du club domicile ou extérieur (sinon `MatchAccessDeniedException`).
3. Le joueur existe (`PlayerService.get`, sinon `PlayerNotFoundException`, gérée globalement).
4. Le `teamId` du joueur correspond à `homeTeamId` ou `awayTeamId` du match (sinon `PlayerNotInMatchException`).
5. Si `type == GOAL_SCORED`, aucun `RED_CARD` préalable pour ce joueur sur ce match (sinon `RedCardViolationException`).
6. Sauvegarde du `MatchEvent` avec le `teamId` résolu à l'étape 4.

## Routes

| Méthode | Route | Body | Réponse | Autorisation | Erreurs |
|---|---|---|---|---|---|
| POST | `/api/v1/competitions` | `{name}` | 201, `CompetitionResponse` | Authentifié | 400 validation |
| GET | `/api/v1/competitions`, `/api/v1/competitions/{id}` | — | 200 | Authentifié | 404 (get) |
| POST | `/api/v1/competitions/{competitionId}/seasons` | `{label}` | 201, `SeasonResponse` | Authentifié | 400, 404 compétition inconnue |
| GET | `/api/v1/competitions/{competitionId}/seasons`, `/api/v1/seasons/{seasonId}` | — | 200 | Authentifié | 404 |
| POST | `/api/v1/matches` | `{seasonId, homeTeamId, awayTeamId, kickoffAt}` | 201, `MatchResponse` | `ADMIN`/`COACH` club domicile | 400, 403, 404 saison/équipe inconnue |
| GET | `/api/v1/matches`, `/api/v1/matches/{id}` | — | 200, `MatchResponse` (avec `homeScore`/`awayScore`) | Authentifié | 404 (get) |
| POST | `/api/v1/matches/{id}/start`, `/half-time`, `/resume`, `/finish` | — | 200, `MatchResponse` mis à jour | `ADMIN`/`COACH` club domicile ou extérieur | 403, 404, 409 transition invalide |
| POST | `/api/v1/matches/{id}/events` | `{type, minute, playerId}` | 201, `MatchEventResponse` | `ADMIN`/`COACH` club domicile ou extérieur | 400 joueur hors roster, 403, 404, 409 match non `LIVE` ou carton rouge déjà présent |
| GET | `/api/v1/matches/{id}/events` | — | 200, `MatchEventResponse[]` | Authentifié | 404 |

Pas de contrôle d'accès particulier sur `Competition`/`Season` (référentiel partagé, comme `GET /clubs` aujourd'hui) — seuls `Match`/`MatchEvent` portent le RBAC, conformément à la règle métier ("Seuls ADMIN/COACH d'un club créent un match ou ajoutent des événements pour ses équipes").

Pas d'escalade automatique (deux `YELLOW_CARD` ne produisent pas un `RED_CARD` implicite) : chaque carton est un événement indépendant dans ce périmètre — hors périmètre explicite ci-dessous.

## Tests

`MatchFlowIT` (nouveau, même pattern Testcontainers que `ClubFlowIT`), qui grandit au fil de l'implémentation :
- Créer competition → season → match (`SCHEDULED`) → vérifier get/list.
- Créer un match sans être ADMIN/COACH du club domicile → 403.
- Séquence de transition complète `start → half-time → resume → finish`, avec vérification du statut à chaque étape.
- Transition invalide (ex. `finish` sur un match `SCHEDULED`) → 409.
- Ajouter un `GOAL_SCORED` hors `LIVE` → 409.
- Ajouter un `GOAL_SCORED` pour un joueur hors roster (ni home ni away) → 400.
- Score dérivé correct après plusieurs `GOAL_SCORED` des deux côtés.
- `RED_CARD` puis tentative de `GOAL_SCORED` pour le même joueur → 409 ; `YELLOW_CARD`/`SUBSTITUTION` après `RED_CARD` restent acceptés.

## Frontend

- **`CompetitionsPage`** (`/competitions`) : liste des compétitions, création (nom), création de saison sous une compétition (label) — même pattern que `ClubsPage`. Nécessaire pour peupler le sélecteur de saison de `MatchesPage`.
- **`MatchesPage`** (`/matches`) : liste des matchs, formulaire de création (saison, équipe domicile, équipe extérieure, date/heure de coup d'envoi).
- **`MatchDetailPage`** (`/matches/:matchId`) : statut courant, boutons de transition affichés selon l'état (`start` si `SCHEDULED`, `half-time`/`finish` si `LIVE`, `resume` si `HALF_TIME`), score affiché, formulaire d'ajout d'événement (type, minute, joueur), timeline des événements.

`frontend/src/lib/api.ts` : nouvelles fonctions `listCompetitions`, `createCompetition`, `listSeasons`, `createSeason`, `listMatches`, `createMatch`, `getMatch`, `startMatch`, `halfTimeMatch`, `resumeMatch`, `finishMatch`, `listMatchEvents`, `addMatchEvent`, et les interfaces `CompetitionResponse`, `SeasonResponse`, `MatchResponse` (avec `homeScore`/`awayScore`), `MatchEventResponse`.

## Hors périmètre (explicite)

Lien équipe↔saison et historique de transfert joueur (`TeamSeasonRegistration` ou équivalent), update/delete sur `Competition`/`Season`/`Match`, annulation d'un événement, modification a posteriori du statut d'un match terminé, escalade automatique double-carton-jaune → rouge, Statistics agrégées (V2 — `PlayerMatchStatistics`/`TeamMatchStatistics`), notifications (V2), WebSocket pour le suivi live (le frontend poll/rafraîchit manuellement dans ce périmètre).
