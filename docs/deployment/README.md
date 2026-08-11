# Déploiement

Ce dossier documentera, au fur et à mesure de leur mise en place :

- **Local** (Phase 2) — `docker compose up`, variables d'environnement requises, healthchecks.
- **VPS / K3s** (Phase 5-6) — provisioning, DNS, HTTPS (cert-manager), ingress, namespace, secrets.
- **CD** (Phase 7) — pipeline de déploiement automatisé, stratégie de rolling update et de rollback.
- **Sauvegardes** (Phase 16) — fréquence, rétention, procédure de restauration testée.

Rien n'est encore déployé à ce stade (Phase 1 — Repository). Voir la roadmap dans [`docs/architecture/overview.md`](../architecture/overview.md#roadmap-résumée).
