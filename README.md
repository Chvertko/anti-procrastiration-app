# FocusStep Android MVP

FocusStep creates a short pause before selected apps. It shows an original JavaScript, TypeScript, or web question. A user can answer it or wait 2–5 minutes, then choose to continue. The app works offline with a bundled JSON bank of 124 questions and can replace it with a validated JSON file hosted at an HTTPS URL.

This is a personal testing prototype. It uses an Android AccessibilityService to observe window changes for packages selected by the user. Google Play restricts this API; sideloading for personal testing does not establish Play Store eligibility. The service does not retrieve window content, text, screenshots, or input. Android lets the device owner disable the service or uninstall the app.

## Build a debug APK

Requirements:

- Android Studio with Android SDK Platform 36 and Build Tools 36;
- JDK 17 (Android Studio's bundled JBR 17 is sufficient);
- internet access for the first Gradle dependency download.

If building from PowerShell and Android Studio is installed in the standard location, set its JBR for the current terminal before running Gradle:

```powershell
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
```

Steps:

1. Open this project folder in Android Studio.
2. Let Gradle sync finish. If prompted, install Android SDK Platform 36 and Build Tools 36.
3. Select **Build → Build Bundle(s) / APK(s) → Build APK(s)**.
4. Android Studio writes the debug APK to `app/build/outputs/apk/debug/app-debug.apk`.

Alternatively, in PowerShell from the project folder:

```powershell
$env:ANDROID_HOME = (Join-Path $PWD ".android-sdk")
$env:ANDROID_SDK_ROOT = $env:ANDROID_HOME
.\gradlew.bat :app:assembleDebug
```

The APK is produced at `app\build\outputs\apk\debug\app-debug.apk`.

## Install on a phone

### USB / ADB

1. On the phone, enable Developer options and USB debugging.
2. Connect it to the computer and accept the phone's debugging prompt.
3. Install Android Platform Tools if `adb` is not available.
4. From the project folder run:

```powershell
adb devices
adb install -r app\build\outputs\apk\debug\app-debug.apk
```

### Copy the APK to the phone

Copy `app-debug.apk` to the phone, open it from Files, and allow installation from that file manager if Android asks. This is a debug build for your own device; do not redistribute it.

## First-run setup

1. Open **FocusStep**.
2. Open **Настройки** and choose the apps to monitor (for example, Instagram).
3. Return to **Сегодня**, set a PIN (you can ask a trusted person to choose it), then tap **Включить защиту**.
4. Review the disclosure and tap **Понятно, открыть настройки**.
5. In Android's Accessibility settings, enable **FocusStep: пауза перед приложениями** and confirm the system warning.
6. Open a selected app. The question gate should appear. Answer correctly, or choose the wait timer; after the timer, tap the continue button.
7. To stop monitoring, switch off FocusStep in Android's Accessibility settings.

The optional PIN protects edits made inside FocusStep. It cannot prevent a device owner from disabling the Android service, clearing app data, or uninstalling the app.

## Import a question pack

Paste an HTTPS URL in **Настройки → Импорт вопросов из JSON по HTTPS** and tap **Обновить каталог**. The URL must serve UTF-8 JSON in this shape:

```json
{
  "schemaVersion": 1,
  "questions": [
    {
      "id": "javascript.example.001",
      "category": "javascript",
      "prompt": "What does typeof null return?",
      "options": ["null", "object", "undefined"],
      "answerIndex": 1,
      "explanation": "This is a historical JavaScript behavior."
    }
  ]
}
```

Supported categories: `javascript`, `typescript`, `web`. A pack must contain 1–500 questions, unique IDs, 2–6 options, and a valid answer index. Downloads are limited to 1 MB, HTTPS only, and stored in the app's private files directory. Import is all-or-nothing; invalid data does not replace the current bank. The built-in catalog remains in `app/src/main/assets/questions.json`; `tools/make_question_bank.py` regenerates it from the original question list.

## Current MVP boundaries

- Question, answer, timer, and outcome data are local.
- The daily view reports completed attempts, completed questions, completed waits, and time spent in FocusStep. It does not claim to measure screen time saved in other apps.
- The timer continues against device wall-clock time if the gate screen is closed; reopening the selected app returns to the current wait state.
- The MVP has no code execution, account, server, ads, or cloud analytics.
- Accessibility API behavior varies across devices and Android versions. Background activity launches may be restricted; if a phone blocks the gate, use the prototype results to revise the interception approach.

## Repository notes

The GitHub Actions workflow builds a debug APK on pushes and manual runs, then publishes it as the `focusstep-debug-apk` workflow artifact. Google Play publication is not configured.
# anti-procrastiration-app
