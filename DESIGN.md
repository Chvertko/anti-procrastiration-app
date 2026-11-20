# Design

## Product principles

- Create a short pause and offer a useful next step; do not shame users or claim to make the device impossible to bypass.
- Keep the core experience local and useful offline.
- Clearly distinguish measured in-app actions from estimates of time saved.
- Explain sensitive Android access in context and let the device owner revoke it.
- Treat telemetry as optional: no analytics or crash collection before explicit user opt-in.

## Current architecture

The app is a single Android application (`com.chvertko.focusstep`) built with Kotlin, Jetpack Compose/Material 3, Gradle Kotlin DSL, and minSdk 26 / target and compile SDK 36. Current code is organized under `app/src/main/java/com/chvertko/focusstep`: UI activities, preferences, question data, monitoring service, and telemetry. The question bank is bundled at `app/src/main/assets/questions.json`; an imported pack is validated before replacing it.

`AppMonitorService` adapts Android AccessibilityService events to the gate experience. It renders the shared Compose gate in a `TYPE_ACCESSIBILITY_OVERLAY` window, so it does not rely on starting an Activity from the background. The overlay is removed when the user leaves the selected app or continues. Keep Android system integration at this boundary and keep product decisions (selected apps, gate state, wait/access window) explicit and independently understandable. The current codebase is small; do not add modules, layers, or libraries without a concrete need.

## Key user flow and states

1. User configures monitored apps and optional PIN in FocusStep.
2. User reviews the disclosure and explicitly enables the accessibility service in Android settings.
3. On a monitored app event, FocusStep presents a question gate.
4. The user answers, starts the configured wait (2–5 minutes), or leaves. A completed answer/wait grants the expected continuation for that attempt.
5. Local outcomes feed the daily summary. Imported question packs replace the active pack only after complete validation.

Represent and test transitions explicitly: service unavailable/disabled, no apps selected, gate shown, answer accepted/rejected, waiting, wait expired, access granted, app/activity lifecycle interruption, and invalid/unavailable imported pack. A closed gate must not silently lose a running timer.

## Platform and privacy constraints

- Accessibility access is user-controlled and revocable. Explain that the service notices selected-app window changes and places the gate over those apps. Do not obstruct system controls, conceal the service, inspect window text/content, or imply that sideloading proves Google Play eligibility.
- Foreground and background activity starts vary across Android versions and OEMs. Treat the gate mechanism as a platform risk and validate it on real devices before relying on it.
- Do not add package names, answer text, question content, PINs, or other sensitive details to analytics, crash reports, or logs. Aggregate monitored-app counts only when needed.
- Do not claim system-wide usage measurement. Label estimates with their calculation method.
- Keep the bundled bank usable without network. Remote imports must use HTTPS, size and schema limits, unique IDs, supported categories, and atomic replacement.

## UX and accessibility

Use calm, direct language and a clear next action. Keep touch targets usable, support system font scaling and TalkBack labels, and provide visible loading/error/empty states. The gate must explain why it appeared and how to return or continue. Permission disclosure should explain what is observed and what is not collected.

## Design decisions to revisit

Before expanding the interception mechanism, complete the feasibility and policy review described in [TECHNICAL_SPEC.md](TECHNICAL_SPEC.md). Record a go/no-go decision and supported-device limitations. Revisit architecture only when product scope or evidence requires it.
