# Tests bout en bout

Tests cross-services (parcours utilisateur complets), distincts des tests unitaires/intégration qui vivent dans chaque `services/<nom>-service/src/test/`.

Aucun test ici pour l'instant — le premier parcours e2e viable nécessite au moins Auth + Club + Match Service (V1) :

```text
Login → Créer club → Créer équipe → Ajouter joueurs → Créer match → Ajouter événements → Consulter les statistiques
```

Outillage à définir au moment de la Phase 9 (tests), une fois qu'il y a un parcours réel à tester.
