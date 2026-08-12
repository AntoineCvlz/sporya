# Auth Service

Service de référence (gabarit) — voir [ADR-004](../../docs/adr/ADR-004-service-de-reference.md).

## Responsabilité

Identité, authentification, rôles par club ([ADR-011](../../docs/adr/ADR-011-rbac-par-club.md)), émission de JWT ([ADR-013](../../docs/adr/ADR-013-jwt-stateless.md)).

## État actuel

Squelette minimal (Phase 6) : aucune logique métier pour l'instant. Objectif de cet incrément : prouver toute la chaîne de déploiement (Docker → CI → registry → K3s → Ingress → TLS → logs/métriques), pas encore l'inscription/connexion. La première migration Flyway (`db/migration/`) et les entités (`User`, `ClubMembership`) arrivent avec le prochain incrément.

Schéma PostgreSQL : `auth` (voir [ADR-012](../../docs/adr/ADR-012-schema-par-service.md) et [`docs/database/README.md`](../../docs/database/README.md)), créé automatiquement par Flyway au démarrage (`spring.flyway.create-schemas`).

## Lancer en local

Nécessite le socle Postgres du repo (`docker compose up postgres` à la racine), puis :

```bash
./mvnw spring-boot:run
```

Variables d'environnement (valeurs par défaut alignées avec `.env.example` à la racine) : `DB_HOST`, `DB_PORT`, `DB_NAME`, `DB_USER`, `DB_PASSWORD`.

## Vérifier

```bash
./mvnw verify                       # tests (Testcontainers démarre un Postgres jetable)
curl localhost:8080/actuator/health
curl localhost:8080/actuator/prometheus
```

## Observabilité

Logs JSON structurés (stdout), en-tête `X-Correlation-Id` porté/généré par requête (`CorrelationIdFilter`) — voir [`docs/conventions.md`](../../docs/conventions.md#observabilité).
