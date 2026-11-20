# Plan

## Product and current scope

FocusStep is a personal Android prototype that adds a deliberate pause before opening user-selected apps. The current MVP shows a bundled JavaScript, TypeScript, or web question, then allows the user to continue after answering or waiting 2–5 minutes. Question data and outcomes are local. A validated question pack can optionally be imported from HTTPS. Firebase Analytics and Crashlytics are optional and must remain disabled until the user opts in.

The product supports reflection and habit change; it does not promise an unbreakable block or a precise measure of system-wide usage. See [README.md](README.md), [CONCEPT.md](CONCEPT.md), and [TECHNICAL_SPEC.md](TECHNICAL_SPEC.md) for the source of truth on current behavior and intended direction.

## Development stages

1. **Understand and triage:** reproduce the reported behavior, identify affected Android versions/devices, inspect related code and existing documentation, and record expected versus actual behavior. Do not assume the desired product behavior from implementation alone.
2. **Plan:** state the user problem, scope, non-goals, acceptance criteria, risks, data/privacy implications, and a small implementation sequence. Update this file when scope or product behavior changes.
3. **Design:** document user flow, state transitions, permission/disclosure implications, data ownership, failure states, accessibility, and any platform-specific decision. For material architecture choices, record alternatives and rationale in `TECHNICAL_SPEC.md` or an ADR.
4. **Build:** implement the smallest coherent change, keep platform APIs isolated, preserve offline behavior, and keep secrets and personal data out of source control and logs.
5. **Test:** run the checks listed in [TEST.md](TEST.md), with focused coverage for the changed behavior. Record device/API coverage and known gaps.
6. **Deploy:** use the checklist in [DEPLOY.md](DEPLOY.md); beta distribution is for personal testing unless distribution and policy requirements have been reviewed.
7. **Maintain:** monitor user-reported issues, dependencies, Android behavior, privacy choices, and release signing using [MAINTAIN.md](MAINTAIN.md).

## Current priorities

- Reproduce and fix reported app behavior before expanding scope.
- Verify the AccessibilityService interception flow across supported devices and Android versions; background activity launch restrictions and manufacturer behavior are known risks.
- Keep question-pack validation all-or-nothing and preserve the built-in question bank as an offline fallback.
- Make the limits of PIN, accessibility permission, estimates, and optional telemetry clear to users.

## Definition of ready

A change is ready to implement when its user-visible behavior and acceptance criteria are clear, relevant risks and data handling are understood, and a test approach exists. Platform-sensitive changes also need a device/API validation plan.

## Definition of done

A change is done when implementation matches acceptance criteria, relevant automated and manual checks pass (or gaps are recorded), user-facing disclosures/docs are updated where needed, and the branch is reviewable. A release is done only after the applicable [DEPLOY.md](DEPLOY.md) checks and artifact verification.
