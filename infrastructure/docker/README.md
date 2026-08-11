# Docker

Le `docker-compose.yml` racine (Phase 2) démarre le socle local :

| Service | Rôle | Port local |
|---|---|---|
| `postgres` | Base de données (schémas créés par service, [ADR-012](../../docs/adr/ADR-012-schema-par-service.md)) | `5432` |
| `adminer` | Client web léger pour inspecter la base en développement | `8081` |

```bash
cp .env.example .env
docker compose up -d
docker compose ps        # vérifier que postgres est "healthy"
docker compose down -v   # arrêt + suppression des données
```

Chaque microservice ajoutera son propre `Dockerfile` (multi-stage) dans `services/<nom>-service/` au moment de sa construction, et un `service:` correspondant sera ajouté ici au `docker-compose.yml` — en copiant le gabarit établi par Auth Service (premier service construit, voir [ADR-004](../../docs/adr/ADR-004-service-de-reference.md)). Kafka et Redis ne sont ajoutés que lorsqu'un besoin réel apparaît ([ADR-005](../../docs/adr/ADR-005-kafka.md), [ADR-006](../../docs/adr/ADR-006-redis.md)).
