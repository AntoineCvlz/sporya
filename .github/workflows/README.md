# Pipelines CI/CD

## En place aujourd'hui

- **`repo-checks.yml`** — tourne sur chaque push/PR (aucune dépendance à un service) : scan de secrets (gitleaks), lint des workflows (actionlint), validation de `docker-compose.yml`.
- **`service-ci.yml`** — reusable workflow (`workflow_call`) pour un déployable Java/Maven : lint, tests, dependency check, build, image Docker, scan Trivy, push `ghcr.io` sur push vers `main` (voir [ADR-016](../../docs/adr/ADR-016-ghcr-registry.md)).
- **`api.yml`** — invoque `service-ci.yml` pour `backend/api`, le monolithe modulaire (voir [ADR-017](../../docs/adr/ADR-017-monolithe-modulaire.md)). Un seul déployable Java désormais, donc un seul workflow de ce type — plus besoin d'en copier un par module métier ajouté.
- **`frontend.yml`** — workflow direct (pas de gabarit réutilisable, seul projet Node du repo pour l'instant) : lint, build, image Docker, scan Trivy, push `ghcr.io` sur push vers `main`.

## À venir

CD (déploiement automatisé) introduit à la Phase 7, après un déploiement manuel maîtrisé.
