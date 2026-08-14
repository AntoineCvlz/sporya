# ADR-019 — CD par SSH restreint depuis GitHub Actions

## Statut

Accepté

## Contexte

Le déploiement Docker Compose + Traefik (ADR-018) a été validé manuellement de bout en bout sur le VPS réinitialisé le 14/08/2026 : certificat Let's Encrypt de prod émis, frontend et `POST /api/v1/auth/register` répondent en HTTPS de confiance. Le roadmap prévoyait explicitement d'automatiser ce déploiement (Phase 7) seulement une fois le flux manuel maîtrisé — c'est désormais le cas.

Un piège concret est apparu pendant le déploiement manuel : `api.yml` et `frontend.yml` ne se déclenchent que si **leur propre** dossier a changé (`backend/api/**` / `frontend/**` respectivement). Un commit qui ne touche que l'un des deux ne republie donc pas d'image pour l'autre à ce SHA. Une CD naïve avec un seul tag partagé entre les deux services casserait le déploiement dès qu'un déploiement ne concernerait qu'un seul des deux (`docker compose pull` échouerait, image introuvable pour ce SHA côté service inchangé).

## Options envisagées

- **Un tag d'image unique partagé** (`IMAGE_TAG`) pour `api` et `frontend` — plus simple à première vue, mais reproduit exactement le piège rencontré manuellement : casse dès qu'un déploiement ne concerne qu'un des deux services.
- **Un tag indépendant par service** (`API_IMAGE_TAG` / `FRONTEND_IMAGE_TAG`), chaque workflow (`api.yml`/`frontend.yml`) redéployant uniquement le service dont il vient de publier l'image — aligné sur la réalité des déclencheurs par chemin, aucun risque de tag manquant.
- **Clé SSH root sans restriction** pour la CI — plus simple à mettre en place, mais un secret GitHub compromis donnerait un accès root complet au VPS.
- **Clé SSH dédiée à la CI, restreinte par `command=`** dans `authorized_keys`, vers un utilisateur `deploy` non-root (groupe `docker`) — même en cas de fuite du secret, la clé ne peut exécuter qu'un script de déploiement fixe et validé, jamais un shell libre.

## Décision

Un tag d'image indépendant par service (`API_IMAGE_TAG`, `FRONTEND_IMAGE_TAG` dans le `.env` du VPS). `api.yml` et `frontend.yml` reçoivent chacun un job `deploy` (après publication de l'image sur `ghcr.io`, uniquement sur push vers `main`) qui se connecte en SSH à un utilisateur `deploy` dédié, non-root, dont la clé CI est restreinte par `command="/opt/sporya/infrastructure/docker/deploy.sh"` dans `authorized_keys`. Ce script (`infrastructure/docker/deploy.sh`, versionné dans le repo) lit `$SSH_ORIGINAL_COMMAND` (la seule chose qu'une commande forcée laisse passer), valide strictement le format (`<api|frontend> <sha 40 hex>`), met à jour la bonne variable dans `.env`, puis ne `pull`/`up -d` **que le service concerné** — jamais l'autre.

## Conséquences

- Chaque service se redéploie indépendamment, sans jamais risquer un `pull` sur un tag d'image inexistant — le problème rencontré manuellement le 14/08/2026 ne peut plus se reproduire.
- Surface d'attaque du secret GitHub `VPS_DEPLOY_SSH_KEY` réduite au strict nécessaire : même volé, il ne permet que de relancer le déploiement Compose avec un SHA valide, pas un accès shell.
- Pas de rollback automatique en V1 : un déploiement problématique se corrige en relançant `deploy.sh` avec un SHA antérieur (procédure manuelle documentée dans `docs/deployment/README.md`). Pas de stratégie blue/green — un seul réplica par service sur un seul VPS, `restart: unless-stopped` suffit au stade actuel ; à revisiter si un besoin de zéro-downtime réel apparaît.
- Le script de déploiement vit dans le repo (versionné, revu comme le reste du code) plutôt que directement sur le VPS — cohérent avec le principe "tout ce qui définit l'infra est dans Git" déjà appliqué aux manifestes K8s archivés et aux fichiers Compose.
