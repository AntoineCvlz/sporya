# Club module — design

## Contexte

Auth est le seul module construit à ce jour (`backend/api/src/main/java/com/sporya/auth/`). Club est la prochaine étape du roadmap (`docs/architecture/overview.md#ordre-de-construction-des-modules`) : domaine CRUD stable, sert à confirmer la convention de module (structure interne, migration, tests) établie par Auth avant d'attaquer Match, nettement plus complexe (state machine, événements).

Décisions de périmètre validées avant ce document :

- **Pas de RBAC dans cette passe.** `ClubMembership` (qui a quel rôle dans quel club) appartient au schéma `auth` d'après `docs/database/README.md`, mais n'est pas construit ici. Toutes les routes exigent juste un Bearer JWT valide (déjà le comportement par défaut de `SecurityConfig`), aucune vérification de rôle. RBAC arrivera avec le module Match, là où la règle "seuls ADMIN/COACH créent un match" devient réellement nécessaire à appliquer.
- **`Team` n'est pas rattachée à une saison.** `Season` appartient au module Match (pas construit). `Team` a juste un `club_id` ; le rattachement à une saison sera ajouté par une migration ultérieure quand Match introduira `Season`.
- **`StaffMember` hors périmètre.** N'apparaît pas dans la liste des 10 fonctionnalités MVP (`docs/architecture/overview.md#fonctionnalités-mvp`) contrairement à Club/Team/Player. Ajouté dans un incrément séparé si un besoin réel apparaît.

## Architecture backend

Nouveau package `com.sporya.club`, même structure en couches que `com.sporya.auth` (voir ADR-004/ADR-017) :

```
com.sporya.club/
├── controller/          ClubController, TeamController, PlayerController, dto/, ApiExceptionHandler
├── application/         ClubService, TeamService, PlayerService (un par agrégat — TeamService dépend
│                        de ClubRepository pour vérifier le club parent, PlayerService de TeamRepository)
├── domain/               Club, Team, Player, ClubNotFoundException, TeamNotFoundException
└── infrastructure/
    └── persistence/      ClubRepository, TeamRepository, PlayerRepository
```

