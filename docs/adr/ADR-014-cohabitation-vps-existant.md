# ADR-014 — Cohabitation avec l'infrastructure existante du VPS

## Statut

Remplacé par [ADR-015](ADR-015-traefik-entree-directe.md) — `collector-shop` a été décommissionné du VPS le 12/08/2026, la contrainte de cohabitation qui motivait cette décision n'existe plus.

## Contexte

Le VPS cible ([ADR-008](ADR-008-k3s.md)) héberge déjà une application en production (`collector-shop.antoine-cuvilliez.fr`), servie via Docker + Caddy, Caddy occupant les ports 80/443 et gérant déjà le TLS (Let's Encrypt automatique) pour le domaine `antoine-cuvilliez.fr`. K3s installe par défaut Traefik comme ingress controller, exposé par le Service LB de K3s (Klipper) qui **revendique aussi les ports 80/443 de l'hôte** — un conflit direct avec Caddy, et un risque réel de casser collector-shop si non anticipé.

## Options envisagées

- **Remplacer Caddy par l'ingress K3s comme point d'entrée unique**, migrer collector-shop dans K3s — techniquement plus "propre" à terme, mais très invasif pour une application tierce déjà en production, hors du périmètre de ce projet, et risqué à faire sans tests approfondis sur collector-shop.
- **Laisser Traefik prendre 80/443 et faire cohabiter les deux reverse proxies avec des règles de routage complexes (iptables, ports différents par app)** — fragile, difficile à maintenir, mauvaise séparation des responsabilités.
- **Caddy reste l'unique point d'entrée TLS du VPS ; le Traefik de K3s est reconfiguré pour écouter sur un port interne (NodePort) plutôt que sur 80/443, et Caddy fait un reverse-proxy simple vers ce port pour `sporya.antoine-cuvilliez.fr`.**

## Décision

Caddy reste le point d'entrée HTTP/HTTPS unique du VPS pour tous les domaines, y compris `sporya.antoine-cuvilliez.fr`. Le Service Traefik de K3s est reconfiguré en `NodePort` (au lieu de `LoadBalancer`/Klipper qui bind 80/443) via un `HelmChartConfig`. collector-shop n'est ni touché ni redémarré.

## Conséquences

- Aucun risque pour collector-shop : sa configuration Caddy existante n'est pas modifiée, seule une nouvelle entrée est ajoutée.
- `cert-manager` n'est **pas** nécessaire dans K3s pour l'instant : Caddy gère déjà le TLS pour tout le domaine `antoine-cuvilliez.fr`, y compris le sous-domaine Sporya. À réévaluer seulement si Sporya quitte un jour ce VPS partagé pour son propre serveur dédié.
- L'ingress K3s (Traefik) sert uniquement le routage HTTP interne (host-based routing entre futurs services Sporya), pas le TLS — le certificat est géré une seule fois, au niveau de Caddy.
- Point de vigilance en entretien : cette décision illustre un cas réel d'intégration dans une infrastructure existante plutôt qu'un environnement greenfield — bon exemple de compromis pragmatique à expliquer.
