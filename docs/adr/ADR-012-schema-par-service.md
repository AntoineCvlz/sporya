# ADR-012 — Un schéma PostgreSQL par service dès sa création

## Statut

Accepté

## Contexte

En microservices, une base de données partagée entre services est un anti-pattern classique : elle recrée un couplage fort (un monolithe "caché") malgré des déploiements séparés. Le projet démarrant directement en microservices (voir [ADR-003](ADR-003-microservices-des-le-mvp.md)), la question de l'ownership des données se pose dès le premier service.

**Note (2026-08-14, [ADR-017](ADR-017-monolithe-modulaire.md))** : le projet est depuis passé d'un microservice par contexte à un monolithe modulaire (un module = un package Java). La décision ci-dessous ne change pas : elle s'applique désormais par module plutôt que par service réseau, et devient d'autant plus importante qu'elle seule empêche le monolithe de dégénérer en "monolithe de données" avec accès direct cross-module.

## Options envisagées

- **Une base partagée avec des tables communes** — plus simple à requêter (jointures directes), mais recrée un couplage fort entre services censés être indépendants.
- **Une instance PostgreSQL séparée par service dès le V1** — isolation maximale, mais coût en ressources significatif sur un seul petit VPS pour 3 services dès le MVP.
- **Un schéma PostgreSQL séparé par service, co-hébergés sur une seule instance PostgreSQL au démarrage** — isolation logique complète (pas de jointure cross-schéma, uniquement des références par ID ou des appels REST), tout en économisant les ressources du VPS. La séparation en instances distinctes reste possible à tout moment sans changement de modèle.

## Décision

Chaque service possède son propre schéma PostgreSQL dès sa création (`auth`, `club`, `match`, ...). Aucune foreign key ni jointure SQL cross-schéma. Un seul conteneur PostgreSQL héberge les schémas au démarrage, par économie de ressources — c'est un détail de déploiement, pas une exception au principe d'isolation.

## Conséquences

- Isolation logique des données garantie dès le premier service, pas de "monolithe de données" caché.
- Requêtes cross-domaine explicites (REST) plutôt qu'implicites (jointure SQL), donc plus visibles et plus faciles à faire évoluer.
- Migration future vers une instance PostgreSQL par service ne nécessite aucun changement de modèle de données, seulement un changement de déploiement.
