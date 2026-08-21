# Supervision des sources

Chaque source de `agent/config.json` publie un rapport dans `health.json`.

## États

- `ok` : collecte exploitable ;
- `disabled` : connecteur volontairement désactivé, sans effet sur l’état global ;
- `warning` : intervention souhaitable (clé absente, panne transitoire, vides répétés ou baisse anormale) ;
- `error` : identifiants refusés, erreur durable ou résultat sous un minimum non nul.

Le champ `reason` précise la cause. Le workflow restaure le `health.json` précédent afin de
conserver `consecutive_empty_collections` et `last_nonzero_candidates`. Une première collecte
vide n’alerte pas. Le seuil par défaut est trois et se règle avec
`empty_warning_threshold`. Une source normalement vide garde `expect_events: false` ou omet
ce champ en l’absence de `min_candidates`.

Le flux d’événements précédent est restauré séparément : une alerte de source ne vide donc
pas le dernier flux valide.

## OpenAgenda

OpenAgenda est actuellement désactivé par `"enabled": false`. Pour le réactiver :

1. créer ou renouveler la clé chez OpenAgenda ;
2. enregistrer sa valeur dans le secret GitHub Actions `OPENAGENDA_API_KEY` ;
3. passer uniquement `enabled` à `true` dans `agent/config.json` ;
4. déclencher manuellement le workflow **Collect and publish events** et contrôler
   `source_reports` dans `health.json`.

Aucune clé ne doit être inscrite dans un fichier du dépôt.
