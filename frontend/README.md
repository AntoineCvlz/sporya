# Frontend

React 19 + TypeScript + Vite, React Router, TanStack Query, Tailwind CSS + shadcn/ui.

## État actuel

Pages minimales pour Auth Service : inscription, connexion, tableau de bord protégé affichant le profil (`GET /api/v1/auth/me`). Rien d'autre (pas de Club/Match) — arrivera avec les services correspondants.

## Appels API

Toujours en chemin relatif (`/api/v1/auth/...`), jamais d'URL absolue ni de configuration CORS :

- **Local (`npm run dev`)** : le proxy Vite (`vite.config.ts`) relaie `/api` vers `http://localhost:8080` (le monolithe `api` en local).
- **Local (`docker compose up`)** : nginx (`nginx.conf`) relaie `/api/v1` vers le conteneur `api`.
- **Production (K3s)** : l'Ingress route `/api/v1` directement vers le service `api`, sans passer par ce pod (voir `infrastructure/kubernetes/ingress/`).

Le token JWT est stocké dans `localStorage` (`sporya.accessToken`) — voir `src/lib/auth-context.tsx`.

## Lancer en local

```bash
npm install
npm run dev
```

Nécessite `api` démarré (`docker compose up postgres api` à la racine, ou `./mvnw spring-boot:run` dans `backend/api/`).

## Vérifier

```bash
npm run lint
npm run build
```
