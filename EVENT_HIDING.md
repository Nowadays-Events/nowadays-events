# Masquage réversible local

## Identité et stockage

La clé est `id:<Event.id>`, jamais le titre, la position, l'URL ou la date affichée.
Pour le flux public, `ApiEventSource` transforme `external_id` en `api-<external_id>`.
Les événements créés sur l'appareil ont un UUID conservé dans Room. La fusion
canonique existante conserve l'identifiant de l'événement courant ; elle n'est pas
modifiée par ce lot. Les changements de titre, statut, horaire ou prochaine
occurrence n'effacent pas le choix tant que cet identifiant est conservé.

Si le producteur change réellement l'identifiant distant ou si un événement local
est supprimé puis recréé avec un autre UUID, il s'agit d'une nouvelle identité :
aucune propagation incertaine du masquage par URL/titre n'est effectuée.

`EventHidingPreferences`, singleton applicatif, stocke les clés dans le fichier
SharedPreferences `event_hiding`, champ `hidden_keys`. Le store publie un StateFlow
commun aux fiches et au ViewModel carte/liste. Aucun champ Room ni migration,
suppression, modification de synchronisation ou modification du flux public.
Les préférences de repli des lignes restent séparées dans `event_list`.

## Présentation

Le ViewModel partitionne les résultats filtrés en visibles/masqués avant le
regroupement familial. Carte, sélection, listes et résultats de proximité ne
consomment que la partie visible. Masquer un principal ne masque pas ses enfants.
Le compteur de la liste respecte aussi son rayon et son lieu de référence.

La gestion propose le filtre actif et tous les masqués actuellement disponibles
dans le catalogue local, ainsi que « Tout réafficher ». Si tous les résultats
du filtre sont masqués, l'état vide le dit explicitement et donne un accès direct.
Les fiches quittent leur affichage lors du masquage, sans supprimer la donnée.

## Absence du flux

La politique actuelle de synchronisation cache un événement automatique après
deux instantanés réussis où il manque ; sa ligne Room est conservée.
La préférence de masquage n'est pas nettoyée lors de cette absence. Si le même
identifiant revient, il demeure masqué. S'il ne revient jamais, seule sa clé est
conservée : aucun rendez-vous disparu n'est réintroduit dans les résultats.
La gestion explique ces choix retenus pour des événements absents et « Tout
réafficher » permet de les effacer aussi. Pas d'expiration automatique implicite.
Effacer les données de l'application efface les préférences locales.

## Validation

Tests JVM avec horloge fixe et stockage injecté ; test du vrai synchroniseur avec
source et repository déterministes. Tests instrumentés sans MapLibre pour les
préférences Android, les actions ligne/fiche, la restauration et les instantanés
Room. Leur exécution sur appareil reste distincte de leur simple compilation.
