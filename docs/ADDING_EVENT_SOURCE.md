# Ajouter une source d’événements

1. Implémenter `EventSource` dans `data/remote` avec un nom stable.
2. Mapper les données externes vers `Event`, en conservant une URL source HTTP(S) officielle.
3. Valider dates, coordonnées, texte, prix et provenance avant de retourner les événements.
4. Déclarer l’implémentation dans Hilt avec `@IntoSet` afin que le synchroniseur la découvre sans modification de l’UI.
5. Ajouter des tests du mapping, des erreurs réseau, de la pagination et des doublons.

La source doit respecter ses conditions d’utilisation, robots.txt lorsqu’il s’applique, les licences, les quotas et les données personnelles. Ne jamais ajouter de clé au dépôt. Les erreurs d’une source ne doivent pas supprimer le cache Room existant.

Le déduplicateur applique, dans l’ordre : identifiant identique, URL canonique, empreinte `titre + lieu + date`, puis correspondance probable par titre, heure et distance. Une correspondance probable est ignorée pour éviter une fusion destructive et devra être soumise à une validation manuelle dans une évolution future.
