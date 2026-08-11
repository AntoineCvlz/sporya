# C4 — Diagramme de contexte

```mermaid
flowchart TB
    coach["Entraîneur / Analyste\n(staff de club)"]
    player["Joueur"]
    admin["Admin de club"]
    fan["Spectateur"]

    subgraph SYS["Sporya — Plateforme d'analyse sportive"]
        sporya["Gère clubs, équipes, matchs,\nstatistiques et analytics football"]
    end

    dataSource["Source de données sportives\n(mock/CSV puis API externe — V3)"]
    llm["Fournisseur LLM\n(AI Service — V4)"]
    mail["Service d'emailing\n(notifications — optionnel)"]

    coach -->|utilise| sporya
    player -->|consulte| sporya
    admin -->|configure| sporya
    fan -->|suit les matchs| sporya

    sporya -->|importe des données| dataSource
    sporya -->|interroge| llm
    sporya -.->|envoie des emails| mail
```

Au MVP (V1), seuls les acteurs humains et Sporya existent : les systèmes externes (source de données, LLM, email) sont introduits progressivement (flèches en pointillés = pas encore construit). Voir [`overview.md`](overview.md) pour le détail des personas et [`containers.md`](containers.md) pour le niveau suivant.
