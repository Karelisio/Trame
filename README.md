# Trame

Liseuse de mangas Android (Kotlin, Jetpack Compose, Material 3 Expressive) qui suit des séries et prévient dès qu'un chapitre sort. Pas de backend, pas de compte : tout est local.

- Données : [MangaDex](https://mangadex.org) (API publique), [MangaUpdates](https://www.mangaupdates.com) en secours.
- Lecture officielle : MANGA Plus (liens fournis par MangaDex).

## Build local

Prérequis : JDK 17+, Android SDK (plateforme 37).

```sh
./gradlew :app:assembleDebug
```

La version de l'appli est lue depuis le fichier `VERSION`. Le `versionCode` vient de la variable d'environnement `VERSION_CODE` (1 par défaut).

> Les étapes de signature et de CI seront documentées à l'étape 8.
