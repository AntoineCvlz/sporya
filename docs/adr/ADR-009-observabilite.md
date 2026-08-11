# ADR-009 — Stratégie d'observabilité

## Statut

Accepté (mise en place progressive à partir de la Phase 4)

## Contexte

Un système en microservices sans observabilité est ingérable en production, même à petite échelle — un incident sur un service devient impossible à diagnostiquer sans logs corrélés, métriques et (à partir de plusieurs services communicants) traces distribuées.

## Options envisagées

- **Logs applicatifs seuls (fichiers, `docker logs`)** — insuffisant dès qu'il y a plusieurs services et un besoin de corrélation entre eux.
- **Solution SaaS externe (Datadog, New Relic...)** — bonne expérience mais coût récurrent non justifié pour un projet portfolio, et dépendance à un tiers pour une démonstration qui doit rester auto-hébergée.
- **OpenTelemetry + Prometheus + Grafana, auto-hébergés sur le VPS** — standard ouvert, gratuit, démontre une compétence directement transférable, s'intègre nativement avec Spring Boot Actuator/Micrometer.

## Décision

Logs structurés (JSON) + `Correlation-ID` dès le premier service (Phase 4). Métriques via Micrometer/Prometheus dès le premier déploiement. Traces distribuées via OpenTelemetry introduites lorsque plusieurs services communiquent réellement entre eux (V2), pas avant — une trace sur un seul service n'apporte pas de valeur suffisante pour justifier la mise en place immédiate.

## Conséquences

- Diagnostic possible dès le premier service en production (logs + métriques + health checks).
- Dashboards Grafana comme preuve tangible de maîtrise de l'observabilité en entretien.
- Coût d'exploitation supplémentaire sur le VPS (Prometheus + Grafana à monitorer eux-mêmes) — budgété dans le dimensionnement des ressources K3s.
