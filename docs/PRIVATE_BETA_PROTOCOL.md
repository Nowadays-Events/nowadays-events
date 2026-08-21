# Protocole de bêta privée Xymis Events

## Parc d’essai minimal

- un téléphone Android 8 à 10 avec 3–4 Go de RAM ;
- un téléphone Android 11 à 13 de milieu de gamme ;
- un téléphone Android 14 à 16 récent ;
- au moins un écran compact et un grand écran ;
- portrait obligatoire, paysage utile pour détecter les défauts de reprise.

Noter pour chaque essai : modèle, version Android, version Xymis, date et type de réseau.

## Scénarios

1. Ouvrir la carte à froid et vérifier le retour à la dernière zone consultée.
2. Dézoomer jusqu’aux bulles, ouvrir une bulle, puis dézoomer : le même groupe doit se reformer.
3. Toucher un événement simple : la fiche s’ouvre sans zoom de caméra.
4. Ouvrir un événement principal, consulter successivement deux enfants, puis revenir aux
   événements principaux. Aucun enfant ne doit être dupliqué ou ouvrir une autre fiche.
5. Tester Aujourd’hui, Demain, 7 jours, Week-end, Toutes les dates et une période personnalisée.
6. Vérifier un événement récurrent, un événement annulé et plusieurs événements au même lieu.
7. Couper le réseau après une synchronisation : les événements restent visibles. Rétablir le
   réseau et vérifier l’heure de mise à jour sans perte brutale du flux.
8. Faire pivoter l’appareil, mettre l’application en arrière-plan cinq minutes, puis revenir.
   Le filtre et une position de carte raisonnables doivent être conservés.
9. Parcourir Mont-de-Marsan, Dax, Mimizan et Biscarrosse. Relever les événements manquants,
   doublons, coordonnées imprécises, dates erronées et annulations absentes.

## Fluidité et mémoire

Les jeux JVM de 150, 500 et 2 000 événements vérifient uniquement la correction fonctionnelle,
pas les performances. Pour mesurer : utiliser une APK `profileable`, Android Studio Profiler et
Macrobenchmark dans une tranche dédiée. Mesurer lancement à froid, mémoire stabilisée, temps de
réponse au zoom et images lentes sur les trois tailles, après trois répétitions par appareil.

## Compte rendu

```text
Appareil / Android / version Xymis :
Date, réseau et zone :
Scénario :
Résultat attendu :
Résultat observé :
Fréquence : toujours / parfois / une fois
Capture ou vidéo :
Événement ou URL concerné :
```

## Critères de sortie

- aucune disparition durable, mauvaise fiche ou duplication bloquante sur les scénarios carte ;
- aucune perte du cache après une panne réseau ;
- aucun plantage sur le parc minimal pendant sept jours ;
- sources attendues non vides surveillées, sans alerte sur un seul vide isolé ;
- tests Python, JVM, compilation Android et `git diff --check` verts ;
- tests instrumentés exécutés sur au moins deux versions Android ;
- anomalies de données critiques documentées et attribuées à une source ou au déduplicateur.
