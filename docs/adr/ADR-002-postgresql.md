# ADR-002 — Choix de PostgreSQL

## Statut

Accepté

## Contexte

Le domaine (clubs, équipes, joueurs, matchs, événements, statistiques) est fortement relationnel avec de vraies contraintes d'intégrité (un match a exactement deux équipes distinctes, un joueur appartient à une équipe pour une saison donnée, un score dérivé d'événements). Le projet vise aussi à démontrer une compétence base de données largement demandée sur le marché backend.

## Options envisagées

- **PostgreSQL** — relationnel, contraintes fortes, JSON natif pour les cas moins structurés (payloads d'événements), écosystème Kubernetes mature (opérateurs, backups), gratuit et open source.
- **MySQL/MariaDB** — alternative relationnelle viable, mais moins riche pour les cas hybrides (JSONB, fonctions analytiques utiles pour Statistics/Analytics).
- **MongoDB** — pertinent pour des documents peu structurés, mais mal aligné avec le besoin d'intégrité référentielle du domaine (matchs, événements, statistiques).

## Décision

PostgreSQL pour tous les services qui persistent des données métier, un schéma par service (voir [ADR-012](ADR-012-schema-par-service.md)).

## Conséquences

- Contraintes d'intégrité fortes disponibles nativement pour un domaine qui en a réellement besoin.
- Un seul moteur à opérer/monitorer/sauvegarder, même avec plusieurs schémas.
- JSONB disponible pour les payloads d'événements évolutifs sans migration systématique.
