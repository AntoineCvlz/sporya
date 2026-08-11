# ADR-007 — Choix de WebSocket pour le temps réel

## Statut

Accepté (implémentation différée à la V2)

## Contexte

Le suivi d'un match en direct (score, timeline) doit se mettre à jour côté frontend sans action de l'utilisateur, avec une latence faible et sans surcharger le backend de requêtes répétées.

## Options envisagées

- **Polling REST périodique** — simple à implémenter, mais latence perçue plus élevée et charge serveur inutile en dehors des moments où un événement se produit réellement.
- **Server-Sent Events (SSE)** — flux unidirectionnel serveur → client suffisant pour ce cas d'usage précis, plus simple que WebSocket, mais moins extensible si un besoin bidirectionnel apparaît plus tard (ex. interactions live).
- **WebSocket** — bidirectionnel, latence faible, standard largement reconnu, bonne valeur de démonstration technique.

## Décision

WebSocket pour la diffusion des mises à jour de match en direct vers le frontend, introduit en V2 avec le reste du temps réel (Statistics/Notification, Kafka).

## Conséquences

- Frontend reçoit les mises à jour de score/timeline sans polling.
- Nécessite une gestion explicite de la reconnexion côté frontend (perte de connexion, reprise d'état).
- Introduit un composant supplémentaire (WebSocket Gateway ou endpoint dédié) à sécuriser et monitorer.
