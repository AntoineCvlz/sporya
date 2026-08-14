# Catalogue d'événements internes

Aucun événement n'existe encore. Depuis [ADR-017](../adr/ADR-017-monolithe-modulaire.md) (2026-08-14), les échanges asynchrones entre modules (ex. Match → Statistics/Notification) passent par des événements **in-process** (Spring `ApplicationEventPublisher`), plus par Kafka — voir [ADR-005](../adr/ADR-005-kafka.md), remplacé. Kafka redevient une option si un vrai besoin de durabilité/rejeu ou de scaling externe apparaît plus tard ; ce catalogue resterait alors pertinent tel quel pour documenter les événements publiés sur le broker.

## Convention (à appliquer dès le premier événement)

Chaque événement documenté ici avant sa première publication, avec :

- **Nom** (ex. `match.goal_scored`)
- **Payload** (classe Java de l'événement, versionnée si publiée un jour hors process)
- **Producteur** (module unique)
- **Consommateurs** (liste des modules qui écoutent, via `@EventListener`/`@TransactionalEventListener`)

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

Producteur pour tous : module Match (sauf `statistics.updated` et `player.performance.updated`, produits par le module Statistics).
