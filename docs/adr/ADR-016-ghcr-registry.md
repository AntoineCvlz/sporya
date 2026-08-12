# ADR-016 — GitHub Container Registry comme registre d'images

## Statut

Accepté

## Contexte

À partir du premier service construit (Auth, Phase 6), les images Docker doivent être publiées quelque part pour que K3s puisse les récupérer sur le VPS — voir section "Conteneur Registry" du cadrage initial.

## Options envisagées

- **Docker Hub** — standard, mais quota de pull anonyme limité sur le tier gratuit, et nécessite un compte/des identifiants séparés à gérer comme secrets GitHub.
- **Registre auto-hébergé sur le VPS** — aucune dépendance externe, mais ajoute un service de plus à faire tourner et sécuriser (auth, stockage, TLS) sur un VPS déjà partagé entre K3s et le reste, sans bénéfice réel pour un projet solo.
- **GitHub Container Registry (ghcr.io)** — même écosystème que le code et la CI (GitHub Actions), authentification via le `GITHUB_TOKEN` déjà disponible dans chaque run (pas de secret supplémentaire à créer/gérer), pas de quota bloquant pour un usage portfolio.

## Décision

`ghcr.io/<owner>/<service>` comme registre pour toutes les images. Publication automatique depuis `service-ci.yml` (le workflow réutilisable, voir [ADR-004](ADR-004-service-de-reference.md)) uniquement sur push vers `main` — les Pull Requests construisent et scannent (Trivy) l'image sans la publier. Tag = SHA du commit, jamais `latest` (voir section "Conteneur Registry" du cadrage).

## Conséquences

- Aucun secret de registre supplémentaire à provisionner : `GITHUB_TOKEN` suffit en écriture (`permissions: packages: write`).
- Le package GHCR doit être rendu public (ou un `imagePullSecret` créé dans K3s référençant un token avec droit `read:packages`) pour que le VPS puisse `docker pull`/`kubectl` sans authentification supplémentaire — choix : package public, acceptable pour un projet portfolio destiné à être montré publiquement.
- Couplage à GitHub comme plateforme — accepté, cohérent avec le reste de la chaîne CI/CD déjà sur GitHub Actions.
