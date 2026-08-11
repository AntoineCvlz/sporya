# Catalogue d'événements Kafka

Aucun événement n'existe encore — Kafka est introduit en V2, au moment de la construction du Statistics Service (voir [ADR-005](../adr/ADR-005-kafka.md)).

## Convention (à appliquer dès le premier événement)

Chaque événement documenté ici avant sa première publication, avec :

- **Nom** (ex. `match.goal_scored`)
- **Payload** (schéma versionné)
- **Version**
- **Producteur** (service unique)
- **Consommateurs** (liste)

## Catalogue cible (V2+)

```text
match.started
match.goal_scored
match.card_received
match.substitution
match.finished
statistics.updated
player.performance.updated
```

Producteur pour tous : Match Service (sauf `statistics.updated` et `player.performance.updated`, produits par Statistics Service).
