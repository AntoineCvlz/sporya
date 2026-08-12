# Kubernetes / K3s

```text
kubernetes/
├── namespace/
├── ingress/          # routage par chemin : /api/v1/auth, /api/v1/clubs, /api/v1/matches...
├── config/
├── secrets/
├── auth/             # ajouté en premier (Phase 6)
├── club/
├── match/
├── frontend/
├── postgres/
└── redis/            # ajouté quand Redis est réellement utilisé
```

Un dossier de manifestes par service, ajouté au moment où ce service est effectivement construit et déployé (voir [ADR-008](../../docs/adr/ADR-008-k3s.md)) — pas de dossiers vides pour des services non encore développés.

## Entrée HTTPS et TLS

Traefik (ingress K3s par défaut) est le point d'entrée direct du VPS sur les ports 80/443 (`LoadBalancer` natif, Klipper) — voir [ADR-015](../../docs/adr/ADR-015-traefik-entree-directe.md). `cert-manager` ([`config/cert-manager.yaml`](config/cert-manager.yaml)) émet et renouvelle les certificats Let's Encrypt via deux `ClusterIssuer` ([`config/letsencrypt-issuers.yaml`](config/letsencrypt-issuers.yaml)) : `letsencrypt-staging` pour développer un `Ingress` sans risquer le rate limit, `letsencrypt-prod` une fois validé.

## PostgreSQL

Instance unique mutualisée (`postgres/statefulset.yaml` + PVC via `local-path`, le storage class par défaut de K3s), un schéma par service (voir [ADR-012](../../docs/adr/ADR-012-schema-par-service.md)). Identifiants dans le Secret `postgres-credentials`, créé à la main sur le VPS à partir du gabarit [`secrets/postgres-credentials.example.yaml`](secrets/postgres-credentials.example.yaml) — jamais commité avec de vraies valeurs.

## Auth Service

Premier service déployé (`auth/`), image publiée sur `ghcr.io` (voir [ADR-016](../../docs/adr/ADR-016-ghcr-registry.md)), exposé via [`ingress/auth-service.yaml`](ingress/auth-service.yaml) sur `sporya.antoine-cuvilliez.fr/api/v1/auth`. Chaque service route directement sur son propre préfixe d'API versionné — pas de réécriture de chemin nécessaire, le contrôleur du service attend exactement ce que Traefik transmet. `/actuator/*` n'est pas exposé via l'Ingress (accès interne uniquement : probes K8s, scrape Prometheus).

## Frontend

`frontend/` (React statique servi par nginx) exposé via [`ingress/frontend.yaml`](ingress/frontend.yaml) en catch-all `/` sur le même host — Traefik priorise automatiquement les chemins plus spécifiques (`/api/v1/auth`) sur celui-ci, peu importe l'ordre de déclaration entre `Ingress`.

## Statut

Phase 6 (premier déploiement) et logique métier Auth Service (inscription/connexion JWT) + frontend minimal terminés — voir [`services/auth-service/README.md`](../../services/auth-service/README.md) et [`frontend/README.md`](../../frontend/README.md). Prochaine étape (Phase 7) : CD automatisé.
