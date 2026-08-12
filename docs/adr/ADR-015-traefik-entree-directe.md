# ADR-015 — Traefik comme point d'entrée HTTPS direct du VPS

## Statut

Accepté

## Contexte

[ADR-014](ADR-014-cohabitation-vps-existant.md) mettait en place un contournement (Traefik en `NodePort`, Caddy en façade TLS) pour cohabiter avec `collector-shop`, alors en production sur le même VPS. `collector-shop` a été décommissionné le 12/08/2026 (conteneurs, volumes et le proxy Caddy associé supprimés) : la contrainte de cohabitation disparaît, et le VPS n'héberge plus que Sporya.

## Options envisagées

- **Garder Caddy en façade malgré tout** — utile seulement s'il fallait héberger d'autres applications non-Kubernetes sur ce VPS à l'avenir ; ajoute une couche et un point de configuration supplémentaires (le Caddyfile, hors du dépôt Git de ce projet) sans bénéfice pour un VPS mono-projet.
- **Traefik (ingress K3s) reprend directement les ports 80/443 de l'hôte**, en `LoadBalancer` natif (Klipper) — configuration par défaut de K3s, aucun contournement à maintenir.

## Décision

Traefik reprend les ports 80/443 en `LoadBalancer` natif. Le `HelmChartConfig` de reconfiguration en `NodePort` (ADR-014) est supprimé. Le TLS n'étant plus géré par Caddy, `cert-manager` est installé dans K3s (même mécanisme `HelmChart` que Traefik, cohérent avec le reste du cluster) avec deux `ClusterIssuer` Let's Encrypt (`letsencrypt-staging` et `letsencrypt-prod`), challenge `HTTP-01` via l'ingress class `traefik` — pas de dépendance à l'API DNS d'IONOS.

## Conséquences

- Chaîne de bout en bout plus simple : `DNS → VPS → K3s/Traefik`, sans proxy intermédiaire ni fichier de configuration hors dépôt.
- Le TLS devient géré par le cluster lui-même (`cert-manager`), donc versionné et reproductible avec le reste des manifestes K8s — contrairement à Caddy qui vivait hors du dépôt.
- `letsencrypt-staging` doit être utilisé pendant le développement de tout nouvel `Ingress` (certificats non fiables mais sans rate limit), puis basculer sur `letsencrypt-prod` une fois le challenge validé.
- Si le VPS devait un jour héberger une autre application non-Kubernetes, il faudrait soit la déployer dans K3s également, soit réintroduire un reverse proxy dédié devant K3s (retour à un schéma proche de l'ADR-014).
- Point de vigilance en entretien : illustre qu'une décision d'architecture prise pour un contexte donné (cohabitation) doit être révisée quand ce contexte change, plutôt que maintenue par inertie.
