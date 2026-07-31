# Analyse et architecture du MVP Android d’événements géolocalisés

Date de décision : 18 juillet 2026

## 1. Périmètre et hypothèses

Cette étape couvre uniquement l’analyse. Aucun projet Android ni code applicatif n’est généré.

- Le MVP cible Android uniquement, téléphone en priorité, avec une interface en français.
- La zone de démonstration initiale est une seule agglomération (Paris par défaut), mais aucune règle métier ne dépend de cette ville.
- L’application est utilisable sans compte. L’ajout manuel est local et activé par un indicateur de build ou un réglage administrateur simple ; il ne constitue pas une authentification sécurisée.
- La localisation est facultative et demandée seulement après une action explicite sur « Me localiser ».
- « Hors ligne » signifie que les événements déjà enregistrés restent consultables. Une carte complète hors ligne n’est pas promise dans le MVP.
- Les dates sont enregistrées comme instants UTC et affichées dans le fuseau du terminal. Les calculs des filtres utilisent un `ZoneId` explicite et une horloge injectable.
- Un événement est retenu si son intervalle `[début, fin]` chevauche la période filtrée. Un événement sans heure de fin utilisera initialement sa date de début comme fin.
- Les 30 événements de démonstration sont générés relativement à la date de premier lancement afin que les filtres restent testables dans le temps, et portent tous une mention visible « Événement fictif ».
- Le catalogue fonctionne sans compte. Les réponses alimentent uniquement deux compteurs d’affluence agrégés : `GOING` (« J’y vais ») et `MAYBE` (« Peut-être »). `NONE` signifie que l’installation n’a plus de réponse active.
- Un compte pourra être proposé plus tard aux volontaires (synchronisation multi-appareils, fonctions sociales), mais il n’est jamais requis pour consulter un événement ou contribuer aux compteurs.

## 2. Parcours utilisateur

1. Au premier lancement, l’application charge les données fictives dans Room puis ouvre la carte centrée sur la ville par défaut. Aucune permission n’est demandée.
2. Le filtre « Aujourd’hui » est sélectionné et les événements correspondants apparaissent sous forme de points ou de groupes.
3. L’utilisateur sélectionne « Ce week-end » ; la même carte est mise à jour sans navigation.
4. Un déplacement ou un zoom change la zone visible, sans recharger l’écran.
5. Un appui sur un groupe zoome jusqu’à son expansion ; un appui sur un point sélectionne l’événement.
6. Une bottom sheet affiche le détail. Elle peut être étendue pour lire la description.
   L’utilisateur peut aussi choisir « J’y vais » ou « Peut-être ». Les autres utilisateurs voient uniquement les totaux, jamais l’identité des répondants. Sélectionner de nouveau le choix permet de le retirer.
7. « Itinéraire » lance un intent `geo:` ; une application compatible est proposée, sans transmettre la position de l’utilisateur au serveur de l’application.
8. Le bouton d’ajout ouvre un écran de formulaire. Après validation, l’événement est enregistré localement et apparaît sur la carte.
9. Sans réseau, Room continue d’alimenter l’écran. Un bandeau non bloquant indique l’état hors ligne et propose une nouvelle tentative.

## 3. Structure d’écrans

- `MapRoute` : top app bar, chips temporelles, carte, bouton de recentrage, état de synchronisation et bottom sheet de détail.
- `EventFormRoute` : création et modification manuelles. C’est la seule navigation secondaire du MVP.

La fiche détaillée reste une bottom sheet sur la carte afin de préserver le contexte spatial. Les liens externes, le partage et l’itinéraire passent par des intents Android.

## 4. Choix techniques

### Socle Android

- Kotlin, Jetpack Compose et Material 3.
- Application mono-module `app` au départ. Les packages imposent les frontières ; des modules Gradle ne seront créés qu’en présence d’un besoin réel de temps de compilation ou de réutilisation.
- MVVM à flux unidirectionnel : l’UI envoie des actions au `MapViewModel`, observe un unique `StateFlow<MapUiState>` et ne connaît aucune source de données concrète.
- Coroutines et Flow pour Room, synchronisation et état UI.
- Navigation Compose pour les deux routes.
- Hilt pour construire la base, le repository, les sources, les cas d’usage, l’horloge et les dispatchers.
- Room comme source de vérité locale. Le réseau écrit dans Room ; l’UI ne combine pas directement cache et réseau.
- Kotlin Serialization plutôt que Gson pour le JSON de démonstration et les futurs DTO.
- WorkManager pour une synchronisation périodique différable, sous contrainte réseau. Aucune synchronisation permanente.

