# Monitoring

## En place (Phase 4)

| Service | Rôle | Port local |
|---|---|---|
| `postgres-exporter` | Expose les métriques PostgreSQL (`pg_up`, connexions, etc.) | `9187` |
| `prometheus` | Scrape `postgres-exporter` (+ lui-même) | `9090` |
| `grafana` | Datasource Prometheus provisionnée automatiquement + dashboard `platform-overview.json` | `3001` (admin/admin par défaut, à changer via `.env`) |

```text
infrastructure/monitoring/
├── prometheus/
│   └── prometheus.yml              # scrape configs — un job par service, ajouté à sa construction
└── grafana/
    ├── provisioning/
    │   ├── datasources/datasource.yml
    │   └── dashboards/dashboards.yml
    └── dashboards/
        └── platform-overview.json  # PostgreSQL up, cibles Prometheus, connexions actives
```

Rien n'est configuré à la main dans l'UI Grafana : datasource et dashboard sont provisionnés par fichiers, donc reproductibles et versionnés (voir [ADR-009](../../docs/adr/ADR-009-observabilite.md)).

## À faire au moment de la construction d'un service (à partir de la Phase 6)

1. Exposer `/actuator/prometheus` (Micrometer) côté service.
2. Ajouter un `job_name` dans `prometheus.yml` (exemple déjà présent en commentaire).
3. Étendre `platform-overview.json` — ou ajouter un dashboard dédié au service — avec latence, taux d'erreur, JVM.

## Logs et traces

Logs structurés (JSON) + `Correlation-ID` : convention posée dans [`docs/conventions.md`](../../docs/conventions.md#observabilité), appliquée dès le premier service. Traces distribuées (OpenTelemetry) introduites en V2, une fois que plusieurs services communiquent réellement entre eux.
