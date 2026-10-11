# OpenGymPR

Log your lifts. See your PRs. That's it.

- **Free.** No ads. No trackers, no analytics.
- **No account.** Your data stays on your phone. The Android app has **no internet permission**.
- **Open source** (MIT).

## Repository

```
android/   the app (Kotlin + Jetpack Compose, SQLite) — what ships on Google Play
web/       the first prototype (PWA, plain JS); same features and backup format
store/     Play listing: texts, privacy policy, feature graphic, screenshots
```

## Android

Requirements: Android Studio (bundled JDK 21) and Android SDK 36. minSdk 26 (Android 8.0), targetSdk 36.

```bash
cd android
./gradlew testDebugUnitTest      # PR logic unit tests
./gradlew assembleDebug          # app/build/outputs/apk/debug/
./gradlew bundleRelease          # Play upload: app/build/outputs/bundle/release/app-release.aab
```

Release signing reads `android/keystore.properties` + `upload-key.jks` (both git-ignored).
**Back them up** (password manager + an offline copy): without the upload key you cannot publish updates
(Google can reset it, but that takes days).

Dependencies, on purpose very few: Compose (BOM 2026.06.01), activity-compose 1.13.0, core-ktx 1.18.0, JUnit 4.13.2 for tests.
Storage is Android's own SQLite; charts are drawn with Compose Canvas; icons are inline vectors.

### Design notes

- Weights are stored in kg; lb is display only.
- PRs are computed from history (`data/Pr.kt`), never stored — deleting a set re-evaluates everything.
- e1RM uses the Epley formula and only counts sets of ≤ 12 reps.
- Backup format `v1` is identical to the web prototype, so data moves between them.
