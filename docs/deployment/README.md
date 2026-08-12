# Déploiement

## Local (Phase 2)

`docker compose up` — voir [`README.md`](../../README.md#démarrer-en-local) et [`infrastructure/docker/`](../../infrastructure/docker/).

## VPS / K3s (Phase 5)

Le VPS (`87.106.171.146`, Ubuntu 24.04) n'héberge que Sporya — l'ancienne application `collector-shop` qui y tournait a été décommissionnée (voir [ADR-015](../adr/ADR-015-traefik-entree-directe.md), qui remplace [ADR-014](../adr/ADR-014-cohabitation-vps-existant.md)).

**Principe** : Traefik (ingress K3s) est le point d'entrée HTTPS direct du VPS, en `LoadBalancer` natif sur les ports 80/443. `cert-manager` gère l'émission et le renouvellement automatique des certificats Let's Encrypt (challenge `HTTP-01` via l'ingress class `traefik`), pour tout `Ingress` annoté avec `cert-manager.io/cluster-issuer`.

DNS : `sporya.antoine-cuvilliez.fr` → `A` → `87.106.171.146` (IONOS).

Manifestes appliqués : [`infrastructure/kubernetes/namespace/namespace.yaml`](../../infrastructure/kubernetes/namespace/namespace.yaml), [`infrastructure/kubernetes/config/cert-manager.yaml`](../../infrastructure/kubernetes/config/cert-manager.yaml), [`infrastructure/kubernetes/config/letsencrypt-issuers.yaml`](../../infrastructure/kubernetes/config/letsencrypt-issuers.yaml).

## Premier déploiement — Auth Service (Phase 6)

Déploiement **manuel** (le CD automatisé arrive Phase 7). Registre : `ghcr.io` ([ADR-016](../adr/ADR-016-ghcr-registry.md)), image publiée par `auth-service.yml` sur push vers `main`.

1. **Secret Postgres** (une seule fois, ne jamais committer les vraies valeurs) :
   ```bash
   kubectl create secret generic postgres-credentials --namespace sporya \
     --from-literal=POSTGRES_USER=sporya \
     --from-literal=POSTGRES_PASSWORD='<mot-de-passe-généré>' \
     --from-literal=POSTGRES_DB=sporya
   ```
2. **Postgres** : `kubectl apply -f infrastructure/kubernetes/postgres/`
3. **Rendre le package GHCR public** (Settings du package sur GitHub) une fois la première image poussée — sinon K3s ne peut pas la tirer sans `imagePullSecret`.
4. **Auth Service** : éditer le tag `<SHA>` dans [`infrastructure/kubernetes/auth/deployment.yaml`](../../infrastructure/kubernetes/auth/deployment.yaml) avec le SHA du commit publié, puis `kubectl apply -f infrastructure/kubernetes/auth/`
5. **Ingress + TLS** : `kubectl apply -f infrastructure/kubernetes/ingress/auth-service.yaml` — `letsencrypt-staging` d'abord (voir [ADR-015](../adr/ADR-015-traefik-entree-directe.md)), vérifier `kubectl describe certificate -n sporya sporya-tls-staging`, puis basculer sur `letsencrypt-prod` une fois validé.
6. **Vérifier** : `curl -I https://sporya.antoine-cuvilliez.fr/api/auth/actuator/health`

## CD (Phase 7)

Pas encore en place — pipeline de déploiement automatisé, stratégie de rolling update et de rollback, à documenter à ce moment-là.

## Sauvegardes (Phase 16)

Pas encore en place.
