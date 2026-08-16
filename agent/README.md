# Agent local Xymis Events

Ce prototype collecte les événements structurés `schema.org/Event`, limite les
résultats à 100 km de Mont-de-Marsan, fusionne les doublons, conserve toutes les
URL sources et détecte les statuts d’annulation publiés par les organisateurs.

Il n’écrit jamais directement dans la base du téléphone. Il produit
`output/events.json`, futur contrat entre le collecteur et l’API Xymis Events.

## Exécution

```powershell
.\agent\run-agent.ps1
```

Les données de travail sont dans `agent/data/agent.db`. Une source indisponible
est signalée sans supprimer ses événements précédemment connus.

Une exécution est plafonnée à trois minutes. Le fichier `agent.lock` empêche
deux collectes de fonctionner simultanément.

## Planification Windows

Après validation du flux et de ses conditions d’utilisation, une tâche
Windows pourra exécuter la commande toutes les heures. Il est préférable de
publier ensuite le JSON derrière une petite API HTTPS : un téléphone n’est pas
une cible fiable pour une injection ADB planifiée.

## Publication gratuite avec GitHub Pages

Le workflow `.github/workflows/publish-events.yml` exécute les tests et la
collecte à la minute 17 de chaque heure, puis publie :

- `/events.json` : flux destiné à l’application ;
- `/events` : alias compatible avec le prototype Android actuel ;
- `/health.json` : état statique du dernier déploiement réussi.

Le travail est limité à cinq minutes et la concurrence est désactivée afin que
deux collectes ne puissent pas se chevaucher. Le dépôt doit être public pour
rester gratuit avec GitHub Pages et les runners GitHub standard.

Pour la démonstration USB actuelle, le téléphone et le PC sont sur deux
sous-réseaux différents. La redirection suivante permet à l’application
d’atteindre l’API du PC via `http://127.0.0.1:8765` :

```powershell
adb reverse tcp:8765 tcp:8765
```

## Limites connues

- seules les pages fournissant du JSON-LD correctement géolocalisé sont
  importées automatiquement ;
- une disparition de page ne suffit pas à déclarer un événement annulé ;
- l’agent respecte les données déjà collectées et nécessite une validation
  humaine pour les correspondances ambiguës ;
- les conditions d’utilisation, quotas et règles d’exploration de chaque
source restent à valider avant un déploiement permanent.

## Fiches validées manuellement

Les pages protégées contre les robots et les événements repérés sur les réseaux
sociaux peuvent être ajoutés dans `curated_events` de `config.json`. Ils utilisent
le format `schema.org/Event`, passent par le même filtre de rayon et la même
déduplication que les sources automatiques. La source originale doit toujours
être conservée dans `url` et la date vérifiée avant publication.

Le fichier public `health.json` indique désormais `degraded` et détaille les
sources en échec, au lieu d’afficher systématiquement un état sain.

## Architecture des sources

Chaque source déclare maintenant son `type`, sa `priority` et son niveau de
confiance `trust`. Les sources officielles sont traitées avant les sources
secondaires lorsque la collecte approche de sa limite de temps.

Ordre d’intégration retenu :

1. offices de tourisme, collectivités et organisateurs officiels ;
2. flux structurés JSON-LD, API, RSS ou calendriers ICS ;
3. plateformes événementielles disposant d’un accès autorisé ;
4. publications sociales transformées en candidats, jamais publiées directement.

Les annonces sociales et autres informations incertaines vont dans
`candidate_events`. Le workflow les publie séparément dans `candidates.json`
avec l’état `pending` ou `incomplete`. Seul le déplacement explicite d’une fiche
validée vers `curated_events` permet sa publication dans l’application.

`health.json` contient également un bilan par source : priorité, confiance,
nombre de fiches trouvées, nombre retenu dans le rayon et nombre d’échecs.
Une source configurée dont les identifiants sont refusés ouvre également une
alerte administrateur et place l’état global en `attention` ; un connecteur
volontairement non configuré reste ignoré.
Les sources officielles à volume régulier déclarent aussi un seuil minimal de
candidats : une rupture de parsing est ainsi détectée même si le total général
du flux reste supérieur au seuil de publication.
Lorsqu'un même événement vient de plusieurs sources, le statut le plus prudent
est conservé (`annulé`, puis `reporté`, puis `actif`) indépendamment de l'ordre
dans lequel les sources répondent.
Le contrôle avant publication refuse également une récurrence active dont la
prochaine occurrence est absente, passée ou située après la fin annoncée.
Les liens de source sont limités aux URL web absolues `http`/`https` afin qu'une
fiche collectée ne puisse pas injecter un lien dangereux dans l'application.
Un second calcul géographique indépendant bloque aussi le déploiement si une
fiche sort du rayon de 100 km autour de Mont-de-Marsan, ce qui empêche notamment
le retour accidentel d'anciens événements de démonstration situés à Paris.
Le centre et le rayon de ce contrôle sont lus dans `config.json`, comme ceux du
collecteur : une future extension vers la côte ne nécessitera donc qu'un seul
changement de configuration.

## Extension future vers la côte landaise

Le rayon ne sera agrandi qu'après stabilisation des connecteurs et ajout de
sources officielles couvrant au minimum Dax, Mimizan et Biscarrosse. Cela évite
de donner une fausse impression de couverture avec de grandes zones presque
vides. L'état `attention` d'un connecteur prioritaire, notamment OpenAgenda,
indique que cette extension est encore prématurée.
`health.json` publie les zones couvertes, les zones manquantes et le booléen
`expansion_ready`. Lorsque celui-ci devient vrai, le workflow ouvre une seule
issue GitHub assignée à l'administrateur ; le rayon ne change jamais sans une
validation humaine explicite.

La source officielle de Mimizan est collectée en préparation de cette extension.
Elle est marquée comme couverture de Mimizan, mais ses événements restent soumis
au rayon public actuel de 100 km, extension vers la côte landaise validée.
L'agenda officiel du Grand Dax est lu depuis son tableau Tourinsoft embarqué ; il
reste soumis au même filtre géographique et compte comme couverture de Dax.

Avant chaque publication, le nouveau flux est comparé au précédent. Une chute
de plus de 50 % bloque le déploiement : l’application conserve ainsi son dernier
flux valide lorsqu’une collecte connaît une panne générale ou un défaut de parsing.