`ApiExceptionHandler` du module Club est un nouveau `@RestControllerAdvice` propre à ses propres exceptions — pas de partage avec `auth.controller.ApiExceptionHandler` (même logique "dupliquer avant de mutualiser" qu'ADR-004).

Toutes les routes passent par la config Security existante (`anyRequest().authenticated()` par défaut) — aucune modification de `SecurityConfig` nécessaire, seuls Club/Team/Player s'ajoutent aux routes déjà protégées.

## Modèle de données

Schéma Postgres `club` (nouveau, à côté de `auth`) :

```sql
club.clubs   { id UUID PK, name TEXT NOT NULL, country TEXT NOT NULL, created_by UUID NOT NULL, created_at TIMESTAMPTZ NOT NULL }
club.teams   { id UUID PK, name TEXT NOT NULL, club_id UUID NOT NULL REFERENCES club.clubs(id), created_at TIMESTAMPTZ NOT NULL }
club.players { id UUID PK, name TEXT NOT NULL, birthdate DATE NOT NULL, position TEXT NOT NULL, team_id UUID NOT NULL REFERENCES club.teams(id), created_at TIMESTAMPTZ NOT NULL }
```

`created_by` (UUID de l'utilisateur authentifié, extrait du JWT) tracé dès maintenant même sans RBAC — pure métadonnée d'audit, ne bloque rien, prépare le terrain sans construire la permission elle-même. Pas de FK cross-schéma vers `auth.users` (ADR-012) : `created_by` est une référence par ID non contrainte.

**Migration** : un seul fichier `backend/api/src/main/resources/db/migration/V2__create_club_tables.sql`, dans le même dossier plat que `V1__create_users_table.sql` (pas de sous-dossier par module tant qu'il n'y a que 2 modules — réévaluer si ça devient confus). Tables qualifiées explicitement (`CREATE TABLE club.clubs (...)`).

**`application.yml`** : `spring.flyway.schemas: auth,club` (au lieu de `auth` seul). `spring.jpa.properties.hibernate.default_schema` reste `auth` — les nouvelles entités JPA précisent `@Table(schema = "club", name = "...")` explicitement plutôt que de toucher le défaut global (qui reste correct pour `User`).

## API

Toutes sous Bearer JWT (401 si absent/invalide, comportement déjà en place) :

| Méthode | Route | Body | Réponse | Erreurs |
|---|---|---|---|---|
| POST | `/api/v1/clubs` | `{name, country}` | 201, `ClubResponse` | 400 validation |
| GET | `/api/v1/clubs` | — | 200, `ClubResponse[]` | — |
| GET | `/api/v1/clubs/{clubId}` | — | 200, `ClubResponse` | 404 `ClubNotFoundException` |
| POST | `/api/v1/clubs/{clubId}/teams` | `{name}` | 201, `TeamResponse` | 400 validation, 404 club inconnu |
| GET | `/api/v1/clubs/{clubId}/teams` | — | 200, `TeamResponse[]` | 404 club inconnu |
| GET | `/api/v1/teams/{teamId}` | — | 200, `TeamResponse` | 404 `TeamNotFoundException` |
| POST | `/api/v1/teams/{teamId}/players` | `{name, birthdate, position}` | 201, `PlayerResponse` | 400 validation, 404 équipe inconnue |
| GET | `/api/v1/teams/{teamId}/players` | — | 200, `PlayerResponse[]` | 404 équipe inconnue |

`GET /api/v1/clubs` (liste complète, sans filtre) ajouté spécifiquement pour que le frontend puisse naviguer vers un club sans notion de "mes clubs" (qui viendra avec `ClubMembership`).

Pas de PUT/DELETE — hors périmètre MVP (`docs/architecture/overview.md#fonctionnalités-mvp` ne liste que la création).

## Tests

`ClubFlowIT` (Testcontainers Postgres, même schéma que `AuthFlowIT`) : register/login (réutilise le flux Auth existant) → créer club → créer équipe → ajouter joueur → vérifier via les GET (club, liste équipes, liste joueurs). Cas d'erreur couverts : créer une équipe sous un club inexistant (404), ajouter un joueur sous une équipe inexistante (404).

## Frontend

Trois nouvelles pages (`frontend/src/pages/`), même patterns visuels que Login/Register (Card/Input/Label/Button shadcn, thème dark en place) :

- **`ClubsPage.tsx`** (`/clubs`) — liste des clubs (`GET /clubs`) + formulaire de création (`POST /clubs`), chaque club de la liste est un lien vers `/clubs/:clubId`.
- **`ClubDetailPage.tsx`** (`/clubs/:clubId`) — infos du club + liste de ses équipes (`GET /clubs/{id}/teams`) + formulaire de création d'équipe (`POST /clubs/{id}/teams`), chaque équipe est un lien vers `/teams/:teamId`.
- **`TeamDetailPage.tsx`** (`/teams/:teamId`) — infos de l'équipe (`GET /teams/{id}`) + liste de ses joueurs (`GET /teams/{id}/players`) + formulaire d'ajout de joueur (`POST /teams/{id}/players`).

Toutes sous `ProtectedRoute` (déjà en place dans `App.tsx`), routes ajoutées à côté de `/dashboard`. `DashboardPage.tsx` reçoit un lien "Voir les clubs" vers `/clubs`.

**Refactor nécessaire dans `src/lib/api.ts`** : `request()` a actuellement `/api/v1/auth` codé en dur comme base pour tous les appels — généralisé pour accepter un chemin complet (`/api/v1/clubs`, `/api/v1/teams/...`), les fonctions `register`/`login`/`me` existantes passent désormais leur chemin complet. Nouvelles fonctions (`listClubs`, `createClub`, `getClub`, `listTeams`, `createTeam`, `listPlayers`, `createPlayer`) ajoutées dans le même fichier, `accessToken` passé explicitement à chaque appel (même pattern que `me()` aujourd'hui — pas de wrapper d'auth centralisé, pas nécessaire pour 7 fonctions).

## Hors périmètre (explicite)

RBAC/`ClubMembership`, rattachement `Team`↔`Season`, `StaffMember`, update/delete sur les trois entités, pagination sur les listes (pas de volume qui le justifie encore).
