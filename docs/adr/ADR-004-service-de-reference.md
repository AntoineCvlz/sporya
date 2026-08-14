# ADR-004 — Service de référence (Auth) comme gabarit pour les suivants

## Statut

Remplacé par [ADR-017](ADR-017-monolithe-modulaire.md) — un seul déployable (`backend/api`) signifie qu'il n'y a plus de gabarit à copier par service ; la convention interne `controller/application/domain/infrastructure` qu'il établissait reste utilisée, mais comme structure d'un package par module à l'intérieur du monolithe.

## Contexte

[ADR-003](ADR-003-microservices-des-le-mvp.md) impose de construire un service à la fois. Sans discipline, chaque nouveau service risque de réinventer sa propre structure (Dockerfile, pipeline CI, manifestes K8s, config d'observabilité), ce qui multiplierait le travail et introduirait des divergences difficiles à maintenir seul.

## Options envisagées

- **Repartir de zéro à chaque service** — flexible mais coûteux en temps et source d'incohérences.
- **Bibliothèque partagée (starter Spring Boot maison) dès le premier service** — factorise tôt, mais impose une abstraction avant d'avoir vu au moins deux ou trois cas réels (risque de sur-généraliser une lib sur la base d'un seul consommateur).
- **Gabarit copié** : le premier service construit (Auth) établit le standard ; les suivants copient sa structure et l'adaptent. Une lib partagée n'est extraite que si la duplication devient réellement douloureuse (voir aussi le risque documenté dans `docs/architecture/overview.md`).

## Décision

Auth Service, premier service construit, sert de gabarit : structure interne (`controller/application/domain/infrastructure`), Dockerfile multi-stage, pipeline CI (reusable workflow), manifestes K8s, configuration d'observabilité. Les services suivants (Club, Match, ...) copient ce gabarit plutôt que d'en inventer un nouveau.

## Conséquences

- Les services 2 à 8 coûtent nettement moins cher en travail d'infrastructure que le premier.
- Cohérence forte entre services, ce qui facilite la maintenance solo.
- Duplication de code assumée à court terme (2-3 services) plutôt qu'une abstraction prématurée ; à réévaluer si la duplication devient un vrai point de friction (candidat naturel : librairie partagée pour la validation JWT et les logs structurés).
