# Trame

Liseuse de mangas Android (Kotlin, Jetpack Compose, Material 3 Expressive) qui suit des séries et prévient dès qu'un chapitre sort. Pensée d'abord pour One Piece. Pas de backend, pas de compte : tout est local.

- Données : [MangaDex](https://mangadex.org) (API publique), [MangaUpdates](https://www.mangaupdates.com) en secours.
- Lecture officielle : [MANGA Plus](https://mangaplus.shueisha.co.jp) (liens fournis par MangaDex), dans une WebView intégrée. Aucun scraping.
- Fichiers locaux : CBZ/ZIP et dossiers d'images.

## Animations signature

- **Trame** : transitions d'écran, apparition des couvertures et chargements faits d'une trame de points (shader AGSL sur Android 13+, rendu Canvas en repli). En avant, l'écran se révèle depuis le doigt ; au retour (y compris le geste prédictif), il se dissout depuis le bouton retour.
- **Lignes de vitesse** : tirer-pour-actualiser, et case « NOUVEAU ! » plein écran quand un chapitre sort pendant que l'appli est ouverte.
- **Bulle** : badges « Nouveau » / non lus et notifications in-app en bulles de dialogue qui « pop » avec un ressort.
- Le réglage système « Supprimer les animations » est respecté partout.

## Publier une version

1. Modifier le fichier `VERSION` (ex. `0.2.0`).
2. Pousser sur `main` (ou fusionner une PR).

GitHub Actions lit `VERSION` :
- tag `vX.Y.Z` absent → build de l'APK signé, création du tag et d'une GitHub Release avec l'APK et le changelog (commits depuis le tag précédent) ;
- tag déjà présent → build de vérification uniquement.

`versionName` = contenu de `VERSION`, `versionCode` = `github.run_number`.
L'appli vérifie `releases/latest` au lancement (au plus toutes les 6 h) et propose la mise à jour.

## Étapes manuelles (une seule fois)

### 1. Keystore de signature

À garder précieusement : **toujours le même**, sinon les mises à jour ne s'installent plus par-dessus.

```sh
keytool -genkeypair -v -keystore trame.jks -alias trame \
  -keyalg RSA -keysize 4096 -validity 36500
base64 -w0 trame.jks > trame.jks.b64   # macOS : base64 -i trame.jks -o trame.jks.b64
```

### 2. Secrets GitHub

*Settings → Secrets and variables → Actions → New repository secret* :

| Secret | Valeur |
|---|---|
| `KEYSTORE_BASE64` | contenu de `trame.jks.b64` |
| `KEYSTORE_PASSWORD` | mot de passe du keystore |
| `KEY_ALIAS` | alias (ex. `trame`) |
| `KEY_PASSWORD` | mot de passe de la clé |

Ne jamais commiter le keystore (`*.jks`, `*.b64` et `keystore.properties` sont dans `.gitignore`).

### 3. Permissions du workflow

*Settings → Actions → General → Workflow permissions* : **Read and write permissions** (création des tags et releases).

### 4. Sur le téléphone

Au premier lancement, l'onboarding demande :
- les notifications ;
- l'exclusion de l'optimisation batterie (**obligatoire** : sans elle, MagicOS, MIUI, One UI… coupent la vérification des sorties ; voir [dontkillmyapp.com](https://dontkillmyapp.com)) ;
- l'installation d'APK (mises à jour in-app).

## Build local

Prérequis : JDK 17+, Android SDK (plateforme 37).

```sh
./gradlew :app:assembleDebug
./gradlew :app:testDebugUnitTest
```

Captures des effets signature (trame, lignes de vitesse, bulles, transitions), rendues par Robolectric + Roborazzi dans `app/build/screenshots/`, avec compilation du shader AGSL par le vrai compilateur SkSL :

```sh
./gradlew :app:testDebugUnitTest -Pscreenshots
```

Release signée en local : créer `keystore.properties` à la racine (non commité) :

```properties
storeFile=/chemin/vers/trame.jks
storePassword=...
keyAlias=trame
keyPassword=...
```
