# Sporya

**Plateforme d'analyse et de suivi des performances sportives — football (MVP), conçue pour évoluer vers le multi-sport.**

> Statut actuel : **Phase 4 — Observabilité**. Aucun service métier n'est encore développé. Voir la [roadmap](docs/architecture/overview.md#roadmap-résumée) pour la suite.

---

## En bref

Sporya permet à des clubs, entraîneurs, analystes et joueurs de gérer des clubs, équipes et compétitions, de suivre des matchs (y compris en direct), et d'analyser des statistiques individuelles et collectives — avec, à terme, un assistant IA capable de répondre à des questions en langage naturel sur des données réelles et vérifiées (jamais inventées).

Le projet est développé comme un produit logiciel complet : architecture justifiée, tests automatisés, CI/CD, observabilité, déploiement Kubernetes (K3s) sur VPS réel — pas seulement un CRUD de démonstration.

## Architecture en un coup d'œil

- **Style** : monolithe modulaire, construit **un module à la fois** (Auth → Club → Match, puis les suivants selon leurs dépendances réelles) — un seul déployable Spring Boot, un package Java par contexte borné. Voir [ADR-017](docs/adr/ADR-017-monolithe-modulaire.md).
- **Backend** : Java / Spring Boot par service.
- **Frontend** : React / TypeScript.
- **Données** : PostgreSQL, un schéma par service dès sa création.
- **Asynchrone** : Kafka (introduit à partir du Statistics/Notification Service), Redis (introduit quand un besoin réel apparaît).
- **Temps réel** : WebSocket pour le suivi de match en direct.
- **Infra** : Docker en local, K3s sur VPS, GitHub Actions pour la CI/CD.
- **Observabilité** : logs structurés, OpenTelemetry, Prometheus, Grafana.

Le détail complet (personas, cas d'usage, bounded contexts, C4, modèle de données, sécurité, roadmap, risques) est dans [`docs/architecture/overview.md`](docs/architecture/overview.md).

## Structure du repository

```text
Sporya/
├── docs/                 Documentation vivante (architecture, ADR, API, DB, déploiement, sécurité, events)
├── infrastructure/       Docker, Kubernetes/K3s, monitoring
├── backend/api/          Monolithe Spring Boot, un package Java par module métier
├── frontend/             Application React
├── tests/                Tests bout en bout, cross-modules
├── scripts/               Scripts d'exploitation (provisioning, backups, etc.)
└── .github/workflows/    Pipelines CI/CD
```

`backend/api/` se remplit module après module (voir la roadmap), pas d'un coup — mais reste un seul déployable dès le premier module.

## Démarrer en local

```bash
cp .env.example .env
docker compose up -d
```

Démarre le socle local — aucun service applicatif encore construit (voir la roadmap) :

| Outil | URL locale | Identifiants |
|---|---|---|
| PostgreSQL | `localhost:5432` | `sporya` / `sporya` |
| Adminer | http://localhost:8081 | serveur `postgres`, `sporya` / `sporya` |
| Prometheus | http://localhost:9090 | — |
| Grafana | http://localhost:3001 | `admin` / `admin` (à changer via `.env`) |

`docker compose down -v` supprime aussi les données persistées.

## Documentation

| Sujet | Emplacement |
|---|---|
| Architecture (personas, C4, bounded contexts, roadmap) | [`docs/architecture/`](docs/architecture/) |
| Décisions d'architecture (ADR) | [`docs/adr/`](docs/adr/) |
| Modèle de données | [`docs/database/`](docs/database/) |
| Conventions (Git, commits, branches) | [`docs/conventions.md`](docs/conventions.md) |
| API (OpenAPI, par service) | [`docs/api/`](docs/api/) |
| Déploiement | [`docs/deployment/`](docs/deployment/) |
| Sécurité | [`docs/security/`](docs/security/) |
| Catalogue d'événements Kafka | [`docs/events/`](docs/events/) |

## Contexte du projet

Sporya est un projet portfolio personnel, développé en solo, avec l'intention explicite de rester réalisable par une seule personne à chaque étape. Chaque décision technique importante est documentée (ADR) et justifiée par un besoin réel du produit plutôt que par la volonté d'ajouter une technologie au CV.

## Licence

[MIT](LICENSE)
