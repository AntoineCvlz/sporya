# Pipelines CI/CD

## En place aujourd'hui

- **`repo-checks.yml`** — tourne sur chaque push/PR (aucune dépendance à un service) : scan de secrets (gitleaks), lint des workflows (actionlint), validation de `docker-compose.yml`.
- **`service-ci.yml`** — reusable workflow (`workflow_call`) pour un service Java/Maven : lint, tests, dependency check, build, image Docker, scan Trivy, push `ghcr.io` sur push vers `main` (voir [ADR-016](../../docs/adr/ADR-016-ghcr-registry.md)). Gabarit établi avant le premier service (voir [ADR-004](../../docs/adr/ADR-004-service-de-reference.md)).
- **`auth-service.yml`** — invoque `service-ci.yml` pour `services/auth-service`. Chaque service Java suivant copie ce fichier de quelques lignes en changeant le chemin.
- **`frontend.yml`** — workflow direct (pas de gabarit réutilisable, seul projet Node du repo pour l'instant) : lint, build, image Docker, scan Trivy, push `ghcr.io` sur push vers `main`.

## À venir

CD (déploiement automatisé) introduit à la Phase 7, après un déploiement manuel maîtrisé.
