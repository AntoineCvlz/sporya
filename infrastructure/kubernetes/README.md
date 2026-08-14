# Kubernetes / K3s

> **Non déployé actuellement.** Depuis [ADR-018](../../docs/adr/ADR-018-docker-compose-vps.md) (2026-08-14), le VPS tourne sur Docker Compose + Traefik en conteneur (voir [`infrastructure/docker/`](../docker/)), pas K3s — le socle d'orchestration coûtait plus de RAM qu'il n'apportait pour un monolithe à 2 déployables. Ces manifestes restent dans le repo comme référence/preuve de compétence Kubernetes, transférables si le projet héberge un jour plusieurs déployables indépendants (candidat : AI Service, V4).

```text
kubernetes/
├── namespace/
├── ingress/          # api (/api/v1, tout le monolithe), frontend (/ catch-all)
├── config/
├── secrets/
├── api/              # monolithe modulaire (ADR-017) : auth (Phase 6), puis club, match, ...
├── frontend/
├── postgres/
└── redis/            # ajouté quand Redis est réellement utilisé
```

Un seul jeu de manifestes (`api/`) pour tout le monolithe (voir [ADR-008](../../docs/adr/ADR-008-k3s.md) et [ADR-017](../../docs/adr/ADR-017-monolithe-modulaire.md)) — les nouveaux modules métier (Club, Match, ...) n'ajoutent pas de nouveau dossier ici, seulement du code dans `backend/api/`.

## Entrée HTTPS et TLS

Traefik (ingress K3s par défaut) est le point d'entrée direct du VPS sur les ports 80/443 (`LoadBalancer` natif, Klipper) — voir [ADR-015](../../docs/adr/ADR-015-traefik-entree-directe.md). `cert-manager` ([`config/cert-manager.yaml`](config/cert-manager.yaml)) émet et renouvelle les certificats Let's Encrypt via deux `ClusterIssuer` ([`config/letsencrypt-issuers.yaml`](config/letsencrypt-issuers.yaml)) : `letsencrypt-staging` pour développer un `Ingress` sans risquer le rate limit, `letsencrypt-prod` une fois validé.

## PostgreSQL

Instance unique mutualisée (`postgres/statefulset.yaml` + PVC via `local-path`, le storage class par défaut de K3s), un schéma par module (voir [ADR-012](../../docs/adr/ADR-012-schema-par-service.md)). Identifiants dans le Secret `postgres-credentials`, créé à la main sur le VPS à partir du gabarit [`secrets/postgres-credentials.example.yaml`](secrets/postgres-credentials.example.yaml) — jamais commité avec de vraies valeurs.

## API

Monolithe modulaire (`api/`, [ADR-017](../../docs/adr/ADR-017-monolithe-modulaire.md)), image publiée sur `ghcr.io` (voir [ADR-016](../../docs/adr/ADR-016-ghcr-registry.md)), exposé via [`ingress/api.yaml`](ingress/api.yaml) sur `sporya.antoine-cuvilliez.fr/api/v1`. Un seul Deployment/Service/Ingress pour tous les modules métier — pas de nouveau manifeste à chaque module ajouté (Auth aujourd'hui, Club/Match ensuite). `/actuator/*` n'est pas exposé via l'Ingress (accès interne uniquement : probes K8s, scrape Prometheus).

## Frontend

`frontend/` (React statique servi par nginx) exposé via [`ingress/frontend.yaml`](ingress/frontend.yaml) en catch-all `/` sur le même host — Traefik priorise automatiquement le chemin plus spécifique (`/api/v1`) sur celui-ci, peu importe l'ordre de déclaration entre `Ingress`.

## Statut

Phase 6 (premier déploiement K3s) et logique métier module Auth (inscription/connexion JWT) + frontend minimal validés le 12/08/2026 sur l'ancien VPS — voir [`backend/api/README.md`](../../backend/api/README.md) et [`frontend/README.md`](../../frontend/README.md). Le VPS a été réinitialisé le 2026-08-14 et le déploiement réel est repassé sur Docker Compose ([ADR-018](../../docs/adr/ADR-018-docker-compose-vps.md), voir [`docs/deployment/README.md`](../../docs/deployment/README.md)) — ces manifestes ne sont plus appliqués tels quels, ils resteraient à adapter (renommage `auth-service` → `api` déjà fait ici) si K8s redevenait la cible de déploiement un jour.
