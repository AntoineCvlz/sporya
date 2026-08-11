# ADR-008 — Choix de K3s comme cible Kubernetes

## Statut

Accepté (mise en place à la Phase 5)

## Contexte

Le projet doit démontrer une compétence Kubernetes réelle et être déployé sur un VPS déjà possédé, avec des ressources limitées (RAM/CPU d'un seul petit serveur).

## Options envisagées

- **Docker Compose seul, sans Kubernetes** — suffisant pour le développement local, mais ne démontre pas la compétence Kubernetes visée par le projet et ne reflète pas les pratiques de déploiement modernes recherchées pour les postes ciblés.
- **Kubernetes complet (kubeadm)** — trop lourd en ressources et en complexité opérationnelle pour un seul petit VPS géré par une seule personne.
- **K3s** — distribution Kubernetes légère, conçue pour des environnements à ressources limitées, compatible avec l'écosystème Kubernetes standard (mêmes manifestes, mêmes concepts), Traefik et cert-manager faciles à intégrer.

## Décision

K3s comme cible Kubernetes sur le VPS, à partir de la Phase 5, après un environnement Docker local fonctionnel (Phase 2).

## Conséquences

- Compétence Kubernetes démontrable avec un coût en ressources compatible avec un seul VPS.
- Les manifestes restent transférables vers un Kubernetes complet si le projet en avait un jour besoin.
- Kubernetes n'est introduit qu'après avoir un environnement local fonctionnel (règle 12 du cadrage) — jamais avant.
