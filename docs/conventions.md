# Conventions du projet

## Git

### Branches

Stratégie volontairement simple (trunk-based) — pas de branche `develop` : sur un projet solo, une branche d'intégration supplémentaire ajoute une étape de merge sans bénéfice réel et complique la question de "quelle branche est déployée".

- `main` : toujours déployable, protégée.
- `feature/<sujet>` : une fonctionnalité ou un incrément (ex. `feature/auth-service-signup`).
- `fix/<sujet>` : un correctif.

Les branches sont fusionnées dans `main` via Pull Request, même en solo — cela garde un historique de revue et déclenche la CI avant fusion.

### Commits

Format [Conventional Commits](https://www.conventionalcommits.org/) :

```text
<type>: <description au présent, à l'impératif>

feat: add player management
fix: validate match score before persisting
refactor: extract statistics aggregation
test: add match integration tests
docs: add ADR-003
ci: add reusable service pipeline
chore: update dependencies
```

Types utilisés : `feat`, `fix`, `refactor`, `test`, `docs`, `ci`, `chore`, `perf`, `security`.

Un commit doit être atomique et compiler/passer les tests seul autant que possible.

## Documentation

- Toute décision d'architecture significative → un ADR dans `docs/adr/` (voir [`ADR-000`](adr/ADR-000-template.md) comme gabarit).
- Toute API exposée → un contrat OpenAPI dans `docs/api/<service>.yaml`, tenu à jour avant que l'implémentation ne diverge.
- Tout événement Kafka → documenté dans `docs/events/` (nom, payload, version, producteur, consommateurs) avant sa première publication.

## Structure d'un service (à partir de la Phase 6)

Chaque service sous `services/<nom>-service/` suit la même structure interne, établie par le premier service construit (Auth) :

```text
<nom>-service/
├── src/main/java/.../{controller,application,domain,infrastructure}/
├── src/test/java/...
├── Dockerfile
├── mvnw, mvnw.cmd, .mvn/       (Maven Wrapper — requis, utilisé tel quel par service-ci.yml)
├── pom.xml
└── README.md   (spécifique au service : responsabilité, API, comment le lancer seul)
```

Build tool : **Maven** (via wrapper), fixé pour tous les services Java — la pipeline CI réutilisable (`service-ci.yml`) suppose `./mvnw` et un plugin Spotless configuré (`spotless:check`).

Écarts à cette structure : possibles si justifiés par le domaine du service, mais à documenter dans le `README.md` du service concerné plutôt qu'à faire silencieusement.

## Principes de développement

Rappel des règles qui s'appliquent à tout le projet (détail dans `docs/architecture/overview.md`) :

1. Ne pas sur-ingénierer — un service n'est créé que lorsque sa raison d'être est réelle.
2. Justifier tout choix technique important par un ADR.
3. Ne jamais committer de secret — `.env` est ignoré, `.env.example` documente les variables attendues.
4. Toute fonctionnalité métier significative a des tests (unitaires a minima, intégration quand pertinent).
5. Travailler de façon incrémentale : chaque Pull Request doit laisser `main` dans un état fonctionnel.
