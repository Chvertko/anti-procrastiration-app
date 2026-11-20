# Maintain

## Issue handling

For each report, capture reproducible steps, expected and actual behavior, app build, Android version, device manufacturer/model, and sanitized logs. Do not ask reporters to provide PINs, answer text, private question packs, or unrelated app/package details. Prioritize crashes, unexpected access behavior, data loss, privacy issues, and broken core gate flow.

## Android and dependency upkeep

- Review Android SDK, Gradle, Android Gradle Plugin, Kotlin, Compose, and Firebase updates deliberately; pin versions and build a reviewable change.
- Re-run build and relevant tests after upgrades, then manually validate platform-sensitive behavior on representative devices.
- Track Android behavior and policy changes affecting AccessibilityService, background activity starts, package visibility, and target SDK requirements.
- Keep GitHub Actions dependencies and action versions under periodic review.

## Data, privacy, and security

- Preserve offline question access and validate imported content before atomic replacement.
- Keep diagnostics opt-in, minimize event fields, and review telemetry changes for accidental collection of sensitive data.
- Never commit Firebase config, signing credentials, PINs, imported private content, or diagnostic dumps containing personal data.
- Document data retention and deletion behavior when persistence or telemetry changes.

## Release and signing continuity

Keep the beta keystore and recovery backup in an access-controlled secure location. Limit access to repository secrets, rotate exposed credentials, and verify upgrades with the established signing identity. Keep release notes, known issues, and supported-device expectations current.

## Documentation upkeep

Update README installation/setup steps when commands, SDK requirements, artifact locations, or user flows change. Update [PLAN.md](PLAN.md), [DESIGN.md](DESIGN.md), [BUILD.md](BUILD.md), [TEST.md](TEST.md), or [DEPLOY.md](DEPLOY.md) alongside changes to their respective lifecycle stage. Keep `CONCEPT.md` for product intent and `TECHNICAL_SPEC.md` for detailed architecture and decisions; resolve conflicts between current behavior and proposals explicitly.
