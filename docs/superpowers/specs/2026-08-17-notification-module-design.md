# Notification — design

## Contexte

Notification est le module #5 de l'ordre de construction (`docs/architecture/overview.md#ordre-de-construction-des-modules`), justifié par "plusieurs consommateurs et des préférences utilisateur, mais reste un module, pas un service réseau". Il dépend de Match via événement in-process — même mécanisme que Statistics, qui vient d'établir et de valider le pattern `@TransactionalEventListener(AFTER_COMMIT)` + `@Transactional(REQUIRES_NEW)` sur `MatchFinishedEvent`.

La roadmap résumée groupe "Statistics, Notification ; Redis ; WebSocket" pour V2, mais ni Redis ni WebSocket ne sont introduits dans cet incrément — décision explicite ci-dessous, cohérente avec le choix déjà fait sur Match (suivi live par rafraîchissement manuel côté frontend, pas de push temps réel) et avec la contrainte VPS (~1.8Gi RAM) qui a déjà fait annuler K3s et pivoter vers un monolithe (voir ADR-017/ADR-018).

Décisions de périmètre validées avant ce document :

- **Ni Redis ni WebSocket** — persistance Postgres classique (comme tous les autres modules), consultation à la demande (`GET`, le frontend rafraîchit manuellement s'il le souhaite). Pas de nouvelle dépendance infra.
- **Destinataires** : tous les membres (n'importe quel rôle) du club domicile **et** du club extérieur du match — réutilise `ClubMembership`/`MembershipService.listForClub` existant, pas de nouvelle notion d'abonnement. Un utilisateur membre des deux clubs ne reçoit qu'une seule notification (dédoublonnage par `userId`).
- **Pas de préférences utilisateur** dans cet incrément — tout membre reçoit toutes les notifications, pas de désactivation possible. La justification architecturale du module (pourquoi ce n'est pas juste un champ ailleurs) reste valable même sans préférences implémentées tout de suite, même raisonnement que ClubMembership RBAC posé avant que Match en ait besoin.
- **Un seul déclencheur dans cet incrément : `MatchFinishedEvent`** (déjà publié par `Match.finish()`, déjà consommé par Statistics). Pas de notification en direct sur `GOAL_SCORED`.
- **Champ `type` conservé sur `Notification`** dès cet incrément malgré une seule valeur possible (`MATCH_FINISHED`) — le module est architecturalement justifié pour porter "des événements importants" au sens large ; ajouter le discriminant maintenant coûte une colonne, l'ajouter après coup coûterait une migration.
- **Champs spécifiques au match directement sur la table** (`matchId`, `homeTeamId`, `awayTeamId`, `homeScore`, `awayScore`) — pas de payload JSON générique. Généraliser seulement si un deuxième type de notification apparaît réellement (cohérent avec la convention déjà en place dans ce projet : dupliquer/spécialiser d'abord, extraire ensuite).
- **État lu/non-lu** (`read: boolean`) — une notification peut être marquée comme lue par son destinataire, pas par un tiers.

## Modèle de données

Nouveau schéma Postgres `notification` :

```sql
notification.notifications {
  id            UUID PK,
  user_id       UUID NOT NULL,  -- auth.users, pas de FK cross-schéma (ADR-012)
  type          VARCHAR(30) NOT NULL,  -- MATCH_FINISHED (seule valeur pour l'instant)
  match_id      UUID NOT NULL,  -- match.matches
  home_team_id  UUID NOT NULL,  -- club.teams
  away_team_id  UUID NOT NULL,  -- club.teams
  home_score    INT NOT NULL,
  away_score    INT NOT NULL,
  read          BOOLEAN NOT NULL DEFAULT false,
  created_at    TIMESTAMPTZ NOT NULL DEFAULT now()
}
```

**Migration** : `backend/api/src/main/resources/db/migration/V6__create_notification_tables.sql`, dossier plat existant, `CREATE SCHEMA IF NOT EXISTS notification;` explicite en première instruction (même précaution que V4/V5). `spring.flyway.schemas` passe à `auth,club,match,statistics,notification`.

## Architecture backend

### Module Notification — nouveau package `com.sporya.notification`

```
com.sporya.notification/
├── domain/
│   ├── Notification.java           (entité)
│   ├── NotificationType.java       (enum : MATCH_FINISHED)
│   └── NotificationNotFoundException.java
├── application/
│   ├── MatchFinishedNotifier.java  (@Component, @TransactionalEventListener(AFTER_COMMIT) + @Transactional(REQUIRES_NEW))
│   └── NotificationService.java    (listForUser(userId), markRead(userId, notificationId))
├── infrastructure/persistence/
│   └── NotificationRepository.java (+ findByUserIdOrderByCreatedAtDesc, findByIdAndUserId)
└── controller/
    ├── NotificationController.java
    ├── dto/ (NotificationResponse)
    └── NotificationApiExceptionHandler.java (copie propre, ADR-004)
```

### `MatchFinishedNotifier` — construit les notifications

Sur réception de `MatchFinishedEvent` (après commit, transaction propre — même piège que Statistics : sans `@Transactional(REQUIRES_NEW)` en plus de `@TransactionalEventListener(AFTER_COMMIT)`, les écritures ne sont jamais réellement committées) :
1. Résout `homeClubId`/`awayClubId` via `TeamService.get(homeTeamId).clubId()` / `.get(awayTeamId).clubId()` (Club, appel Java direct — même pattern que Match → Club).
2. Récupère les membres via `MembershipService.listForClub(homeClubId)` + `MembershipService.listForClub(awayClubId)` (Auth, déjà existant — utilisé aujourd'hui par `ClubMemberService`).
3. Déduplique les `userId` (`Set<UUID>` ou équivalent) — un membre des deux clubs ne reçoit qu'une notification.
4. Calcule `homeScore`/`awayScore` via `MatchEventService.listForMatch(matchId)` (même logique que Statistics — duplication acceptée, ADR-004).
5. Sauvegarde une `Notification` par `userId` unique.

### `NotificationService` — lecture et marquage

`listForUser(UUID userId): List<NotificationResponse>` — toutes les notifications de l'utilisateur, triées par `createdAt` décroissant, pas de pagination dans ce périmètre.

`markRead(UUID userId, UUID notificationId): NotificationResponse` — cherche par `(id, userId)` ensemble ; si rien ne correspond (notification inexistante OU appartenant à un autre utilisateur), lève `NotificationNotFoundException` (404) — indistinguable volontairement, évite de révéler l'existence d'une notification à quelqu'un d'autre que son destinataire.

## Routes

| Méthode | Route | Auth | Réponse | Erreurs |
|---|---|---|---|---|
| GET | `/api/v1/notifications` | Bearer JWT, scope = appelant (`AuthenticatedUser`, pas de `userId` dans l'URL — même pattern que `/auth/me`) | 200, `NotificationResponse[]` | — |
| POST | `/api/v1/notifications/{id}/read` | idem | 200, `NotificationResponse` mis à jour | 404 (inconnue ou pas la sienne) |

`NotificationResponse(UUID id, NotificationType type, UUID matchId, UUID homeTeamId, UUID awayTeamId, int homeScore, int awayScore, boolean read, Instant createdAt)`.

## Tests

`NotificationFlowIT` (nouveau, même pattern Testcontainers que `StatisticsFlowIT`) :
- Terminer un match dont les deux clubs ont chacun un membre distinct → chacun des deux voit la notification via `GET /notifications`.
- Un utilisateur membre des deux clubs (home et away) → une seule notification, pas deux.
- Marquer une notification lue → `read=true` en relecture.
- Marquer la notification d'un autre utilisateur → 404.
- Un match jamais terminé ne génère aucune notification.

## Hors périmètre (explicite)

Redis, WebSocket, push temps réel, préférences utilisateur (opt-out), autres déclencheurs que `MatchFinishedEvent` (ex. but en direct), pagination, suppression d'une notification, notion d'abonnement indépendante du rôle/club.
