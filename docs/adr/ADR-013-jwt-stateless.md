# ADR-013 — Authentification stateless par JWT validé localement

## Statut

Accepté

## Contexte

Avec plusieurs services construits dès le V1 ([ADR-003](ADR-003-microservices-des-le-mvp.md)), chaque requête entrante doit être authentifiée. Si chaque service devait appeler Auth Service pour valider chaque requête, Auth deviendrait un point de couplage fort et un goulot d'étranglement malgré sa position de simple fondation.

## Options envisagées

- **Validation centralisée** : chaque service appelle Auth Service (REST) pour valider un token à chaque requête — couplage fort, latence ajoutée, point de défaillance unique en pratique.
- **Sessions côté serveur partagées (Redis)** — introduit une dépendance à Redis dès le V1 alors qu'aucun autre besoin ne le justifie encore (voir [ADR-006](ADR-006-redis.md)).
- **JWT stateless, validé localement par chaque service** — Auth Service émet un JWT signé (access + refresh token) contenant l'identité et les memberships (club + rôle, voir [ADR-011](ADR-011-rbac-par-club.md)) ; chaque service vérifie la signature avec la clé publique partagée, sans appel réseau.

## Décision

JWT stateless. Auth Service émet les tokens ; tous les autres services valident la signature localement (clé publique partagée) et lisent les claims (identité, memberships) directement dans le token.

## Conséquences

- Aucun appel réseau à Auth Service par requête — Auth reste une dépendance de construction (gabarit, émission de token), pas une dépendance d'exécution critique pour les autres services.
- Révocation immédiate d'un token plus complexe (nécessite une liste de révocation ou des tokens courts + refresh) — à traiter explicitement si le besoin se présente, pas ignoré.
- La clé de signature (ou clé publique en cas d'asymétrique) est un secret partagé entre services, à gérer comme tel (Kubernetes Secrets, jamais commité).
