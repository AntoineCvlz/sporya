# Pipelines CI/CD

Aucun workflow encore en place (Phase 1 — Repository). Mise en place prévue à la Phase 3 :

- `service-ci.yml` — reusable workflow (`workflow_call`), écrit une fois avec Auth Service puis invoqué par chaque service suivant (voir [ADR-004](../../docs/adr/ADR-004-service-de-reference.md)).
- `<nom>-service.yml` — un déclencheur court par service, qui invoque `service-ci.yml` avec le chemin du service concerné.

CD (déploiement automatisé) introduit à la Phase 7, après un déploiement manuel maîtrisé.
