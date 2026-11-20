# Build

## Prerequisites

- JDK 17 (Android Studio's bundled JBR is suitable).
- Android SDK Platform 36 and Build Tools 36.
- Gradle wrapper in this repository; CI currently uses Gradle 8.13.
- Internet access for the initial dependency resolution.

## Local debug build (PowerShell)

If using Android Studio's standard installation:

```powershell
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
$env:ANDROID_HOME = (Join-Path $PWD ".android-sdk")
$env:ANDROID_SDK_ROOT = $env:ANDROID_HOME
```

Build with the repository wrapper:

```powershell
.\gradlew.bat :app:assembleDebug
```

The APK is `app\build\outputs\apk\debug\app-debug.apk`. The debug variant uses application ID `com.chvertko.focusstep.debug` and label **FocusStep Debug**, so it can coexist with a signed beta. Android Studio can also sync the project and run **Build → Build Bundle(s) / APK(s) → Build APK(s)**.

## Configuration and secrets

The app builds without Firebase configuration. To enable optional Firebase integrations locally, place the Firebase Android config at `app/google-services.json`; this file must remain untracked. CI accepts its contents through the `FIREBASE_ANDROID_CONFIG_JSON` secret. Never commit signing keys, credentials, local SDK paths, or user data.

Release signing is configured through environment variables consumed by `app/build.gradle.kts`: `BETA_KEYSTORE_PATH`, `BETA_KEYSTORE_PASSWORD`, `BETA_KEY_ALIAS`, and `BETA_KEY_PASSWORD`. Keep the persistent beta keystore backed up securely and out of Git.

## CI build

`.github/workflows/android.yml` builds a debug APK on pushes to `main`/`master`, pull requests, and manual runs. It uploads `focusstep-debug-apk` for 30 days. A `beta-*` tag also starts the signed beta job, which requires the four signing secrets and creates a prerelease. See [DEPLOY.md](DEPLOY.md) before tagging.

## Build troubleshooting

- Confirm `java -version` reports JDK 17 and SDK 36 is installed.
- Review Gradle sync/build output before clearing caches; avoid deleting shared build data as a first response.
- If Firebase-related build configuration fails, verify that the local config is valid and corresponds to application ID `com.chvertko.focusstep`; a Firebase-free build should otherwise remain supported.
- Do not treat a successful compile as proof that AccessibilityService behavior works on a device.
