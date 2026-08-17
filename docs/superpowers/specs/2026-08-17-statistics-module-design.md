# Statistics — design

## Contexte

Statistics est le module #4 de l'ordre de construction (`docs/architecture/overview.md#ordre-de-construction-des-modules`), justifié une fois qu'il "y a un historique multi-matchs à agréger" — condition désormais remplie, le module Match (Competition/Season/Match/MatchEvent, state machine, score dérivé) venant d'être livré et vérifié.

Le module Match expose déjà deux endpoints dérivés calculés à la volée depuis les `MatchEvent` bruts : `GET /players/{id}/stats` (totaux all-time d'un joueur) et `GET /teams/{id}/form` (5 derniers résultats d'une équipe). Statistics ne les remplace pas — il apporte autre chose : des agrégats **persistés**, **scopés par saison**, construits une fois pour toutes quand un match se termine, plutôt que recalculés à chaque lecture. C'est la fondation attendue par Analytics (V3, `docs/architecture/overview.md#ordre-de-construction-des-modules`) pour agréger au-delà d'une seule saison.

Décisions de périmètre validées avant ce document :

- **Coexistence avec les endpoints Match existants**, pas de remplacement — périmètres différents (all-time recalculé vs. saison persistée).
- **Déclenchement par événement in-process**, pas par appel direct : `Match.finish()` publie `MatchFinishedEvent` (Spring `ApplicationEventPublisher`) ; Statistics écoute avec `@TransactionalEventListener(phase = AFTER_COMMIT)`, dans une transaction séparée après le commit de `finish()`. Pas de `@Async` — écouteur synchrone, pas de pool de threads supplémentaire (contrainte VPS).
- **`TeamMatchStatistics` : toujours une ligne par équipe et par match terminé** (domicile + extérieure) — une équipe participe par définition. **`PlayerMatchStatistics` : une ligne uniquement pour les joueurs ayant au moins un `MatchEvent`** dans ce match — pas de notion de feuille de match/composition, impossible de déduire une participation sans événement.
- **Pas de champs `assists`/`possession`/`shots`** (esquissés dans le tout premier schéma DB avant que Match existe réellement) — aucun type de `MatchEvent` ne les capture, donc impossibles à dériver honnêtement.
- **`season_id` stocké directement sur les deux tables** (y compris `PlayerMatchStatistics`, connu via `MatchFinishedEvent`) — évite un appel cross-module vers Match à la lecture, une requête SQL directe suffit pour les deux agrégats.
- **Premier incrément : les agrégats par saison (joueur et équipe)**, pas seulement la persistance brute — un historique existe déjà (matchs déjà joués en local/tests), donc l'agrégation a un sens immédiat.
- **Pas de validation cross-module** de `playerId`/`teamId`/`seasonId` sur les endpoints de lecture (même choix que Match) — inconnu retourne un agrégat à zéro plutôt qu'un 404.

## Modèle de données

Nouveau schéma Postgres `statistics` :

```sql
statistics.player_match_statistics {
  id           UUID PK,
  player_id    UUID NOT NULL,  -- club.players, pas de FK cross-schéma (ADR-012)
  match_id     UUID NOT NULL,  -- match.matches
  team_id      UUID NOT NULL,  -- club.teams
  season_id    UUID NOT NULL,  -- match.seasons
  goals        INT NOT NULL,
  yellow_cards INT NOT NULL,
  red_cards    INT NOT NULL,
  created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
  UNIQUE (player_id, match_id)
}

statistics.team_match_statistics {
  id            UUID PK,
  team_id       UUID NOT NULL,  -- club.teams
  match_id      UUID NOT NULL,  -- match.matches
  season_id     UUID NOT NULL,  -- match.seasons
  goals_for     INT NOT NULL,
  goals_against INT NOT NULL,
  result        VARCHAR(10) NOT NULL,  -- WIN | DRAW | LOSS
  created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
  UNIQUE (team_id, match_id)
}
```

**Migration** : `backend/api/src/main/resources/db/migration/V5__create_statistics_tables.sql`, dossier plat existant, `CREATE SCHEMA IF NOT EXISTS statistics;` explicite en première instruction (même précaution que `V4`, voir sa note). `spring.flyway.schemas` passe à `auth,club,match,statistics`.

## Architecture backend

### Module Statistics — nouveau package `com.sporya.statistics`

