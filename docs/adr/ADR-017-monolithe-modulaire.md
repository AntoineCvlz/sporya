# ADR-017 — Monolithe modulaire au lieu de microservices

## Statut

Accepté

## Contexte

[ADR-003](ADR-003-microservices-des-le-mvp.md) avait choisi des microservices dès le MVP, construits un par un. Le déploiement du tout premier service (Auth Service) a suffi à saturer le VPS de production (1.8Gi RAM total, voir `docs/deployment/README.md#dimensionnement-mémoire`) : un rolling deploy avec deux JVM en parallèle a fait passer la box en thrashing (swap, load average 40+, SSH quasi inutilisable). Un plafonnement JVM explicite (`-Xmx192m`, threads Tomcat limités) a stabilisé la situation, mais sans marge : chaque nouveau service (Club, Match, ...) reproduirait le même risque. Une upgrade RAM a été explicitement écartée pour l'instant. Continuer en microservices signifierait donc soit accepter ce risque à chaque nouveau service, soit revisiter la contrainte budgétaire — l'un ou l'autre à chaque incrément.

## Options envisagées

- **Continuer en microservices, un JVM par service** — cohérent avec ADR-003/004, mais chaque nouveau service ajoute ~200-400Mi de RAM sur un budget total de 1.8Gi déjà entamé par K3s (Traefik, cert-manager, coredns, metrics-server) et Postgres. Non soutenable au-delà d'un service ou deux sans upgrade.
- **Upgrade du VPS** — lève la contrainte sans changer l'architecture, mais l'utilisateur a explicitement décliné cette option pour l'instant.
- **Monolithe modulaire** — un seul déployable Spring Boot, les contextes bornés (Auth, Club, Match, ...) comme packages Java séparés plutôt que comme services réseau séparés. Une seule JVM à dimensionner et surveiller, quel que soit le nombre de modules métier. Coût : perte de l'isolation de déploiement/scaling indépendant par contexte, et un couplage de compilation (un module qui casse le build casse tout le déployable).

## Décision

Basculer vers un monolithe modulaire : un seul déployable Spring Boot (`backend/api`), avec un package Java par contexte borné (`com.sporya.auth`, `com.sporya.club`, `com.sporya.match`, ...). Chaque module garde la structure interne `controller/application/domain/infrastructure` établie par [ADR-004](ADR-004-service-de-reference.md) et son propre schéma PostgreSQL (`auth`, `club`, `match`, ...), sans accès direct à la persistence d'un autre module — uniquement des appels Java explicites entre modules, jamais de repository partagé ni de jointure cross-schéma. Un module ne redevient un service réseau séparé que si une raison technique réelle apparaît (charge, cycle de déploiement différent, runtime différent) — même discipline "pas d'abstraction prématurée" que le reste du projet, appliquée dans l'autre sens.

Conséquence directe sur l'asynchrone : [ADR-005](ADR-005-kafka.md) (Kafka pour découpler Match → Statistics/Notification *entre services séparés*) perd sa raison d'être — ces échanges deviennent des appels in-process (Spring `ApplicationEventPublisher`, `@Async` si un traitement doit être différé du thread de la requête). Kafka redevient une option seulement si un besoin réel de durabilité/rejeu ou de scaling externe apparaît plus tard.

AI Service (V4, [ADR-010](ADR-010-ai-service-python.md)) reste une exception assumée : runtime Python/FastAPI différent de la JVM, il restera un processus séparé appelé en REST quand il sera construit — ce n'est pas remis en cause par cette décision.

## Conséquences

- Une seule JVM à dimensionner/surveiller sur le VPS, quel que soit le nombre de modules métier — le tuning déjà fait pour Auth (`-Xmx`, threads Tomcat, probes K8s) profite à tous les modules suivants sans le répéter.
- Un seul Dockerfile, un seul pipeline CI, un seul jeu de manifestes K8s (Deployment/Service/Ingress) au lieu d'un jeu par service — le gabarit d'ADR-004 devient inutile en tant que tel (plus rien à copier-coller), remplacé par l'ajout d'un package dans le même module.
- Perte du déploiement/scaling indépendant par contexte métier — un bug ou un déploiement dans le module Match redéploie tout le monolithe, y compris Auth. Acceptable au stade actuel (solo, VPS unique, pas de besoin de scaling différencié).
- Discipline requise pour ne pas recréer un "monolithe spaghetti" : chaque module garde son schéma PostgreSQL propre et n'accède jamais directement aux tables d'un autre module — à revérifier à chaque nouveau module, comme la discipline "un contexte ne devient service que si justifié" l'était pour ADR-003.
- Migration future vers un ou plusieurs services séparés reste possible sans redesign du domaine (frontières de module déjà alignées sur les bounded contexts, schémas déjà séparés) — seulement un changement de déploiement, le jour où c'est réellement justifié.

Remplace [ADR-003](ADR-003-microservices-des-le-mvp.md), [ADR-004](ADR-004-service-de-reference.md) et [ADR-005](ADR-005-kafka.md).
