# Pipelines CI/CD

## En place aujourd'hui

- **`repo-checks.yml`** — tourne sur chaque push/PR dès maintenant (aucune dépendance à un service) : scan de secrets (gitleaks), lint des workflows (actionlint), validation de `docker-compose.yml`.
- **`service-ci.yml`** — reusable workflow (`workflow_call`) pour un service Java/Maven : lint, tests, dependency check, build, image Docker, scan Trivy. N'est encore invoqué par personne — c'est le gabarit établi avant le premier service (voir [ADR-004](../../docs/adr/ADR-004-service-de-reference.md)).

## À faire au moment de la construction d'Auth Service (Phase 6)

Créer `.github/workflows/auth-service.yml` :

```yaml
name: Auth Service CI
on:
  push:
    paths: ["services/auth-service/**"]
  pull_request:
    paths: ["services/auth-service/**"]
jobs:
  ci:
    uses: ./.github/workflows/service-ci.yml
    with:
      service_path: services/auth-service
```

Chaque service suivant copie ce fichier de quelques lignes en changeant le chemin — pas de pipeline dupliquée.

## À venir

CD (déploiement automatisé) introduit à la Phase 7, après un déploiement manuel maîtrisé.
