# ADR-011 — Modèle RBAC par club plutôt que rôle global

## Statut

Accepté

## Contexte

Un même utilisateur peut être joueur dans un club et analyste dans un autre, ou coach dans un club et simple spectateur ailleurs. Un modèle de rôle unique et global par utilisateur ne représente pas correctement ce cas, pourtant courant dès qu'on imagine un usage multi-club.

## Options envisagées

- **Rôle global par utilisateur** (`ADMIN`, `COACH`, `PLAYER`...) — simple à modéliser mais incapable de représenter un utilisateur avec des rôles différents selon le club.
- **Rôle par club (`ClubMembership` = utilisateur + club + rôle)** — modélise fidèlement le cas réel, un peu plus de complexité au niveau de la vérification des permissions (il faut toujours connaître le club concerné par l'action).

## Décision

RBAC par club : chaque `ClubMembership` porte un rôle (`ADMIN`, `COACH`, `ANALYST`, `PLAYER`, `VIEWER`). Toute action métier sensible (créer un match, ajouter un événement) vérifie le rôle de l'utilisateur **pour le club concerné**, pas un rôle global.

## Conséquences

- Modélise correctement le cas multi-club sans réécriture ultérieure.
- Chaque vérification de permission doit connaître le club concerné par la ressource manipulée — légèrement plus de contexte à propager dans les requêtes/tokens.
- Le JWT porte les memberships (club + rôle) de l'utilisateur pour permettre une vérification locale sans appel réseau (voir [ADR-013](ADR-013-jwt-stateless.md)).
