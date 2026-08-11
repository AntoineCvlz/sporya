# Services

Ce dossier se remplit **au fil de la construction réelle** des microservices, un à la fois — voir [ADR-003](../docs/adr/ADR-003-microservices-des-le-mvp.md) et l'[ordre de construction](../docs/architecture/overview.md#ordre-de-construction-des-microservices).

Aucun service n'est encore construit (Phase 1 — Repository). Le premier sera `auth-service/` (Phase 6), qui sert de gabarit pour les suivants ([ADR-004](../docs/adr/ADR-004-service-de-reference.md)).

Ordre prévu :

1. `auth-service/`
2. `club-service/`
3. `match-service/`
4. `statistics-service/` (V2)
5. `notification-service/` (V2)
6. `analytics-service/` (V3)
7. `data-import-service/` (V3)
8. `ai-service/` (V4)
