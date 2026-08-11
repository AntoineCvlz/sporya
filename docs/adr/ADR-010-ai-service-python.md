# ADR-010 — Architecture du AI Service (Python + FastAPI, séparé)

## Statut

Accepté (implémentation différée à la V4, après Statistics et Analytics)

## Contexte

Sporya doit permettre d'interroger les données sportives en langage naturel, sans jamais inventer de statistiques. L'écosystème LLM/embeddings est très majoritairement Python, alors que le reste du backend est en Java/Spring Boot.

## Options envisagées

- **Intégrer l'IA dans un service Java existant (ex. Analytics)** — évite un second runtime, mais l'écosystème Java pour LLM/embeddings est nettement moins mature que Python, et coupler l'IA à Analytics créerait une dépendance de déploiement inutile entre deux préoccupations très différentes.
- **AI Service séparé en Python/FastAPI, lecture seule via API interne** — runtime adapté à l'écosystème LLM, isolation claire : l'IA ne possède aucune donnée, elle interroge Statistics/Analytics via REST et ne fait que formuler la réponse.

## Décision

AI Service séparé, Python + FastAPI, construit en dernier (V4) une fois que Statistics et Analytics exposent des données fiables. L'IA accède aux données **uniquement** via les API Statistics/Analytics — jamais de génération libre de chiffres.

## Conséquences

- Runtime adapté à l'écosystème LLM (embeddings, vector DB si RAG introduit).
- Confiance dans les réponses : les statistiques citées par l'IA sont toujours réelles, car sourcées via API, jamais générées.
- Deuxième stack technique à opérer (Python) — assumé car techniquement justifié, pas pour la diversité en soi.
- RAG introduit uniquement s'il apporte une vraie valeur constatée, pas par défaut.
