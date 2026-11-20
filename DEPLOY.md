# Deploy

## Distribution intent

This repository currently supports debug APK artifacts for personal testing and signed beta APK prereleases. Google Play publication is not configured. A successful sideload build does not establish Play policy eligibility, especially for Accessibility API use.

## Before a beta

- Confirm the change is reviewed and relevant checks in [TEST.md](TEST.md) are recorded.
- Verify application ID, version name/code, release notes, and tested device/API coverage.
- Review permission disclosures, privacy notice, telemetry opt-in behavior, and known platform limitations.
- Check that no secrets, local configuration, or personal data are included in the artifact or commit.
- Confirm the persistent beta signing key is available and backed up securely. Do not generate a different key for an update to an existing beta installation.

## Debug artifact

After a push or manual run, open GitHub Actions → **Android build** → the successful run and download `focusstep-debug-apk`. It is a debug build with application ID `com.chvertko.focusstep.debug` for personal testing, retained for 30 days; it installs alongside the signed beta and is not a production release.

## Signed beta release

Configure repository Actions secrets `BETA_KEYSTORE_BASE64`, `BETA_KEYSTORE_PASSWORD`, `BETA_KEY_ALIAS`, and `BETA_KEY_PASSWORD`, then push a versioned tag such as `beta-0.2.0`. The workflow builds a signed release APK and publishes a prerelease. Verify the workflow succeeded, inspect the release asset, install/upgrade it on a test device, and confirm the signature matches the previous beta. Treat public repository releases as public downloads; use private distribution if access should be limited.

## Rollback and incident response

For a faulty beta, stop directing testers to it, document affected versions and symptoms, and publish a corrected higher-version build signed with the same key. If data loss or privacy exposure is suspected, pause distribution, investigate logs/configuration without collecting unnecessary personal data, and update the privacy/incident record before resuming.
