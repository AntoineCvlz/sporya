# ADR-001 — Choix de Java / Spring Boot pour le backend

## Statut

Accepté

## Contexte

Sporya vise à démontrer des compétences backend solides et employables (objectif CV explicite : Software/Backend Engineer). Le choix du langage/framework backend structure tout le reste (écosystème de tests, observabilité, sécurité, packaging).

## Options envisagées

- **Java / Spring Boot** — écosystème mature (Spring Security, Spring Data JPA, Testcontainers, Actuator), très demandé sur le marché backend visé, bonne intégration avec Kubernetes/observabilité.
- **Node.js / NestJS** — cohérent avec un frontend TypeScript (un seul langage), mais écosystème de tests d'intégration et de sécurité moins mature pour ce type de projet.
- **Kotlin / Spring Boot** — même écosystème que Java avec une syntaxe plus concise, mais moins directement aligné avec l'objectif CV (Java reste la cible la plus demandée pour les postes visés).

## Décision

Java + Spring Boot pour tous les services backend (sauf AI Service, voir [ADR-010](ADR-010-ai-service-python.md)).

## Conséquences

- Accès à un écosystème de test et de sécurité éprouvé (JUnit, Mockito, Testcontainers, Spring Security).
- Cohérence technique entre tous les services métier, ce qui facilite la duplication du gabarit de service (voir [ADR-004](ADR-004-service-de-reference.md)).
- Deux stacks à maintenir dans le projet (Java + Python pour l'AI Service) — assumé, car justifié techniquement.
