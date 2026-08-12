# Déploiement

## Local (Phase 2)

`docker compose up` — voir [`README.md`](../../README.md#démarrer-en-local) et [`infrastructure/docker/`](../../infrastructure/docker/).

## VPS / K3s (Phase 5)

Le VPS (`87.106.171.146`, Ubuntu 24.04) n'héberge que Sporya — l'ancienne application `collector-shop` qui y tournait a été décommissionnée (voir [ADR-015](../adr/ADR-015-traefik-entree-directe.md), qui remplace [ADR-014](../adr/ADR-014-cohabitation-vps-existant.md)).

**Principe** : Traefik (ingress K3s) est le point d'entrée HTTPS direct du VPS, en `LoadBalancer` natif sur les ports 80/443. `cert-manager` gère l'émission et le renouvellement automatique des certificats Let's Encrypt (challenge `HTTP-01` via l'ingress class `traefik`), pour tout `Ingress` annoté avec `cert-manager.io/cluster-issuer`.

DNS : `sporya.antoine-cuvilliez.fr` → `A` → `87.106.171.146` (IONOS).

Manifestes appliqués : [`infrastructure/kubernetes/namespace/namespace.yaml`](../../infrastructure/kubernetes/namespace/namespace.yaml), [`infrastructure/kubernetes/config/cert-manager.yaml`](../../infrastructure/kubernetes/config/cert-manager.yaml), [`infrastructure/kubernetes/config/letsencrypt-issuers.yaml`](../../infrastructure/kubernetes/config/letsencrypt-issuers.yaml).

## CD (Phase 7)

Pas encore en place — pipeline de déploiement automatisé, stratégie de rolling update et de rollback, à documenter à ce moment-là.

## Sauvegardes (Phase 16)

Pas encore en place.
