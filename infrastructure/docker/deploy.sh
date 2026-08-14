#!/usr/bin/env bash
# Commande forcée SSH pour l'utilisateur `deploy` (ADR-019) : la clé CI dans
# authorized_keys est préfixée par command="/opt/sporya/infrastructure/docker/deploy.sh",
# donc SSH ignore ce que le client demande et exécute toujours ce script —
# la seule chose qui passe est $SSH_ORIGINAL_COMMAND, lu et validé
# strictement ci-dessous avant toute action. Usage attendu (depuis la CI) :
#   ssh deploy@vps "api <sha-40-hex>"
#   ssh deploy@vps "frontend <sha-40-hex>"
set -euo pipefail
cd "$(dirname "$0")"

read -r service tag <<< "${SSH_ORIGINAL_COMMAND:-}"

case "$service" in
  api) var=API_IMAGE_TAG ;;
  frontend) var=FRONTEND_IMAGE_TAG ;;
  *)
    echo "service inconnu: '$service' (attendu: api|frontend)" >&2
    exit 1
    ;;
esac

if ! [[ "$tag" =~ ^[0-9a-f]{40}$ ]]; then
  echo "tag invalide: '$tag' (attendu: SHA de commit, 40 caractères hexadécimaux)" >&2
  exit 1
fi

sed -i "s/^${var}=.*/${var}=${tag}/" .env
docker compose -f docker-compose.prod.yml pull "$service"
docker compose -f docker-compose.prod.yml up -d "$service"
