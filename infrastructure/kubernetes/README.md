# Kubernetes / K3s

```text
kubernetes/
├── namespace/
├── ingress/          # routage par chemin : /api/auth, /api/clubs, /api/matches...
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

Premier service déployé (`auth/`), image publiée sur `ghcr.io` (voir [ADR-016](../../docs/adr/ADR-016-ghcr-registry.md)), exposé via [`ingress/auth-service.yaml`](ingress/auth-service.yaml) sur `sporya.antoine-cuvilliez.fr/api/auth`.

## Statut

Phase 6 en cours. Namespace, Traefik, cert-manager en place (Phase 5 terminée). Manifestes Postgres et Auth Service écrits, déploiement manuel sur le VPS à valider — voir [`docs/deployment/README.md`](../../docs/deployment/README.md).
