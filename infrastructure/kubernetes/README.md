# Kubernetes / K3s

```text
kubernetes/
├── namespace/
├── ingress/          # routage par chemin : /api/auth, /api/clubs, /api/matches...
├── config/
├── secrets/
├── auth/             # ajouté en premier (Phase 6)
├── club/
├── match/
├── frontend/
├── postgres/
└── redis/            # ajouté quand Redis est réellement utilisé
```

Un dossier de manifestes par service, ajouté au moment où ce service est effectivement construit et déployé (voir [ADR-008](../../docs/adr/ADR-008-k3s.md)) — pas de dossiers vides pour des services non encore développés.

Rien n'est encore en place (Phase 1 — Repository). Mise en place prévue à la Phase 5.
