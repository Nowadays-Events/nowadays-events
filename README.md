# Nowadays Events

MVP Android de découverte d’événements géolocalisés. Les étapes 2 à 7 sont présentes : socle Android, données locales, filtres, carte MapLibre clusterisée, fiche détaillée, affluence anonyme, formulaire manuel et synchronisation extensible.

## Chaîne de compilation

Une chaîne locale est installée dans `.toolchain` et ignorée par Git : Temurin JDK 17.0.19, Gradle 8.13, Android SDK 36, Build Tools 35/36 et Platform Tools. Android Studio reste recommandé pour lancer un émulateur ou un appareil Android 8.0 (API 26) minimum.

## Lancement

1. Ouvrir ce dossier dans Android Studio et lancer la configuration `app`, ou utiliser le Gradle Wrapper.
2. Vérifier que `local.properties` pointe vers un SDK Android 36 valide.
3. En ligne de commande, définir `JAVA_HOME` vers un JDK 17 puis exécuter `gradlew.bat testDebugUnitTest assembleDebug lintDebug`.

Le Gradle Wrapper 8.13 est inclus. La dernière certification produit 6 tests réussis, aucune erreur lint et l’APK `app/build/outputs/apk/debug/app-debug.apk`.

## Architecture

Le détail des décisions, du modèle de données, des filtres, du clustering et des coûts se trouve dans [ANALYSE_MVP.md](ANALYSE_MVP.md).
Le guide d’intégration d’une nouvelle source se trouve dans [docs/ADDING_EVENT_SOURCE.md](docs/ADDING_EVENT_SOURCE.md).

Packages préparés : `presentation`, `domain`, `data`, `local`, `remote`, `map` et `sync`. Ils seront peuplés progressivement sans créer de modules Gradle prématurés.

Au premier lancement, Room reçoit automatiquement 30 événements fictifs calculés relativement à la date courante. Les événements couvrent aujourd’hui, la semaine, le week-end et des dates futures, avec plusieurs points aux mêmes coordonnées.

## Confidentialité

La localisation n’est demandée qu’après action explicite. Les réponses « J’y vais » et « Peut-être » alimenteront uniquement des compteurs d’affluence agrégés : aucune liste nominative de répondants n’est exposée. Un compte restera facultatif. Room conserve la réponse de l’installation pour le mode hors ligne ; le partage entre appareils nécessitera un petit endpoint protégé contre les doublons et les abus.

## Cartographie

MapLibre Native est configuré comme moteur. Aucun fournisseur de tuiles ni secret n’est codé en dur. L’attribution et les conditions du fournisseur devront être respectées lors de l’implémentation de la carte.
Le style public de démonstration MapLibre est utilisé uniquement pour le développement. Il doit être remplacé par une configuration de fournisseur explicitement dimensionnée avant publication.
