# Auth Service

Service de référence (gabarit) — voir [ADR-004](../../docs/adr/ADR-004-service-de-reference.md).

## Responsabilité

Identité, authentification, rôles par club ([ADR-011](../../docs/adr/ADR-011-rbac-par-club.md)), émission de JWT ([ADR-013](../../docs/adr/ADR-013-jwt-stateless.md)).

## API

Contrat : [`docs/api/auth-service.yaml`](../../docs/api/auth-service.yaml), Swagger UI en local sur `/swagger-ui.html`.

| Route | Auth | Description |
|---|---|---|
| `POST /api/v1/auth/register` | Publique | Créer un compte (email + mot de passe, hashé BCrypt) |
| `POST /api/v1/auth/login` | Publique | Authentification, renvoie un access token JWT (RS256, 15 min) |
| `GET /api/v1/auth/me` | Bearer JWT | Profil de l'utilisateur authentifié |

Pas encore de `ClubMembership` dans le token (claim `memberships` vide) : Club Service n'existe pas encore, voir [ADR-011](../../docs/adr/ADR-011-rbac-par-club.md).

Schéma PostgreSQL : `auth` (voir [ADR-012](../../docs/adr/ADR-012-schema-par-service.md)), créé automatiquement par Flyway au démarrage. Migrations dans `src/main/resources/db/migration/`.

## Clés JWT (RS256)

Auth Service signe avec une clé privée ; les autres services (à venir) valident avec la clé publique correspondante, sans appel réseau (ADR-013). Fournies via `JWT_PRIVATE_KEY_BASE64` / `JWT_PUBLIC_KEY_BASE64` — le PEM complet, encodé en base64 sur une seule ligne (évite les soucis de sauts de ligne dans les fichiers `.env` / Secrets K8s).

`.env.example` contient une paire **de développement uniquement**, générée pour ce repo — à ne jamais réutiliser pour un déploiement réel. Pour en générer une nouvelle (dev ou prod) :

```bash
openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:2048 -out private.pem
openssl rsa -pubout -in private.pem -out public.pem
base64 -w0 private.pem   # -> JWT_PRIVATE_KEY_BASE64
base64 -w0 public.pem    # -> JWT_PUBLIC_KEY_BASE64
```

En production (VPS), ces valeurs vivent uniquement dans le Secret K8s `auth-service-jwt-keys` — jamais commitées (voir `docs/deployment/README.md`).

## Lancer en local

Nécessite le socle Postgres du repo (`docker compose up postgres` à la racine), puis :

```bash
./mvnw spring-boot:run
```

Variables d'environnement (valeurs par défaut alignées avec `.env.example` à la racine) : `DB_HOST`, `DB_PORT`, `DB_NAME`, `DB_USER`, `DB_PASSWORD`, `JWT_PRIVATE_KEY_BASE64`, `JWT_PUBLIC_KEY_BASE64`.

## Vérifier

```bash
./mvnw verify   # tests (Testcontainers démarre un Postgres jetable) : register -> login -> /me

curl -X POST localhost:8080/api/v1/auth/register \
  -H 'Content-Type: application/json' \
  -d '{"email":"test@sporya.local","password":"correct-horse-battery"}'

curl -X POST localhost:8080/api/v1/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"email":"test@sporya.local","password":"correct-horse-battery"}'

curl localhost:8080/api/v1/auth/me -H "Authorization: Bearer <accessToken>"
```

## Observabilité

Logs JSON structurés (stdout), en-tête `X-Correlation-Id` porté/généré par requête (`CorrelationIdFilter`) — voir [`docs/conventions.md`](../../docs/conventions.md#observabilité). `/actuator/health` et `/actuator/prometheus` accessibles en interne (pas exposés via l'Ingress public, voir `infrastructure/kubernetes/README.md`).
