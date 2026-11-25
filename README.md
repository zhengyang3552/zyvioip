# zyvioip

Android Kotlin / Jetpack Compose app for checking IP "purity" across multiple IP intelligence providers.

## Features

- Auto-detects the device public IP on startup; manual search accepts IPv4/IPv6 only.
- Aggregates multiple providers concurrently, normalizes their signals into a 0–100 purity score, and shows per-provider cards.
- Default active cards need no API key; other providers are available from the "add provider" list.
- Cards support retry (bypassing cache) and swipe-to-remove (config is preserved).
- Settings store API keys/contact email locally with DataStore; no hardcoded credentials.
- Result caching: provider responses 12h (configurable), local public IP 5–10min.

## Build

- Android Studio / JDK 17, Android SDK 34, Gradle 8.2 (wrapper included).
- Debug: `./gradlew assembleDebug`
- Tests: `./gradlew testDebugUnitTest`
- Release signing uses repository secrets via GitHub Actions (see [SIGNING.md](docs/SIGNING.md)).

## CI

`.github/workflows/ci.yml` runs unit tests, builds the debug APK, uploads artifacts, runs lint, and creates a signed release for `v*` tags.

CI setup mirrors the other ZY Android projects on this machine: `actions/checkout`, JDK 17 Temurin, Gradle wrapper with `gradle/actions/setup-gradle`, Android SDK components, `upload-artifact`, and `softprops/action-gh-release`.
