# Sécurité

## Approche

| Sujet | Approche MVP | Évolution |
|---|---|---|
| Authentification | JWT émis par Auth Service (access + refresh), validé localement par chaque service ([ADR-013](../adr/ADR-013-jwt-stateless.md)) | OAuth2/OIDC si un besoin fédéré réel apparaît |
| Autorisation | RBAC par club : `ADMIN`, `COACH`, `ANALYST`, `PLAYER`, `VIEWER` ([ADR-011](../adr/ADR-011-rbac-par-club.md)) | Granularité plus fine si nécessaire |
| Secrets | Variables d'environnement (`.env` non commité) en local ; `Kubernetes Secrets` en cluster | Secret manager externe si la complexité opérationnelle le justifie |
| Validation des entrées | Bean Validation côté API | — |
| Rate limiting | Différé (introduit avec Redis, voir [ADR-006](../adr/ADR-006-redis.md)) | — |
| CORS | Restreint à l'origine du frontend dès le MVP | — |
| HTTPS | Dès le premier déploiement (Phase 6), via cert-manager | — |
| Audit logs | Log structuré des actions sensibles (création/suppression club, changement de rôle) dès le MVP | Corrélation avec l'observabilité |

## Règle absolue

Aucun secret dans Git. Vérification via hook pre-commit (`gitleaks` ou équivalent) à mettre en place avant le premier service (Phase 6).

## Statut

Aucune implémentation à ce stade (Phase 1 — Repository). Ce document sera complété au fil de la construction d'Auth Service.
