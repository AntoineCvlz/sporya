# Docker

Deux fichiers Compose, deux usages :

| Fichier | Usage | Images | Ports |
|---|---|---|---|
| [`docker-compose.yml`](../../docker-compose.yml) (racine) | Développement local | `build:` local (`backend/api`, `frontend`) | Tous publiés sur `localhost` |
| [`docker-compose.prod.yml`](docker-compose.prod.yml) | VPS (ADR-018) | `image:` GHCR (`ghcr.io/antoinecvlz/...`) | Seul Traefik (80/443) publié ; le reste sur `127.0.0.1` uniquement |

## Local

```bash
cp .env.example .env
docker compose up -d
docker compose ps        # vérifier que postgres est "healthy"
docker compose down -v   # arrêt + suppression des données
```

Démarre `postgres`, `adminer`, `postgres-exporter`, `api`, `frontend`, `prometheus`, `grafana` — voir la table du [`README.md`](../../README.md#démarrer-en-local) racine.

## Production (VPS)

Voir [`docs/deployment/README.md`](../../docs/deployment/README.md) pour la procédure complète (provisioning, secrets, premier déploiement, bascule staging→prod du certificat). En résumé : `docker-compose.prod.yml` ajoute Traefik (TLS via son résolveur ACME intégré, routage par labels vers `api`/`frontend`) et retire tout port public hors 80/443 — Postgres, Prometheus, Grafana ne sont accessibles que via tunnel SSH (`127.0.0.1`).

Le `.env` réel du VPS n'est **jamais commité** — gabarit dans [`.env.prod.example`](.env.prod.example).
