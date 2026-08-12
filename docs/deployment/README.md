# Déploiement

## Local (Phase 2)

`docker compose up` — voir [`README.md`](../../README.md#démarrer-en-local) et [`infrastructure/docker/`](../../infrastructure/docker/).

## VPS / K3s (Phase 5)

Le VPS (`87.106.171.146`, Ubuntu 24.04) n'héberge que Sporya — l'ancienne application `collector-shop` qui y tournait a été décommissionnée (voir [ADR-015](../adr/ADR-015-traefik-entree-directe.md), qui remplace [ADR-014](../adr/ADR-014-cohabitation-vps-existant.md)).

**Principe** : Traefik (ingress K3s) est le point d'entrée HTTPS direct du VPS, en `LoadBalancer` natif sur les ports 80/443. `cert-manager` gère l'émission et le renouvellement automatique des certificats Let's Encrypt (challenge `HTTP-01` via l'ingress class `traefik`), pour tout `Ingress` annoté avec `cert-manager.io/cluster-issuer`.

DNS : `sporya.antoine-cuvilliez.fr` → `A` → `87.106.171.146` (IONOS).

Manifestes appliqués : [`infrastructure/kubernetes/namespace/namespace.yaml`](../../infrastructure/kubernetes/namespace/namespace.yaml), [`infrastructure/kubernetes/config/cert-manager.yaml`](../../infrastructure/kubernetes/config/cert-manager.yaml), [`infrastructure/kubernetes/config/letsencrypt-issuers.yaml`](../../infrastructure/kubernetes/config/letsencrypt-issuers.yaml).

## Déploiement — Auth Service + Frontend (Phase 6)

Déploiement **manuel** (le CD automatisé arrive Phase 7). Registre : `ghcr.io` ([ADR-016](../adr/ADR-016-ghcr-registry.md)), images publiées par `auth-service.yml` / `frontend.yml` sur push vers `main`. Rendre chaque package public une fois poussé (Settings du package sur GitHub) — sinon K3s ne peut pas le tirer sans `imagePullSecret`.

1. **Secret Postgres** (une seule fois) :
   ```bash
   kubectl create secret generic postgres-credentials --namespace sporya \
     --from-literal=POSTGRES_USER=sporya \
     --from-literal=POSTGRES_PASSWORD='<mot-de-passe-généré>' \
     --from-literal=POSTGRES_DB=sporya
   ```
2. **Postgres** : `kubectl apply -f infrastructure/kubernetes/postgres/`
3. **Secret clés JWT** (une seule fois, jamais la paire de dev de `.env.example`) — voir [`services/auth-service/README.md`](../../services/auth-service/README.md#clés-jwt-rs256) pour générer la paire :
   ```bash
   kubectl create secret generic auth-service-jwt-keys --namespace sporya \
     --from-literal=JWT_PRIVATE_KEY_BASE64="$(base64 -w0 private.pem)" \
     --from-literal=JWT_PUBLIC_KEY_BASE64="$(base64 -w0 public.pem)"
   ```
4. **Auth Service** : éditer le tag `<SHA>` dans [`infrastructure/kubernetes/auth/deployment.yaml`](../../infrastructure/kubernetes/auth/deployment.yaml), puis `kubectl apply -f infrastructure/kubernetes/auth/`
5. **Frontend** : éditer le tag `<SHA>` dans [`infrastructure/kubernetes/frontend/deployment.yaml`](../../infrastructure/kubernetes/frontend/deployment.yaml), puis `kubectl apply -f infrastructure/kubernetes/frontend/`
6. **Ingress + TLS** : `kubectl apply -f infrastructure/kubernetes/ingress/` — `letsencrypt-staging` d'abord pour un nouveau host (voir [ADR-015](../adr/ADR-015-traefik-entree-directe.md)), `letsencrypt-prod` une fois validé (déjà le cas ici, `sporya.antoine-cuvilliez.fr` a un certificat prod valide).
7. **Vérifier** :
   ```bash
   curl -X POST https://sporya.antoine-cuvilliez.fr/api/v1/auth/register \
     -H 'Content-Type: application/json' \
     -d '{"email":"test@sporya.local","password":"correct-horse-battery"}'
   curl -I https://sporya.antoine-cuvilliez.fr/   # frontend
   ```

Premier déploiement (squelette minimal) validé le 12/08/2026 : `200` avec certificat Let's Encrypt de production.

## Dimensionnement mémoire

Le VPS a **1.8 Gi de RAM au total** (pas d'upgrade prévu pour l'instant) — déjà partagés entre K3s (Traefik, cert-manager, coredns...), Postgres et les services applicatifs. Le 12/08/2026, un rolling deploy a fait tourner brièvement deux JVM Auth Service en même temps sous un système déjà en tension (aucun bug applicatif — démarrage normal, juste ~37s au lieu de quelques secondes), ce qui a fait basculer le VPS en situation de swap thrashing (load average 40+, SSH quasi inutilisable). Un swapfile de 2 Gi a été ajouté comme filet de sécurité (`/swapfile`, persistant via `/etc/fstab`), mais la vraie marge de manœuvre vient de contraindre chaque JVM explicitement plutôt que de laisser les heuristiques par défaut décider :

- **`JAVA_TOOL_OPTIONS`** fixé dans le `Dockerfile` de chaque service Java : `-Xms192m -Xmx192m -XX:MaxMetaspaceSize=96m -XX:MaxDirectMemorySize=32m -XX:ReservedCodeCacheSize=48m -XX:+UseSerialGC` (SerialGC : moins d'overhead mémoire que G1 pour un petit tas mono-instance à faible trafic).
- **`server.tomcat.threads.max: 20`** dans `application.yml` (défaut Tomcat = 200, chaque thread réserve de la pile même inactif).
- **Ressources K8s réduites en conséquence** (`auth/deployment.yaml`) : `requests: 300Mi` / `limits: 400Mi` (avant : 384/512Mi), avec marge au-dessus du plafond JVM (~368Mi) pour l'overhead natif du process.
- **Probes plus tolérantes** (`initialDelaySeconds`/`failureThreshold` généreux) pour qu'un démarrage lent sous pression CPU ne déclenche pas un cycle kill-restart qui aggrave la situation, comme observé le 12/08/2026.

Chaque nouveau service Java copie ce gabarit (voir [ADR-004](../adr/ADR-004-service-de-reference.md)). Avant d'ajouter Club/Match Service, revérifier `free -h` sur le VPS.

## CD (Phase 7)

Pas encore en place — pipeline de déploiement automatisé, stratégie de rolling update et de rollback, à documenter à ce moment-là.

## Sauvegardes (Phase 16)

Pas encore en place.
