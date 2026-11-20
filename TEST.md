# Test

## Testing approach

Test the user-visible behavior and platform integration changed by each patch. Prefer deterministic unit tests for parsing, validation, preference/state transitions, and calculations; add Android/instrumentation coverage for lifecycle and framework integration when practical. Do not rely on compilation alone for AccessibilityService or background-launch behavior.

## Checks

Run relevant checks for the change and record results in the PR/change summary:

```powershell
.\gradlew.bat :app:testDebugUnitTest
.\gradlew.bat :app:assembleDebug
```

If instrumentation tests exist or are added, run them on a documented emulator/device/API level. The current repository may not yet contain automated test sources; identify that gap instead of implying coverage.

## Manual smoke scenarios

- Fresh install, onboarding/settings, no monitored apps, and service disabled/enabled.
- Select and deselect apps; verify only selected targets trigger the gate.
- Correct and incorrect answers, answer completion, wait at the minimum and maximum configured durations, expiry, return, and repeated app switching.
- Close/reopen or background the gate while waiting; verify timer and continuation state are predictable.
- Revoke accessibility access, force-stop/restart, and reboot; verify clear recovery and no false claim that protection remains active.
- Import valid, malformed, oversized, wrong-schema, duplicate-ID, unsupported-category, and unreachable/non-HTTPS packs; verify failed imports do not replace the active bank.
- Inspect daily summary labels and ensure estimates are not presented as measured device usage.
- Verify diagnostics stay off by default, opt-in/out works, and no answer text, package names, question content, or PIN enters telemetry/logs.

## Device coverage

For interception or lifecycle changes, test representative Android versions and at least two device manufacturers when available. Include launch from launcher, notification/deep link where relevant, recent-app switching, screen off/on, battery restrictions, and permission revocation. Record device model, Android version, steps, expected result, actual result, and logs with sensitive data removed.

## Reporting failures

Provide reproduction steps, expected and actual behavior, app version/build, Android version/device, and sanitized logs. Separate a reproducible product defect from an OS/OEM limitation, and link the issue to its acceptance criteria.
