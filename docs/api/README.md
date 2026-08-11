# API

Chaque service publie un contrat OpenAPI, tenu à jour avant que l'implémentation ne diverge (voir [`docs/conventions.md`](../conventions.md)).

## Convention

- Un fichier par service : `docs/api/<nom-service>.yaml` (ex. `auth-service.yaml`).
- Généré depuis les annotations Spring (springdoc-openapi) puis exporté ici pour référence versionnée, ou maintenu manuellement si l'écart avec le code doit être visible en revue.
- Toutes les routes sont versionnées (`/api/v1/...`).

## Exemples de routes cibles (à titre indicatif — non implémentées)

```text
GET    /api/v1/teams
GET    /api/v1/teams/{id}
POST   /api/v1/teams
PUT    /api/v1/teams/{id}
DELETE /api/v1/teams/{id}

GET    /api/v1/matches
GET    /api/v1/matches/{id}
POST   /api/v1/matches

GET    /api/v1/players/{id}/statistics
GET    /api/v1/teams/{id}/statistics
```

Aucun contrat n'est encore publié — le premier arrivera avec Auth Service (Phase 6).
