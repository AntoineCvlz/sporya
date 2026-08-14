# Pipelines CI/CD

## En place aujourd'hui

- **`repo-checks.yml`** — tourne sur chaque push/PR (aucune dépendance à un service) : scan de secrets (gitleaks), lint des workflows (actionlint), validation de `docker-compose.yml`.
- **`service-ci.yml`** — reusable workflow (`workflow_call`) pour un déployable Java/Maven : lint, tests, dependency check, build, image Docker, scan Trivy, push `ghcr.io` sur push vers `main` (voir [ADR-016](../../docs/adr/ADR-016-ghcr-registry.md)).
- **`api.yml`** — invoque `service-ci.yml` pour `backend/api`, le monolithe modulaire (voir [ADR-017](../../docs/adr/ADR-017-monolithe-modulaire.md)). Un seul déployable Java désormais, donc un seul workflow de ce type — plus besoin d'en copier un par module métier ajouté. Sur push vers `main`, un job `deploy` supplémentaire redéploie `api` sur le VPS par SSH une fois l'image publiée (voir [ADR-019](../../docs/adr/ADR-019-cd-ssh-github-actions.md)).
- **`frontend.yml`** — workflow direct (pas de gabarit réutilisable, seul projet Node du repo pour l'instant) : lint, build, image Docker, scan Trivy, push `ghcr.io` sur push vers `main`, puis job `deploy` (même mécanisme SSH que `api.yml`, ADR-019) qui redéploie uniquement `frontend`.

## CD (Phase 7)

En place depuis [ADR-019](../../docs/adr/ADR-019-cd-ssh-github-actions.md) : `api.yml`/`frontend.yml` déploient chacun leur propre service sur le VPS par SSH restreint (`command=` forcée côté serveur) après publication de l'image — jamais un déploiement combiné, les deux workflows se déclenchant indépendamment selon le chemin modifié. Détail du flux et rollback manuel : [`docs/deployment/README.md`](../../docs/deployment/README.md#cd-phase-7).
