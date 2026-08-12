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

## Entrée HTTPS et TLS

Traefik (ingress K3s par défaut) est le point d'entrée direct du VPS sur les ports 80/443 (`LoadBalancer` natif, Klipper) — voir [ADR-015](../../docs/adr/ADR-015-traefik-entree-directe.md). `cert-manager` ([`config/cert-manager.yaml`](config/cert-manager.yaml)) émet et renouvelle les certificats Let's Encrypt via deux `ClusterIssuer` ([`config/letsencrypt-issuers.yaml`](config/letsencrypt-issuers.yaml)) : `letsencrypt-staging` pour développer un `Ingress` sans risquer le rate limit, `letsencrypt-prod` une fois validé.

## Statut

Phase 5 en cours. Namespace `sporya`, Traefik et cert-manager appliqués et vérifiés sur le VPS. Prochaine étape (Phase 6) : premier service minimal + `Ingress` réel pour valider l'émission de certificat de bout en bout.
