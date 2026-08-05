# Ajouter une source d’événements

1. Implémenter `EventSource` dans `data/remote` avec un nom stable.
2. Mapper les données externes vers `Event`, en conservant une URL source HTTP(S) officielle.
3. Valider dates, coordonnées, texte, prix et provenance avant de retourner les événements.
4. Déclarer l’implémentation dans Hilt avec `@IntoSet` afin que le synchroniseur la découvre sans modification de l’UI.
5. Ajouter des tests du mapping, des erreurs réseau, de la pagination et des doublons.

La source doit respecter ses conditions d’utilisation, robots.txt lorsqu’il s’applique, les licences, les quotas et les données personnelles. Ne jamais ajouter de clé au dépôt. Les erreurs d’une source ne doivent pas supprimer le cache Room existant.

Le déduplicateur applique, dans l’ordre : identifiant identique, URL canonique, empreinte `titre + lieu + date`, puis correspondance probable par titre, heure et distance. Une correspondance probable est ignorée pour éviter une fusion destructive et devra être soumise à une validation manuelle dans une évolution future.

## Connecteurs avec authentification

Les connecteurs OpenAgenda, HelloAsso et Eventbrite sont exécutés uniquement côté
GitHub Actions. Les identifiants doivent être enregistrés dans les secrets du dépôt :

- `OPENAGENDA_API_KEY` : clé publique de consultation OpenAgenda ;
- `HELLOASSO_CLIENT_ID` et `HELLOASSO_CLIENT_SECRET` : client disposant du privilège
  `FormOpenDirectory` pour lire l’annuaire public ;
- `EVENTBRITE_PRIVATE_TOKEN` et `EVENTBRITE_ORGANIZATION_IDS` : jeton privé et liste
  d’identifiants d’organisations autorisées, séparés par des virgules.

Eventbrite ne fournit pas de recherche géographique générale de tous les événements
publics dans son API actuelle. Ce connecteur ne collecte donc que les organisations qui
ont accordé l’accès au jeton. Une source sans secret est signalée
`credentials_missing` et n’empêche pas les autres sources de fonctionner.
