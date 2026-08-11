# ADR-005 — Choix de Kafka

## Statut

Accepté (implémentation différée à la V2 — introduction avec Statistics/Notification Service)

## Contexte

Plusieurs services doivent réagir aux mêmes événements de match (but, carton, fin de match) sans que Match Service ait à connaître ses consommateurs. Un appel REST direct de Match vers chaque consommateur créerait un couplage fort et un point de fragilité (si un consommateur est indisponible, l'émetteur ne doit pas être bloqué).

## Options envisagées

- **Appels REST directs de Match vers chaque service consommateur** — simple mais couple fortement les services entre eux, fragile à l'ajout d'un nouveau consommateur.
- **RabbitMQ** — bon choix pour du message queuing classique, mais moins adapté au rejeu d'événements (utile pour reconstruire des statistiques a posteriori) et moins central dans l'écosystème data/streaming que Kafka.
- **Kafka** — pub/sub découplé, rejeu possible, standard de facto pour l'architecture événementielle, forte valeur de démonstration technique pour l'objectif CV.

## Décision

Kafka pour les événements de match diffusés à plusieurs consommateurs (`match.started`, `match.goal_scored`, `match.card_received`, `match.substitution`, `match.finished`). Introduit seulement quand un premier consommateur asynchrone existe réellement (Statistics Service, V2) — pas avant, pour ne pas alourdir l'environnement local sans besoin.

## Conséquences

- Découplage fort entre Match Service (producteur) et ses consommateurs (Statistics, Notification, futur Analytics).
- Complexité opérationnelle supplémentaire (broker à opérer, monitorer, sauvegarder) — assumée à partir du moment où elle est justifiée par un besoin réel.
- Chaque événement documenté (nom, payload versionné, producteur, consommateurs) dans `docs/events/` avant sa première publication.
