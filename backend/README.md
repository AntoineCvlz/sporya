# Backend

Depuis [ADR-017](../docs/adr/ADR-017-monolithe-modulaire.md), Sporya est un **monolithe modulaire** : un seul déployable Spring Boot, `api/`, plutôt qu'un dossier par microservice.

`api/` se construit **un module à la fois**, dans l'ordre de leurs dépendances réelles (voir l'[ordre de construction](../docs/architecture/overview.md#ordre-de-construction-des-modules)) — un module métier est un package Java (`com.sporya.<domaine>`), avec sa propre structure `controller/application/domain/infrastructure` et son propre schéma PostgreSQL, pas un nouveau dossier ici.

Ordre prévu (modules, tous dans `api/src/main/java/com/sporya/`) :

1. `auth` (Phase 6, construit)
2. `club`
3. `match`
4. `statistics` (V2)
5. `notification` (V2)
6. `analytics` (V3)
7. `data-import` (V3)

**AI Service** (V4) est la seule exception : runtime Python/FastAPI différent, il reste un service séparé — il aura son propre dossier `backend/ai-service/` le moment venu (voir [ADR-010](../docs/adr/ADR-010-ai-service-python.md)).