### Carte

MapLibre Native Android est retenu. Le SDK est actif, open source et fournit `GeoJsonSource` ainsi que le clustering de points côté moteur. La carte native sera intégrée à Compose via `AndroidView`, avec gestion explicite de son cycle de vie. Un wrapper Compose tiers n’est pas nécessaire au MVP et augmenterait le risque de compatibilité.

Les événements filtrés sont transformés en une `FeatureCollection` GeoJSON :

- source avec clustering activé ;
- couche cercle pour les groupes ;
- couche symbole contenant `point_count_abbreviated` ;
- couche point pour les événements isolés ;
- clic sur un groupe : calcul du zoom d’expansion et animation de caméra ;
- clic sur un point : récupération de l’identifiant puis sélection via le ViewModel.

Cette solution évite de créer un composable ou une vue par marqueur et reste adaptée à plusieurs milliers de points. Le filtrage métier reste effectué avant la conversion GeoJSON. Une optimisation par bounding box pourra être ajoutée au DAO si le volume dépasse quelques dizaines de milliers d’éléments.

Le style et l’URL des tuiles sont injectés par configuration. L’attribution OSM reste visible. Les serveurs communautaires `tile.openstreetmap.org` n’offrent ni SLA ni téléchargement hors ligne et interdisent le préchargement massif ; ils ne doivent pas être considérés comme l’infrastructure gratuite de production. Pour le développement, on utilise un style compatible avec les règles du fournisseur ; avant publication, on choisit un hébergeur de tuiles OSM avec quota adapté ou un hébergement propre.

