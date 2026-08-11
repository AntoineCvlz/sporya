# Architecture — Vue d'ensemble

> Statut : validé le 2026-08-11 (Phase 0). Ce document est vivant : il évolue avec le projet, contrairement aux ADR qui figent une décision à un instant donné.

## Le produit

Sporya est une plateforme d'analyse et de suivi des performances sportives, football en premier périmètre, conçue pour pouvoir évoluer vers le multi-sport sans que cette évolution future ne complique le MVP (pas d'abstraction "sport" prématurée).

Sa valeur ne vient pas de la gestion CRUD de clubs/joueurs/matchs (commodité), mais de trois couches empilées dessus :

| Couche | Ce qu'elle apporte | Pourquoi c'est difficile |
|---|---|---|
| Référentiel (clubs, équipes, joueurs, matchs) | Données de base fiables et cohérentes | Relations temporelles (un joueur change d'équipe entre saisons, un match a un statut qui évolue) |
| Événements de match | Source de vérité du score et de la timeline | Le score ne doit **jamais** être un champ modifiable directement : il se déduit des événements |
| Statistiques / Analytics | Agrégats fiables, comparaisons, tendances | Les calculs doivent être documentés et reproductibles — c'est ce qui rend l'IA (V4) digne de confiance |

## Personas

| Persona | Rôle | Besoin principal |
|---|---|---|
| Entraîneur / staff technique | Gère l'effectif, prépare les matchs | Voir la forme de ses joueurs, composer une équipe informée |
| Analyste performance | Staff data | Comparer joueurs et équipes, détecter des tendances |
| Joueur | Membre d'une équipe | Suivre sa propre progression |
| Admin de club | Gère le club dans l'outil | Créer équipes, gérer les droits d'accès du staff |
| Spectateur / passionné | Public | Suivre un match en direct, consulter des stats publiques |
| Super Admin plateforme | Exploitant | Monitoring, gestion multi-clubs, support |

Un même utilisateur humain peut cumuler plusieurs rôles (ex. joueur + analyste) — d'où un modèle RBAC **par rôle et par club**, pas par type de compte figé (voir [ADR-011](../adr/ADR-011-rbac-par-club.md)).

## Cas d'utilisation (résumé)

- **Identity** : inscription/connexion, rejoindre un club avec un rôle, gérer les permissions.
- **Club & Effectif** : créer club/équipe, gérer joueurs et staff.
- **Compétition & Match** : planifier, démarrer, suivre en direct (score, timeline), clôturer un match.
- **Statistiques & Analytics** : consulter, comparer, visualiser une tendance.
- **Notifications** : être notifié d'un événement clé.
- **IA (V4)** : poser une question en langage naturel, réponse sourcée sur des données réelles.
- **Import (V3)** : importer un jeu de données externe, validé et dédupliqué.

## Fonctionnalités MVP

1. Inscription / connexion (JWT)
2. Créer un club
3. Créer une équipe (rattachée à une saison)
4. Ajouter des joueurs à l'équipe
5. Créer un match entre deux équipes
6. Ajouter des événements de match (but, carton, remplacement)
7. Score calculé automatiquement à partir des événements
8. Statistiques basiques (buts, cartons, participation) — servies par un **endpoint dérivé du Match Service**, pas par un Statistics Service séparé au MVP
9. Dashboard équipe (matchs, forme récente)
10. Frontend responsive

Hors MVP explicitement : Kafka, WebSocket, Analytics avancée, Data Import, IA, notifications push, multi-sport.

## Règles métier principales

| Règle | Justification |
|---|---|
| Le score d'un match est **dérivé** des `MatchEvent` de type `GOAL_SCORED`, jamais un champ éditable | Source de vérité unique |
| Un joueur appartient à une équipe **pour une saison donnée** | Modélise les transferts sans réécrire l'historique |
| Un match a un statut strict : `SCHEDULED → LIVE → HALF_TIME → LIVE → FINISHED` | Empêche d'ajouter un événement hors séquence |
| Un événement ne peut être ajouté que si le match est `LIVE` (sauf `MATCH_FINISHED`) | Intégrité de la timeline |
| Un carton rouge invalide les futurs événements offensifs du joueur sur ce match | Cohérence vérifiable en test |
| Les statistiques agrégées sont **recalculées**, jamais saisies manuellement | Une seule source de vérité (les événements) |
| Un rôle est attribué **par club**, pas globalement | Un utilisateur peut avoir des rôles différents dans deux clubs |
| Seuls `ADMIN`/`COACH` d'un club créent un match ou ajoutent des événements pour ses équipes | Contrôle d'accès aligné métier |

## Bounded contexts

| Bounded Context | Responsabilité | Données possédées | Dépendances |
|---|---|---|---|
| Identity & Access | Comptes, authentification, rôles par club | `User`, `Role`, `ClubMembership` | Aucune (fondation) |
| Club & Roster | Clubs, équipes, joueurs, staff | `Club`, `Team`, `Player`, `StaffMember` | Identity |
| Competition & Match | Compétitions, saisons, calendrier, déroulé du match, score, événements | `Competition`, `Season`, `Match`, `MatchEvent` | Club & Roster |
| Statistics | Agrégation des statistiques match/joueur/équipe | `PlayerMatchStatistics`, `TeamMatchStatistics` | Competition & Match |
| Analytics | Tendances, comparaisons, scores de forme | Vues dérivées | Statistics |
| Notification | Diffusion d'événements importants | `Notification` | Competition & Match |
| Data Import | Ingestion externe, validation, déduplication | Jobs d'import | Club & Roster, Competition & Match |
| AI / Insights | Réponses en langage naturel sur données fiables | Aucune donnée propre | Statistics, Analytics |

Ces contextes sont la structure logique du domaine. Un contexte ne devient un **service déployable** que lorsque sa séparation est réellement justifiée — voir la table d'ordre de construction ci-dessous.

## Diagrammes C4

Voir [`context.md`](context.md) (niveau 1 — acteurs et systèmes externes) et [`containers.md`](containers.md) (niveau 2 — services, bases, flux).

## Ordre de construction des microservices

Décision : **microservices dès le V1, construits un par un**, jamais les huit en parallèle (voir [ADR-003](../adr/ADR-003-microservices-des-le-mvp.md)).

| # | Service | Construit en | Dépend de | Justification |
|---|---|---|---|---|
| 1 | Identity/Auth | V1, en premier | Aucune | Sert de gabarit (structure interne, Dockerfile, CI, K8s, observabilité) pour tous les suivants |
| 2 | Club Service | V1 | Auth (JWT validé localement) | Domaine CRUD stable, confirme le gabarit avant un domaine complexe |
| 3 | Match Service | V1 (référence de complexité métier) | Club (REST) | State machine, événements, score dérivé — premier producteur d'événements |
| 4 | Statistics Service | V2 | Match (événements) | N'a de sens qu'une fois qu'il y a un historique multi-matchs à agréger |
| 5 | Notification Service | V2 | Match (Kafka) | Devient un service propre avec plusieurs consommateurs et des préférences utilisateur |
| 6 | Analytics Service | V3 | Statistics (REST) | Frontière avec Statistics à réévaluer au moment venu (ADR à ce moment-là) |
| 7 | Data Import Service | V3 | Club, Match (cibles) | Cycle de vie très différent (batch vs synchrone) |
| 8 | AI Service | V4 | Statistics, Analytics (REST) | Runtime différent (Python/FastAPI), dépendances lourdes (LLM) |

Découplage : aucun appel réseau à Auth par requête — chaque service valide le JWT localement (voir [ADR-013](../adr/ADR-013-jwt-stateless.md)).

## Communication inter-services

| Flux | Mécanisme | Raison |
|---|---|---|
| Frontend → services | REST synchrone | Réponse immédiate attendue |
| Match → Statistics / Notification | Kafka | Traitement asynchrone tolérable, plusieurs consommateurs |
| Match live → Frontend | WebSocket | Mise à jour poussée, pas de polling |
| AI Service → Statistics/Analytics | REST synchrone interne | Réponse immédiate et fiable requise |
| Match → Club | REST synchrone | Validation de composition d'équipe |

## Modèle de données

Voir [`docs/database/`](../database/). Chaque service possède son propre schéma PostgreSQL dès sa création — pas de base partagée logiquement, même si les schémas cohabitent sur une seule instance au démarrage pour économiser les ressources du VPS.

## Roadmap résumée

| Version | Contenu |
|---|---|
| V1 — MVP | Auth, Club, Match Service ; React ; PostgreSQL ; Docker ; CI ; K3s ; premier déploiement |
| V2 | Statistics, Notification ; Redis ; Kafka ; WebSocket |
| V3 | Analytics, Data Import ; comparaisons, statistiques avancées |
| V4 | AI Service, assistant conversationnel |
| V5 | Industrialisation : observabilité complète, sécurité avancée, backups, optimisation, doc finale |

## Risques principaux

| Risque | Mitigation |
|---|---|
| Chantier d'infra répété à chaque nouveau service | Gabarit posé dès Auth Service, CI en reusable workflow, manifestes K8s copiés |
| Création d'un service pour un contexte pas encore prêt | Un service n'est construit que si sa raison d'être est réelle |
| Duplication de code cross-cutting entre services | Dupliquer d'abord (2-3 services), extraire une lib partagée seulement si ça devient douloureux |
| IA qui invente des statistiques | Accès uniquement via l'API Statistics/Analytics, jamais de génération libre de chiffres |
| Dépendance à une API sportive externe payante | Démarrer avec données mockées/CSV, interface d'import agnostique de la source |

Détail complet (sécurité, observabilité, infra, estimation par phase) : voir l'historique de conception dans `docs/adr/` et les documents dédiés par sujet.
