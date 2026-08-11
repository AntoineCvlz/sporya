# ADR-006 — Choix de Redis

## Statut

Accepté (implémentation différée — introduit dès qu'un besoin réel apparaît, au plus tôt en V2)

## Contexte

Redis est régulièrement ajouté par réflexe (cache, sessions) sans besoin démontré. Le principe du projet est de n'introduire une technologie que si elle résout un problème réel (voir règle 2 du cadrage).

## Options envisagées

- **Ne pas utiliser Redis** — PostgreSQL suffit tant que le volume et la fréquence de lecture restent faibles.
- **Redis dès le V1** — anticipe un besoin de cache ou de rate limiting qui n'est pas encore démontré au MVP.
- **Redis introduit au moment où un besoin concret apparaît** (cache de lecture chaude sur le match en direct, rate limiting sur l'API publique, état éphémère de session).

## Décision

Ne pas inclure Redis au V1. L'introduire lors de la Phase où un besoin réel se présente — candidats identifiés : cache des données de match en direct (lecture très fréquente pendant un match), rate limiting sur les endpoints publics.

## Conséquences

- Environnement de développement V1 plus simple (un composant de moins).
- Chaque usage de Redis, une fois introduit, est documenté avec sa justification (cache ? rate limiting ? donnée temporaire ?) plutôt que listé comme un composant générique.
- Réévaluation explicite à chaque nouvelle phase plutôt qu'un ajout par défaut.