Références vérifiées : [MapLibre Native](https://github.com/maplibre/maplibre-native), [GeoJSON Android](https://maplibre.org/maplibre-native/android/examples/geojson-guide/), [clustering GeoJSON](https://maplibre.org/maplibre-native/android/api/-map-libre%20-native%20-android/org.maplibre.android.style.sources/-geo-json-options/with-cluster.html), [politique de tuiles OSM](https://operations.osmfoundation.org/policies/tiles/).

### Réseau et géocodage

Retrofit avec OkHttp est retenu plutôt que Ktor : intégration Android simple, surface connue et suffisante pour des API HTTP JSON. Il ne sera ajouté que lors de l’implémentation de la source distante simulée ou d’une première API réelle.

Le géocodage automatique n’est pas requis pour afficher les données de démonstration. Dans le formulaire MVP, les coordonnées peuvent être saisies ou choisies sur la carte. Une interface `Geocoder` préparera une intégration ultérieure. Le Nominatim public ne doit pas être utilisé comme service intensif ou pour de l’autocomplétion ; toute intégration devra respecter sa politique, limiter les requêtes, mettre en cache et rester remplaçable.

## 5. Architecture proposée

Les dépendances vont vers le domaine, jamais vers la présentation :

```text
presentation -> domain <- data
                    ^       |-- local (Room, JSON)
                    |       |-- remote (EventSource, DTO)
                    |       `-- sync
                    `-- map (adaptateur GeoJSON côté présentation/infrastructure)
```

- `domain` : modèles et règles pures, sans Android ni Room.
- `data` : implémentation du repository, mappers et orchestration des sources.
- `local` : base Room, DAO, entités et amorçage JSON.
- `remote` : contrat des sources et implémentations réseau/simulées.
- `sync` : stratégie de synchronisation, dédoublonnage et worker.
- `map` : conversion des événements en features et façade autour de MapLibre.
- `presentation` : écrans, composants, ViewModels et états UI.

`EventRepository` expose des `Flow` provenant de Room et des opérations `upsert`, `delete` et `sync`. `SyncEventsUseCase` demande des événements aux `EventSource`, les normalise, valide, dédoublonne puis les écrit dans une transaction. Un échec distant ne supprime jamais le cache valide.

## 6. Structure de dossiers cible

```text
app/src/main/java/<package>/
  App.kt
  MainActivity.kt
  core/
    common/       Result, Clock, DispatcherProvider
    designsystem/ thème et composants partagés
  domain/
    model/        Event, Category, DataOrigin, Price
    repository/   EventRepository
    usecase/      ObserveEvents, FilterEvents, SaveEvent, SyncEvents
  data/
    repository/   OfflineFirstEventRepository
    mapper/
    local/        EventDatabase, EventDao, EventEntity, SeedLoader
    remote/       EventSource, RemoteEventDto, FakeRemoteEventSource
    sync/         EventSynchronizer, Deduplicator, SyncWorker
  map/
    EventGeoJsonMapper, MapController, MapConfig
  presentation/
    navigation/
    map/          MapScreen, MapViewModel, MapUiState, MapAction
    detail/       EventDetailSheet
    form/         EventFormScreen, EventFormViewModel, validation
  di/
app/src/main/assets/demo_events.json
app/src/test/
app/src/androidTest/
```

Les noms `local`, `remote`, `map` et `sync` sont conservés comme sous-couches de `data` ou adaptateurs, sans multiplier prématurément les modules Gradle.

## 7. Modèle de données

### Domaine `Event`

```text
id: String (UUID)
title: String
shortDescription: String
fullDescription: String?
category: EventCategory
startsAt: Instant
endsAt: Instant
venueName: String
address: String
latitude: Double
longitude: Double
sourceUrl: String
imageUrl: String?
organizer: String?
price: Price                  // Free ou Paid(amount?, currency?)
updatedAt: Instant
origin: DataOrigin            // MANUAL, AUTOMATIC, DEMO
sourceId: String?             // identifiant stable chez la source
sourceName: String?
confidenceScore: Double?
status: EventStatus           // ACTIVE, CANCELLED, ARCHIVED
isFictional: Boolean
```

Contraintes : titre non vide, `endsAt >= startsAt`, latitude `[-90, 90]`, longitude `[-180, 180]`, URL HTTP(S) valide, score `[0, 1]`, montant positif. `Instant` est utilisé dans le domaine ; Room stocke des millisecondes epoch via converters.

### Entité Room

Une table `events` dénormalisée suffit au MVP. Index : `starts_at`, `ends_at`, `(latitude, longitude)`, `(source_name, source_id)` unique lorsqu’ils existent, et `deduplication_key`. Une table `sync_metadata(source_name, last_success_at, last_attempt_at, error_message)` conserve l’état de synchronisation.

Une table locale `event_attendance(event_id, response, updated_at, sync_state)` conserve au plus une réponse par événement et par installation. Les événements embarquent les derniers compteurs connus `going_count` et `maybe_count`. Le repository expose `observeAttendance(eventId)`, `setAttendance(eventId, response)` et `clearAttendance(eventId)`.

Pour partager réellement les compteurs, un endpoint minimal accepte un identifiant d’installation pseudonyme et applique une contrainte unique `(event_id, installation_id)`. Il remplace atomiquement l’ancienne réponse et retourne uniquement les totaux. L’identifiant est aléatoire, révocable, non dérivé du matériel et n’est pas affiché. Limitation de débit et protection anti-abus sont nécessaires ; sans compte, le dispositif réduit les doublons ordinaires mais ne garantit pas un sondage infalsifiable.

Le DAO expose une requête de chevauchement :

```text
starts_at <= periodEnd AND ends_at >= periodStart AND status = ACTIVE
```

### Périodes temporelles

- Aujourd’hui : `[début du jour local, début du jour suivant)`.
- Cette semaine : `[lundi 00:00, lundi suivant 00:00)` selon ISO-8601.
- Ce week-end : `[vendredi 18:00, lundi 00:00)`. Si l’utilisateur consulte après le dimanche, il s’agit encore du week-end de la semaine courante, conformément au libellé.
- Toutes les dates : événements dont `endsAt >= now`.

Les intervalles de filtre sont semi-ouverts `[start, end)` afin d’éviter les doublons aux frontières. Un événement qui a commencé mais n’est pas terminé est considéré actif.

## 8. Synchronisation et détection des doublons

```text
EventSource.fetch(since?) -> RemoteEvent
  -> validation et normalisation
  -> déduplication
  -> transaction Room
  -> mise à jour SyncMetadata
```

`EventSource` porte `sourceName` et une fonction suspendue de récupération. Le synchroniseur ne connaît aucun protocole concret.

Ordre de rapprochement :

1. clé forte `(sourceName, sourceId)` ;
2. URL source canonique ;
3. empreinte normalisée `titre + lieu + jour local` ;
4. rapprochement prudent par titre normalisé, proximité temporelle et distance géographique.

Les règles 1 à 3 peuvent fusionner automatiquement. La règle 4 ne fusionne que si le score dépasse un seuil documenté ; sinon l’élément est conservé pour validation future. Les suppressions distantes deviennent `ARCHIVED`, jamais une suppression physique immédiate. L’historique complet des modifications est reporté après le MVP, mais les champs de provenance et de mise à jour sont présents dès le départ.

## 9. État UI et hors ligne

`MapUiState` contient : période sélectionnée, liste filtrée, événement sélectionné, état initial, état de synchronisation, dernière synchronisation, message non bloquant et disponibilité de la localisation. Room alimente toujours la carte ; la synchronisation est un effet indépendant.

États distingués :

- premier chargement sans cache ;
- contenu disponible et synchronisation en cours ;
- contenu en cache avec réseau indisponible ;
- cache vide et erreur ;
- contenu disponible avec dernière tentative échouée.

La connectivité réseau n’est qu’un indice. L’état hors ligne fiable vient de l’échec réel de la requête.

## 10. Autorisations, sécurité et confidentialité

- Permissions prévues : `INTERNET` et, uniquement pour le bouton de recentrage, localisation approximative/précise au choix de l’utilisateur. Pas de localisation en arrière-plan.
- Pas de demande de permission au lancement ; la carte fonctionne avec un centre par défaut.
- La dernière position de l’utilisateur n’est ni mise en base ni envoyée au backend.
- Les choix sont synchronisés après une action explicite. Seuls les compteurs agrégés sont exposés ; aucune liste de répondants n’existe dans l’API publique.
- Le cache local mémorise la réponse de l’installation pour permettre sa modification hors ligne. L’identifiant d’installation peut être réinitialisé et les journaux ne contiennent pas les réponses individuelles.
- Un compte reste facultatif. Si une future liaison de compte est activée, elle exige un consentement explicite et une migration documentée de la réponse anonyme.
- Toute donnée distante ou manuelle passe par les mêmes validateurs de domaine.
- Les intents externes sont vérifiés avant lancement et les URL sont limitées à HTTP(S).
- Aucune clé dans Git. Les éventuels jetons proviendront de `local.properties`, secrets Gradle ou CI, avec une valeur absente par défaut.
- La base locale ne contient pas de donnée personnelle nécessaire au MVP ; le chiffrement Room n’est donc pas imposé.

## 11. Dépendances prévues

Les versions exactes seront figées dans un catalogue `libs.versions.toml` à l’étape 2 après vérification avec la version stable d’Android Studio/AGP choisie.

- AndroidX Core KTX, Lifecycle Runtime Compose et ViewModel Compose.
- Compose BOM, Material 3, Navigation Compose.
- Room Runtime, Room KTX et compilateur KSP.
- Hilt Android, compilateur KSP et Hilt Navigation Compose.
- Kotlin Coroutines Android et test.
- Kotlinx Serialization JSON et immutable collections si le profilage le justifie.
- MapLibre Native Android SDK ; intégration directe avec `AndroidView`.
- WorkManager KTX et intégration Hilt.
- Retrofit, converter Kotlin Serialization et OkHttp lors de l’ajout HTTP.
- Tests : JUnit, Kotlin Coroutines Test, Turbine, Room testing, AndroidX Test, Compose UI Test et Hilt testing.

Aucune dépendance de clustering supplémentaire n’est nécessaire. Coil ne sera ajouté que lorsque des images distantes seront réellement affichées.

## 12. Stratégie de tests

- Tests unitaires purs avec `Clock` et `ZoneId` contrôlés : aujourd’hui, semaine, week-end, changement d’heure, chevauchements et exclusion des expirés.
- Tests de mappers domaine/Room dans les deux sens.
- Tests du déduplicateur pour les quatre niveaux de rapprochement.
- Tests repository avec Room en mémoire et source distante factice : cache observé, upsert, erreur réseau conservant le cache.
- Tests ViewModel avec dispatcher de test : changement de filtre, sélection/désélection, synchronisation et message d’erreur.
- Tests d’affluence : persistance offline-first, bascule entre « J’y vais » et « Peut-être », retrait, reprise de synchronisation, idempotence et mise à jour atomique des deux compteurs.
- Tests d’API : aucune identité de répondant dans les réponses, une seule réponse active par installation et limitation de débit.
- Test Compose instrumenté du parcours demandé. Pour le rendre stable, la vraie carte est derrière une petite façade et remplacée par un composant de test exposant un événement cliquable.
- Test d’intégration MapLibre séparé pour vérifier le mapping clic feature/identifiant ; il n’est pas mélangé au test métier principal.

## 13. Risques et compromis

| Risque | Décision ou réduction |
|---|---|
| Fournisseur de tuiles gratuit sans SLA | URL configurable, attribution, cache conforme, choix du fournisseur avant production |
| Cycle de vie d’une vue MapLibre dans Compose | Wrapper unique testé, intégration `AndroidView`, pas de wrapper tiers indispensable |
| Clustering et mises à jour coûteuses | Une seule source GeoJSON, couches natives, mises à jour dédupliquées, profilage à 1 000/10 000 points |
| Dates, fuseaux et heure d’été | `Instant`, `ZoneId`, `Clock` injectée, intervalles semi-ouverts et tests de DST |
| Faux doublons | Clés fortes prioritaires, fusion floue conservatrice, provenance conservée |
| Géocodage gratuit limité | Pas d’autocomplétion au MVP, interface remplaçable, cache et quotas futurs |
| Mode administrateur local contournable | Accepté pour une démo locale ; authentification serveur obligatoire avant contribution publique |
| Compteurs anonymes manipulables | Identifiant d’installation révocable, unicité serveur, rate limiting ; ne pas présenter les chiffres comme certifiés |
| Comptes élargissant fortement le MVP | Comptes facultatifs explicitement reportés après validation du cœur cartographique |
| Architecture trop ambitieuse | Mono-module, interfaces uniquement aux frontières volatiles, pas de backend ni microservices |
| Carte indisponible hors ligne | Les événements et détails restent disponibles ; les tuiles déjà mises en cache peuvent apparaître, sans garantie de carte complète |

## 14. Estimation qualitative des coûts

| Poste | MVP | Évolution probable |
|---|---|---|
| Développement | principal coût, moyen à élevé | tests de sources et qualité des données dominent |
| Hébergement | nul pour le prototype aux compteurs simulés | faible mais nécessaire pour partager les compteurs entre appareils |
| Cartographie | faible en développement | dépend du trafic et du fournisseur de tuiles ; prévoir un quota explicite |
| Stockage | négligeable sur appareil | faible pour un catalogue régional |
| Collecte | nulle/limitée au manuel | poste humain et technique potentiellement élevé |
| Géocodage | nul au départ | faible à moyen selon volume, cache et fournisseur |
| Maintenance | faible pour le client seul | augmente avec le nombre et l’instabilité des sources |

Le coût caché principal n’est pas Room ou le backend : c’est la fiabilité, la légalité et la maintenance de la collecte d’événements.

## 15. Plan de développement du MVP

1. Initialiser le projet, verrouiller la chaîne Gradle/JDK, le thème, la navigation et un écran de démarrage compilable.
2. Créer le domaine, Room, l’amorçage de 30 événements, les filtres et leurs tests.
3. Intégrer MapLibre, la source GeoJSON clusterisée et les interactions de caméra/sélection.
4. Construire les chips, la bottom sheet, les intents, les états cache/réseau et l’accessibilité.
5. Ajouter le formulaire local, ses validateurs et le mode administrateur de démonstration.
6. Ajouter `EventSource`, la source simulée, le synchroniseur, WorkManager, le dédoublonnage et la documentation d’extension.
7. Exécuter tests unitaires/instrumentés, profilage sur un jeu étendu, vérifications clair/sombre et lecteur d’écran, puis documenter le lancement.

Chaque étape doit se terminer par une compilation et les tests pertinents avant de passer à la suivante.

## 16. Décisions reportées

- fournisseur de tuiles de production et budget associé ;
- choix du backend de compteurs et, séparément, éventuel fournisseur d’identité facultatif ;
- API publiques réellement intégrées ;
- géocodage et recherche d’adresses ;
- téléchargement de cartes hors ligne ;
- validation humaine et historique complet ;
- pagination/spatialisation serveur à très grande échelle.

Ces reports sont volontaires : ils évitent d’engager des coûts ou une architecture serveur avant validation de l’usage du MVP.
