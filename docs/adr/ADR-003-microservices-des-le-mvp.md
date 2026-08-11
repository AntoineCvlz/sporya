# ADR-003 — Microservices dès le MVP, construits un service à la fois

## Statut

Accepté

## Contexte

Deux stratégies de démarrage étaient envisageables : (a) un monolithe modulaire d'abord, extrait en services plus tard, ou (b) des microservices dès le départ. La contrainte principale est humaine, pas technique : un développeur solo, en soirées/week-ends, qui doit pouvoir livrer quelque chose de fonctionnel à chaque incrément sans porter un trop gros chantier avant la première mise en production réelle.

## Options envisagées

- **Monolithe modulaire d'abord, extraction ensuite** — moins de travail d'infrastructure au départ (un seul déployable), mais impose un chantier de refonte (extraction) avant de pouvoir montrer une vraie architecture microservices, et ce chantier est lui-même risqué à mener seul.
- **8 microservices dès le jour 1** — cible réaliste à terme, mais un chantier ingérable en une seule fois pour un projet solo (8 pipelines, 8 bases, 8 déploiements à faire fonctionner simultanément avant la première démonstration).
- **Microservices dès le début, construits un par un** — chaque service est un incrément complet et démontrable seul (Docker, CI, K8s, tests, doc) avant d'attaquer le suivant. Pas de refonte plus tard, mais plus de travail d'infrastructure répété par service.

## Décision

Démarrer directement en microservices, mais **un service à la fois**, dans l'ordre de leurs dépendances réelles (Auth → Club → Match pour le V1). Un bounded context ne devient un service que lorsque sa séparation est réellement justifiée (voir `docs/architecture/overview.md#ordre-de-construction-des-microservices`) — les contextes pas encore prêts restent des endpoints dérivés à l'intérieur d'un service voisin.

## Conséquences

- Pas de chantier de migration monolithe → microservices à mener plus tard.
- Chaque service livré est démontrable et déployable seul, ce qui convient à un rythme solo par petites itérations.
- Plus de travail d'infrastructure répété (Docker, CI, K8s) à chaque nouveau service — mitigé par un gabarit de service réutilisable (voir [ADR-004](ADR-004-service-de-reference.md)).
- Risque de créer un service prématurément si la discipline "un contexte ne devient service que si justifié" n'est pas respectée — à revérifier à chaque nouveau service.
