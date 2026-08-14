# Déploiement

## Local (Phase 2)

`docker compose up` — voir [`README.md`](../../README.md#démarrer-en-local) et [`infrastructure/docker/`](../../infrastructure/docker/).

## VPS (Phase 5/6, révisé — ADR-018)

Le VPS (`87.106.171.146`) a été réinitialisé le 2026-08-14 (image Ubuntu 26 fraîche) — l'occasion d'abandonner K3s au profit de **Docker Compose + Traefik en conteneur** ([ADR-018](../adr/ADR-018-docker-compose-vps.md)), qui remplace [ADR-008](../adr/ADR-008-k3s.md) (K3s) et [ADR-015](../adr/ADR-015-traefik-entree-directe.md) (Traefik comme ingress K3s). `infrastructure/kubernetes/` reste dans le repo pour référence mais n'est plus le mécanisme de déploiement réel — voir [`infrastructure/kubernetes/README.md`](../../infrastructure/kubernetes/README.md).

**Principe** : Traefik tourne en conteneur, seul service à publier des ports sur l'hôte (80/443). Il fait la terminaison TLS via son résolveur ACME intégré (Let's Encrypt, challenge HTTP-01) et route vers `api`/`frontend` par labels Docker — même logique que l'Ingress K8s d'avant, sans le socle K3s. Tout le reste (Postgres, Prometheus, Grafana, l'exporter) n'écoute que sur `127.0.0.1` : accès admin uniquement via tunnel SSH.

DNS (inchangé) : `sporya.antoine-cuvilliez.fr` → `A` → `87.106.171.146` (IONOS).

### Provisionner le VPS neuf (une seule fois)

```bash
# Docker Engine + plugin Compose (dépôt officiel, pas le paquet Ubuntu générique)
curl -fsSL https://get.docker.com | sh

# Swapfile 2Gi (mesure de stabilité indépendante de K3s, toujours pertinente
# vu la RAM — voir #dimensionnement-mémoire ci-dessous)
fallocate -l 2G /swapfile
chmod 600 /swapfile
mkswap /swapfile
swapon /swapfile
echo '/swapfile none swap sw 0 0' >> /etc/fstab

mkdir -p /opt/sporya
```

### Déploiement — API + Frontend

Déploiement **manuel** (le CD automatisé, Phase 7, est un chantier séparé). Registre : `ghcr.io` ([ADR-016](../adr/ADR-016-ghcr-registry.md)), images publiées par `api.yml` / `frontend.yml` sur push vers `main`. Rendre chaque package public une fois poussé (Settings du package sur GitHub) — sinon `docker compose pull` échoue sans authentification.

1. Copier sur le VPS (`/opt/sporya/`) : [`infrastructure/docker/docker-compose.prod.yml`](../../infrastructure/docker/docker-compose.prod.yml) et [`infrastructure/monitoring/`](../../infrastructure/monitoring/) (chemins relatifs utilisés par le compose pour Prometheus/Grafana). Pas besoin de cloner tout le repo.
2. Créer le vrai `.env` à côté du compose, à partir de [`infrastructure/docker/.env.prod.example`](../../infrastructure/docker/.env.prod.example) — **jamais** commité. Génération de la paire JWT de prod (jamais celle de dev) documentée dans [`backend/api/README.md`](../../backend/api/README.md#clés-jwt-rs256).
3. Premier déploiement, résolveur ACME **staging** (déjà la valeur par défaut dans `docker-compose.prod.yml`, ligne `caserver`) pour éviter le rate limit Let's Encrypt pendant la mise au point :
   ```bash
   docker compose -f docker-compose.prod.yml pull
   docker compose -f docker-compose.prod.yml up -d
   docker compose -f docker-compose.prod.yml logs -f traefik   # confirmer l'émission du certificat (invalide en staging, normal)
   ```
4. Une fois le flux validé sans erreur, retirer la ligne `--certificatesresolvers.letsencrypt.acme.caserver=...` (staging) du service `traefik`, puis :
   ```bash
   docker compose -f docker-compose.prod.yml up -d --force-recreate traefik
   ```
5. **Vérifier** :
   ```bash
   curl -I https://sporya.antoine-cuvilliez.fr/                        # frontend, certificat prod valide
   curl -X POST https://sporya.antoine-cuvilliez.fr/api/v1/auth/register \
     -H 'Content-Type: application/json' \
     -d '{"email":"test@sporya.local","password":"correct-horse-battery"}'
   ```

Redéployer une nouvelle version = changer `API_IMAGE_TAG` et/ou `FRONTEND_IMAGE_TAG` dans le `.env` du VPS (les deux services ont chacun leur tag, voir #cd-phase-7) puis `pull`/`up -d` sur le(s) service(s) concerné(s) uniquement, sans toucher au reste. En pratique, ce n'est plus fait à la main depuis la mise en place de la CD.

Le VPS étant reparti de zéro, il n'y a pas de données à migrer depuis l'ancien déploiement K3s (Postgres ne contenait que des données de test Phase 6).

## Dimensionnement mémoire

Le VPS a **1.8 Gi de RAM au total** (pas d'upgrade prévu pour l'instant). Le 12/08/2026 (ancien déploiement K3s), un rolling deploy a fait tourner brièvement deux JVM Auth Service en même temps sous un système déjà en tension (aucun bug applicatif — démarrage normal, juste ~37s au lieu de quelques secondes), ce qui a fait basculer le VPS en situation de swap thrashing (load average 40+, SSH quasi inutilisable). C'est cet épisode qui a motivé à la fois le passage au monolithe modulaire ([ADR-017](../adr/ADR-017-monolithe-modulaire.md)) et, une fois le VPS réinitialisé, l'abandon du socle K3s ([ADR-018](../adr/ADR-018-docker-compose-vps.md)) : une seule JVM à faire tourner, sans plus payer l'overhead d'orchestration en plus. Le swapfile de 2 Gi (voir provisioning ci-dessus) reste un filet de sécurité, mais la vraie marge de manœuvre vient de contraindre la JVM explicitement plutôt que de laisser les heuristiques par défaut décider :

- **`JAVA_TOOL_OPTIONS`** fixé dans le `Dockerfile` de `backend/api` : `-Xms192m -Xmx192m -XX:MaxMetaspaceSize=96m -XX:MaxDirectMemorySize=32m -XX:ReservedCodeCacheSize=48m -XX:+UseSerialGC` (SerialGC : moins d'overhead mémoire que G1 pour un petit tas mono-instance à faible trafic).
- **`server.tomcat.threads.max: 20`** dans `application.yml` (défaut Tomcat = 200, chaque thread réserve de la pile même inactif).
- **`mem_limit: 400m`** sur le service `api` de `docker-compose.prod.yml`, avec marge au-dessus du plafond JVM (~368Mi) pour l'overhead natif du process.
- **`healthcheck` généreux** (`start_period: 60s`, `retries: 6`) pour qu'un démarrage lent sous pression CPU ne déclenche pas un cycle kill-restart qui aggrave la situation, comme observé le 12/08/2026.

Ce tuning reste le même quel que soit le nombre de modules ajoutés à `backend/api` (Club, Match, ...) — un seul déployable à surveiller. Avant d'ajouter un module conséquent, revérifier `free -h` sur le VPS.

## CD (Phase 7)

En place depuis [ADR-019](../adr/ADR-019-cd-ssh-github-actions.md) : `api.yml` et `frontend.yml` déploient chacun leur propre service sur le VPS par SSH, juste après avoir publié son image sur `ghcr.io` (push vers `main` uniquement) — jamais un déploiement combiné, puisque les deux workflows se déclenchent indépendamment selon le chemin modifié (`backend/api/**` / `frontend/**`).

**Mécanisme** : le job `deploy` de chaque workflow écrit une clé privée et un `known_hosts` à partir de secrets, puis appelle le client `ssh` natif du runner (`StrictHostKeyChecking=yes`) — pas d'action tierce pour ce pas, voir ADR-019 pour le pourquoi (l'entrée `fingerprint` d'`appleboy/ssh-action` échouait de façon non diagnosticable malgré une empreinte confirmée correcte). La connexion aboutit sur un utilisateur dédié `deploy` (non-root, groupe `docker`) dont la clé CI est restreinte par une commande forcée dans `authorized_keys` — elle ne peut exécuter que [`infrastructure/docker/deploy.sh`](../../infrastructure/docker/deploy.sh), jamais un shell libre. Ce script lit le service et le SHA demandés (`$SSH_ORIGINAL_COMMAND`), valide strictement le format, met à jour `API_IMAGE_TAG` ou `FRONTEND_IMAGE_TAG` dans le `.env` du VPS, puis `pull`/`up -d` uniquement ce service.

**Provisioning requis sur le VPS** (une seule fois) :
```bash
adduser --system --group --home /home/deploy --shell /bin/bash --disabled-password deploy
usermod -aG docker deploy
chown -R deploy:deploy /opt/sporya   # deploy.sh doit pouvoir écrire .env
chmod +x /opt/sporya/infrastructure/docker/deploy.sh   # après l'avoir copié comme le reste de infrastructure/docker/
```

Depuis ton poste, génère une paire de clés dédiée à la CI (jamais ta clé perso), installe la clé publique sur le VPS préfixée par la commande forcée, puis lis directement la clé d'hôte du VPS (plus fiable que `ssh-keyscan` — la négociation d'un VPS très récent peut y faire échouer certains clients OpenSSH) :
```bash
ssh-keygen -t ed25519 -f sporya_deploy_key -C "github-actions-deploy"   # passphrase vide

# Publique -> authorized_keys de `deploy`, avec la commande forcée
echo "command=\"/opt/sporya/infrastructure/docker/deploy.sh\",no-port-forwarding,no-X11-forwarding,no-agent-forwarding,no-pty $(cat sporya_deploy_key.pub)" \
  | ssh root@87.106.171.146 "mkdir -p /home/deploy/.ssh && cat >> /home/deploy/.ssh/authorized_keys && chown -R deploy:deploy /home/deploy/.ssh && chmod 700 /home/deploy/.ssh && chmod 600 /home/deploy/.ssh/authorized_keys"

# Clé d'hôte du VPS, au format known_hosts (pour le secret VPS_HOST_KEY)
echo "87.106.171.146 $(ssh root@87.106.171.146 'cat /etc/ssh/ssh_host_ed25519_key.pub')"
```

**Secrets GitHub** (Settings → Secrets and variables → Actions) :
- `VPS_HOST` = `87.106.171.146`
- `VPS_DEPLOY_SSH_KEY` = contenu de `sporya_deploy_key` (clé **privée**, générée ci-dessus — jamais commitée, voir `.gitignore`)
- `VPS_HOST_KEY` = sortie de la dernière commande ci-dessus (ligne complète `<host> ssh-ed25519 <clé>`, pas juste l'empreinte)

**Rollback manuel** (pas d'automatisation en V1, voir ADR-019) : identifier le SHA du dernier déploiement sain (Actions → run précédent), puis sur le VPS :
```bash
cd /opt/sporya/infrastructure/docker
sed -i "s/^API_IMAGE_TAG=.*/API_IMAGE_TAG=<ancien-sha>/" .env   # ou FRONTEND_IMAGE_TAG
docker compose -f docker-compose.prod.yml pull api              # ou frontend
docker compose -f docker-compose.prod.yml up -d api
```

## Sauvegardes (Phase 16)

Pas encore en place.