```
com.sporya.statistics/
├── domain/
│   ├── PlayerMatchStatistics.java, TeamMatchStatistics.java   (entités)
│   └── MatchOutcome.java                (enum WIN, DRAW, LOSS — même valeurs que MatchResult de Match, copie propre, ADR-004)
├── application/
│   ├── MatchFinishedListener.java       (@Component, @TransactionalEventListener(phase = AFTER_COMMIT))
│   ├── PlayerStatisticsService.java     (seasonStatsFor(playerId, seasonId))
│   └── TeamStatisticsService.java       (seasonStatsFor(teamId, seasonId))
├── infrastructure/persistence/
│   ├── PlayerMatchStatisticsRepository.java  (+ sumBy...ForPlayerAndSeason)
│   └── TeamMatchStatisticsRepository.java    (+ sumBy...ForTeamAndSeason, countByResult)
└── controller/
    ├── PlayerStatisticsController.java, TeamStatisticsController.java
    └── dto/ (PlayerSeasonStatisticsResponse, TeamSeasonStatisticsResponse)
```

### `MatchFinishedEvent` — nouveau, publié par Match

`backend/api/src/main/java/com/sporya/match/domain/MatchFinishedEvent.java` : `record MatchFinishedEvent(UUID matchId, UUID homeTeamId, UUID awayTeamId, UUID seasonId)`. `MatchService.finish()` (déjà `@Transactional`) publie cet événement via `ApplicationEventPublisher` juste avant de retourner, une fois le statut `FINISHED` sauvegardé.

### `MatchFinishedListener` — construit les statistiques

Sur réception de `MatchFinishedEvent` (après commit) :
1. Calcule `homeScore`/`awayScore` (même logique que `MatchService.toResponse` — appel à `MatchEventRepository` du module Match, cross-module direct comme déjà pratiqué).
2. Déduit `result` pour chaque équipe (WIN/DRAW/LOSS, même règle que `MatchService.toRecentResult`) → sauvegarde 2 `TeamMatchStatistics` (domicile, extérieure).
3. Liste les `MatchEvent` du match (`MatchEventRepository.findByMatchIdOrderByMinuteAsc`), regroupe par `playerId`, compte `GOAL_SCORED`/`YELLOW_CARD`/`RED_CARD` par joueur → sauvegarde une `PlayerMatchStatistics` par joueur apparu.

Léger doublon avec le calcul de score/résultat déjà présent dans `MatchService` — accepté (ADR-004, dupliquer avant de partager), les deux vivent dans des modules différents avec des besoins qui divergeront probablement (Statistics n'a pas besoin de la logique de state machine, seulement du résultat final).

## Routes

| Méthode | Route | Calcul | Réponse | Erreurs |
|---|---|---|---|---|
| GET | `/api/v1/players/{playerId}/seasons/{seasonId}/statistics` | Somme des `goals`/`yellow_cards`/`red_cards` et **compte des lignes** `PlayerMatchStatistics` du joueur pour cette saison (`matchesPlayed` = nombre de lignes, une ligne = un match où il a au moins un événement) | `{playerId, seasonId, goals, yellowCards, redCards, matchesPlayed}` (zéros si rien trouvé) | — |
| GET | `/api/v1/teams/{teamId}/seasons/{seasonId}/statistics` | Somme/compte des `TeamMatchStatistics` de l'équipe pour cette saison | `{teamId, seasonId, wins, draws, losses, goalsFor, goalsAgainst}` (zéros si rien trouvé) | — |

## Tests

`StatisticsFlowIT` (nouveau, même pattern Testcontainers que `MatchFlowIT`) :
- Terminer un match avec buts/cartons pour un joueur → les endpoints d'agrégation reflètent bien les valeurs (pas d'accès direct DB, uniquement via l'API).
- Deux matchs terminés dans la même saison pour la même équipe (1 victoire, 1 défaite) → `wins=1, losses=1, draws=0`, buts marqués/encaissés cumulés.
- Un joueur avec des buts sur 2 matchs différents de la même saison → agrégat cumulé, `matchesPlayed=2`.
- Un match d'une autre saison n'apparaît pas dans l'agrégat de la première.
- Un match `SCHEDULED`/`LIVE` (jamais terminé) ne génère aucune ligne — les endpoints renvoient des zéros tant qu'aucun match de la saison n'est `FINISHED`.

## Hors périmètre (explicite)

Retrait/recalcul d'une ligne si un match `FINISHED` est un jour rouvert (aucun mécanisme de correction), agrégats multi-saisons ou toutes compétitions confondues (Analytics, V3), classement/tableau de compétition, `assists`/`possession`/`shots` (aucune donnée source), notifications de mise à jour de stats (Notification, V2 mais increment séparé), UI frontend (cet incrément est backend uniquement — l'affichage viendra avec le dashboard équipe existant ou un futur incrément, à décider séparément).
