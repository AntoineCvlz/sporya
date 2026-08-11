# Base de données

## Ownership

Chaque service possède son propre schéma PostgreSQL dès sa création (voir [ADR-012](../adr/ADR-012-schema-par-service.md)). Aucune jointure SQL cross-schéma : les références entre données de services différents se font par ID, résolues via appel REST si nécessaire.

## Modèle de données initial

```mermaid
erDiagram
    CLUB ||--o{ TEAM : possede
    TEAM ||--o{ PLAYER : compte
    TEAM ||--o{ STAFF_MEMBER : emploie
    COMPETITION ||--o{ SEASON : organise
    SEASON ||--o{ MATCH : planifie
    TEAM ||--o{ MATCH : "joue (home/away)"
    MATCH ||--o{ MATCH_EVENT : genere
    PLAYER ||--o{ MATCH_EVENT : declenche
    MATCH ||--o{ TEAM_MATCH_STATISTICS : agrege
    MATCH ||--o{ PLAYER_MATCH_STATISTICS : agrege
    PLAYER ||--o{ PLAYER_MATCH_STATISTICS : concerne
    USER ||--o{ CLUB_MEMBERSHIP : a
    CLUB ||--o{ CLUB_MEMBERSHIP : accueille

    CLUB { uuid id; string name; string country }
    TEAM { uuid id; string name; uuid club_id; uuid season_id }
    PLAYER { uuid id; string name; date birthdate; string position }
    USER { uuid id; string email; string password_hash }
    CLUB_MEMBERSHIP { uuid user_id; uuid club_id; string role }
    COMPETITION { uuid id; string name }
    SEASON { uuid id; string label; uuid competition_id }
    MATCH { uuid id; uuid home_team_id; uuid away_team_id; string status; datetime kickoff_at }
    MATCH_EVENT { uuid id; uuid match_id; string type; int minute; uuid player_id }
    PLAYER_MATCH_STATISTICS { uuid player_id; uuid match_id; int goals; int assists; int yellow_cards }
    TEAM_MATCH_STATISTICS { uuid team_id; uuid match_id; int possession; int shots }
```

## Répartition par schéma (V1)

| Schéma | Service | Entités |
|---|---|---|
| `auth` | Auth Service | `User`, `ClubMembership` |
| `club` | Club Service | `Club`, `Team`, `Player`, `StaffMember` |
| `match` | Match Service | `Competition`, `Season`, `Match`, `MatchEvent` |

`PlayerMatchStatistics` / `TeamMatchStatistics` rejoignent un schéma `statistics` lors de la construction du Statistics Service (V2).

## Migrations

Convention à établir à la Phase 6 (premier service) : Flyway, une migration versionnée par changement de schéma, un dossier de migrations par service (`services/<nom>-service/src/main/resources/db/migration/`).
