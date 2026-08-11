# Docker

Contient les `Dockerfile` réutilisables/partagés et la documentation du socle Docker Compose.

Le `docker-compose.yml` racine du projet sera introduit à la Phase 2, avec PostgreSQL seul dans un premier temps. Chaque service ajoute son propre `Dockerfile` dans `services/<nom>-service/` au moment de sa construction ; ce dossier documente les conventions partagées (voir [`docs/conventions.md`](../../docs/conventions.md)).

Rien n'est encore en place (Phase 1 — Repository).
