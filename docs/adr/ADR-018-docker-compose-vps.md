# ADR-018 — Docker Compose + Traefik direct sur le VPS, abandon de K3s

## Statut

Accepté

## Contexte

Le VPS a été réinitialisé (image Ubuntu 26 fraîche, 2026-08-14) : K3s, Traefik-ingress, cert-manager et tous les secrets/déploiements précédents ont disparu avec l'ancienne image. C'est l'occasion de reconsidérer [ADR-008](ADR-008-k3s.md) maintenant que l'architecture est passée d'une cible de plusieurs microservices à un monolithe modulaire ([ADR-017](ADR-017-monolithe-modulaire.md)) : le socle K3s (control plane, Traefik-ingress, cert-manager x3 pods, coredns, metrics-server) consommait une part significative des ~1.8Gi RAM du VPS avant même qu'un seul conteneur applicatif ne tourne — pour un monolithe qui ne compte plus que 2 déployables (`api`, `frontend`) + Postgres, ce socle d'orchestration coûte plus qu'il n'apporte.

## Options envisagées

- **Reprovisionner K3s à l'identique sur le VPS neuf** — cohérent avec l'existant (manifestes déjà prêts, renommés `api/` lors du pivot monolithe), mais reproduit le même coût RAM fixe pour un nombre de workloads qui a été divisé par 3-4.
- **Docker Compose direct sur le VPS, Traefik en conteneur pour le TLS** — même outil que le développement local (`docker-compose.yml`), pas de socle d'orchestration à payer, Traefik garde son rôle de reverse-proxy HTTPS mais avec son résolveur ACME natif au lieu de cert-manager.

## Décision

Docker Compose directement sur le VPS. Traefik tourne en conteneur (`infrastructure/docker/docker-compose.prod.yml`), fait la terminaison TLS via son résolveur ACME intégré (Let's Encrypt, challenge HTTP-01 — même mécanisme que cert-manager avant, sans CRD ni contrôleur à faire tourner), et route par label Docker (`Host`/`PathPrefix`) vers `api` et `frontend`, exactement comme le faisait l'Ingress K8s. `api`, `postgres` et les outils d'observabilité ne publient plus de port sur `0.0.0.0` : seuls Traefik (80/443) est exposé publiquement, le reste est accessible en local (`127.0.0.1`, tunnel SSH) ou via le réseau Docker interne.

`infrastructure/kubernetes/` est **conservé, pas supprimé** — marqué non déployé dans son README. C'est du travail réel et transférable si le projet héberge un jour plusieurs déployables indépendants avec des besoins de scaling/déploiement différenciés (candidat naturel : AI Service, V4, runtime Python séparé). [ADR-016](ADR-016-ghcr-registry.md) (GHCR comme registre) reste valide tel quel : les images sont toujours publiées sur `ghcr.io`, seul le mécanisme qui les récupère change (`docker compose pull` au lieu de `kubectl`/imagePullSecret implicite).

## Conséquences

- RAM libérée du socle K3s, disponible pour l'application elle-même — sur un VPS déjà identifié comme à la limite ([[vps-resource-constraint]] côté mémoire du projet), c'est le principal bénéfice recherché.
- Perte de l'argument "démontrer une compétence Kubernetes réelle" explicitement cité comme raison d'être d'[ADR-008](ADR-008-k3s.md) — assumé : les manifestes restent dans le repo comme preuve de compétence (portfolio), mais ne sont plus le mécanisme de déploiement réel.
- Un seul outil (Docker Compose) à connaître pour le local et la prod, plutôt que deux (Compose local, kubectl/Helm en prod) — réduit la charge cognitive pour un projet solo.
- Pas de rolling update ni d'auto-healing aussi sophistiqué qu'un ReplicaSet K8s — acceptable : un seul réplica de toute façon (VPS mono-instance), `restart: unless-stopped` suffit pour le cas d'usage réel.
- Migration future vers K8s (si le projet en avait de nouveau besoin) reste possible sans perte : les manifestes `infrastructure/kubernetes/api/` existent déjà et suivent la même convention de nommage.

Remplace [ADR-008](ADR-008-k3s.md) et [ADR-015](ADR-015-traefik-entree-directe.md).
