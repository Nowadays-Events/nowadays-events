# Signature Android permanente

Les APK distribués par GitHub doivent tous être signés avec la même clé. Sans cette
continuité, Android refuse une mise à jour installée par-dessus une version précédente.

## Secrets GitHub attendus

- `ANDROID_SIGNING_KEY_BASE64` : contenu Base64 du fichier `.jks` ;
- `ANDROID_SIGNING_STORE_PASSWORD` : mot de passe du coffre ;
- `ANDROID_SIGNING_KEY_ALIAS` : alias de la clé ;
- `ANDROID_SIGNING_KEY_PASSWORD` : mot de passe de la clé.

Le workflow `release-test-apk.yml` échoue volontairement si un secret manque. La clé
et les mots de passe ne doivent jamais être ajoutés au dépôt. Les extensions `.jks` et
`.keystore`, ainsi que `signing.properties`, sont ignorées par Git.

## Sauvegarde indispensable

Conserver le fichier `.jks`, son alias et ses mots de passe dans deux emplacements sûrs.
La perte de cette clé rend impossible la publication d’une mise à jour compatible avec
les installations existantes.
