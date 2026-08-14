# C4 — Diagramme de conteneurs

> Depuis [ADR-017](../adr/ADR-017-monolithe-modulaire.md) (2026-08-14), l'architecture est un **monolithe modulaire** : un seul déployable Spring Boot (`backend/api`), les anciens "services" ci-dessous sont des **modules** (packages Java) à l'intérieur de ce déployable, pas des conteneurs séparés. Seul AI Service reste un processus externe (runtime Python différent).

## Vue cible (V5, indicative — ne pas construire d'un coup)

```mermaid
flowchart TB
    fe["Frontend\nReact + TypeScript"]
    ing["Ingress"]
    subgraph api["backend/api — monolithe Spring Boot"]
        auth["Auth"]
        club["Club"]
        match["Match"]
        stats["Statistics"]
        analytics["Analytics"]
        notif["Notification"]
        imp["Data Import"]
    end
    ai["AI Service\nPython + FastAPI\n(seul module resté un processus séparé)"]
    pg[("PostgreSQL\n(un schéma par module)")]
    redis[("Redis")]
    ws["WebSocket"]

    fe -->|REST /api/v1/*| ing
    fe <-->|WS| ws
    ing --> api
    match -->|événement in-process| stats & analytics & notif
    notif -->|push| ws
    ai -->|REST| stats
    ai -->|REST| analytics
    auth & club & match & stats & analytics & imp --> pg
    match & auth -.-> redis
```

## Vue MVP (V1 — ce qu'on construit réellement, dans cet ordre)

```mermaid
flowchart TB
    fe["Frontend\nReact + TypeScript"]
    ing["Ingress\n(routage /api/v1/* vers l'unique déployable)"]
    subgraph api["backend/api — monolithe Spring Boot"]
        auth["1. Auth\n(premier module / fondation)"]
        club["2. Club"]
        match["3. Match\n(référence de complexité métier)"]
    end
    pgA[("PostgreSQL\nschéma auth")]
    pgC[("PostgreSQL\nschéma club")]
    pgM[("PostgreSQL\nschéma match")]

    fe -->|REST /api/v1/*| ing
    ing --> api
    club -.->|appel Java direct, JWT déjà validé\ndans le même process| auth
    match -->|appel Java direct, lecture équipes/joueurs| club
    auth --> pgA
    club --> pgC
    match --> pgM
```

Chaque module est construit et testé avant d'attaquer le suivant, mais tous partagent le même Dockerfile/pipeline CI/déploiement K8s (un seul déployable). Un seul conteneur PostgreSQL héberge les 3 schémas au départ (économie de ressources VPS), sans jointure cross-module — uniquement des références par ID ou des appels Java explicites. Statistics, Analytics, Notification, Data Import arrivent en V2+ comme nouveaux modules du même monolithe ; AI Service (V4) est la seule exception qui reste un service réseau séparé, runtime Python oblige (voir [`overview.md`](overview.md#ordre-de-construction-des-modules)).
