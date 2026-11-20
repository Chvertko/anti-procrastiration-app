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
- On Android 10+ and target API 36, verify the gate is shown as an accessibility overlay above the selected app while FocusStep has no visible Activity; confirm switching to an unselected app removes the overlay.
- Correct and incorrect answers, answer completion, wait at the minimum and maximum configured durations, expiry, return, and repeated app switching.
- Close/reopen or background the gate while waiting; verify timer and continuation state are predictable.
- Revoke accessibility access, force-stop/restart, and reboot; verify clear recovery and no false claim that protection remains active.
- Import valid, malformed, oversized, wrong-schema, duplicate-ID, unsupported-category, and unreachable/non-HTTPS packs; verify failed imports do not replace the active bank.
- Inspect daily summary labels and ensure estimates are not presented as measured device usage.
- Verify the home-screen widget status on service connect/disconnect and check that its daily statistics refresh after gate attempts/outcomes.
- Verify settings opens with the selected-app list visible, selection changes appear in that list, and the pinned **Готово** button returns to Today.
- With protection active, verify the status card is green and **Отключить защиту** requires the local PIN plus a second confirmation, then clears selected packages, temporary grants, accessibility self-binding, and the status indicator.
- Start playback in a protected app before the gate; verify the gate silences it and dismissing the gate restores the previous music volume. Also verify volume is restored after process restart.
- Verify the in-app widget controls can request a launcher pin and toggle daily stats, and that the widget uses the app icon.
- After solving the gate, verify the configured temporary-access countdown appears in the ongoing notification and the gate returns at expiry while the selected app remains foreground.
- On Android 13+, verify the notification permission is requested during setup (or from Settings) and that denial does not prevent protection from running.
- Verify the enable flow deep-links to the app-specific Accessibility page where supported and falls back to the general settings page elsewhere.
- Verify gate action buttons remain above Android's system navigation area on gesture and three-button navigation modes.
- Verify diagnostics stay off by default, opt-in/out works, and no answer text, package names, question content, or PIN enters telemetry/logs.
- With protection requested, swipe FocusStep from Recents. Confirm its ongoing notification remains, open a selected app, and verify the gate still appears. Repeat after device reboot and app update; distinguish task dismissal from Android Task Manager stop/force-stop.
- Verify every readiness item can be resolved from the dashboard and the green **Защита активна** state appears only when all platform requirements and a fresh monitor heartbeat are present. On Xiaomi, verify autostart/battery/Recents instructions and the confirmation flow.

## Device coverage

For interception or lifecycle changes, test representative Android versions and at least two device manufacturers when available. Include launch from launcher, notification/deep link where relevant, recent-app switching, screen off/on, battery restrictions, and permission revocation. Record device model, Android version, steps, expected result, actual result, and logs with sensitive data removed.

## Reporting failures

Provide reproduction steps, expected and actual behavior, app version/build, Android version/device, and sanitized logs. Separate a reproducible product defect from an OS/OEM limitation, and link the issue to its acceptance criteria.

## Latest device run

Run date: 2026-11-20. Device: Xiaomi 23030RAC7Y, Android 15 (API 35); app targets API 36.

- Installed **FocusStep Debug** (`com.chvertko.focusstep.debug`, version code 20) beside the existing signed beta. The beta could not be updated by the local debug key, so its data and service were left intact.
- The widget provider is registered on-device. Manual launcher-widget placement was not included in this run.
- Crash buffer analysis found repeated FocusStep crashes (16:27–16:32): `SecurityException` starting the hidden Accessibility details settings activity due to missing `OPEN_ACCESSIBILITY_DETAILS_SETTINGS`. The old Compose `SavedStateRegistryOwner` exception at 15:45 predates its fix. The settings helper now catches the restricted intent and falls back to public Accessibility settings.
- ADB confirmed version 16 ran the Debug monitor as a foreground service with notification ID 4107 and special-use type on Android 15. No new crash-buffer entry appeared after version 17 was installed and opened.
- The user reported the settings **Готово** button was behind the navigation bar, no widget was visible, the red disable button had blue text, crashes continued, and the temporary unlock duration was unclear. The follow-up build adds navigation-bar insets, an in-app icon/statistics card, explicit white text on the danger action, a visible configurable 5–30 minute access timer (10-minute default), and timed gate reactivation plus countdown notification.
- ADB `exit-info` after version 20 shows `PACKAGE UPDATED`, not a crash; crash-buffer entries remain the previously diagnosed `OPEN_ACCESSIBILITY_DETAILS_SETTINGS` permission failures. UI, audio, notification-permission, and countdown checks for version 20 are pending manual confirmation on the phone.
- The user reported seeing a gate and wait countdown, but the original signed beta service was also enabled during that run. The debug preferences had no recorded wait state afterward, so the visual result cannot yet be attributed conclusively to the new overlay.
- The first overlay build exposed a missing Compose `SavedStateRegistryOwner`; the owner was added before the later device run.
- `:app:testDebugUnitTest` completed with `NO-SOURCE`; this repository currently has no unit-test sources.
- Repeat the gate/countdown run with the old beta service temporarily disabled, then test wait/resume/expiry across leaving and reopening the selected app.

### Background persistence/readiness follow-up (version code 21)

- Device logs showed the most recent prior process exit was Xiaomi `OneKeyClean` (`OTHER KILLS BY SYSTEM`), and the Accessibility service was then listed as crashed. This is distinct from a new app crash.
- Added `ProtectionStatusService`, an independently started sticky foreground service with `stopWithTask=false`, boot/app-update restart, and a persistent status/countdown notification. AccessibilityService no longer owns the foreground notification lifecycle.
- Added readiness checklist for app selection, PIN, Accessibility enablement/heartbeat, notification access, battery optimization, and Xiaomi background autostart/Recents lock confirmations. Green active state is now gated on every requirement. Added direct notification and battery settings actions and Xiaomi guidance.
- Added notification channel countdown/status text and status remains after the Activity task is removed by design. On Xiaomi, system/OEM clean-up may still kill the package; autostart and battery/Recents settings are required and no software can guarantee survival of a force-stop.
- `:app:testDebugUnitTest :app:assembleDebug` succeeded, but test task reported `NO-SOURCE`; no unit tests currently exist.
- Installed debug APK version code 21 on Xiaomi (`com.chvertko.focusstep.debug`). `dumpsys activity services` confirmed `ProtectionStatusService` is foreground (`foregroundId=4107`, `startRequested=true`); the process was running after install/launch. Recent logcat scan contained no `FATAL EXCEPTION` or FocusStepStatus error.
- Manual UI capture showed the readiness items and action buttons fit above the system navigation bar. The device's ADB shell blocks injected key/touch events (`INJECT_EVENTS` SecurityException), so recents swipe and actual target-app gate/audio/countdown expiry still need manual interaction on the phone. Notification was checked in service state only; countdown behavior after a real answer is not yet manually verified.
