# Recruiter Verification Runbook

Run date: 2026-05-10

## Purpose

This file turns the Gas Price Tracker resume claim into a reviewable engineering
artifact. It explains what a reviewer can verify without needing a car, a live
Places billing account, or background Android sensors.

## Proof Points

- Kotlin Android app with a local-first Room data model.
- Activity Recognition and geofencing are documented as battery-saving gates.
- Microphone capture is user-initiated and scoped to the visible screen.
- Google Places usage is isolated behind `PlacesRepository`.
- Unit tests cover parser and cooldown/ranking behavior.
- Cooldown tests include the no-Places-ID fallback station-key path.

## Local Setup Boundary

`local.properties` must stay untracked. It may contain:

```properties
sdk.dir=C:\\Users\\<you>\\AppData\\Local\\Android\\Sdk
GOOGLE_API_KEY=<restricted development key>
```

The Google key is required for live Places lookup. Parser, cooldown, and most
architecture review can be completed without a production key.

## Smallest Honest Verification

From the repository root:

```powershell
.\\gradlew.bat :app:test
```

Expected result: Gradle runs local unit tests without an emulator. If the host
does not have the Android SDK configured, record that as an environment blocker
instead of treating it as an app failure.

For the automation-friendly path, run:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File scripts\verify-recruiter-proof.ps1
```

That script first confirms `local.properties` is not tracked, scans tracked text
files for committed Google API-key shapes, and then runs the local unit tests.
This preserves the recruiter-visible proof path without requiring a live Places
billing account or a device.

## Device Smoke Path

1. Install a debug build on a physical Android device.
2. Grant foreground location and activity recognition.
3. Start monitoring manually if Activity Recognition does not fire.
4. Use a nearby station or simulated coordinates to trigger capture.
5. Enter a Canadian cents-per-litre price and confirm local history updates.

## Decisions Preserved

The app intentionally avoids background microphone access. A geofence only posts
a notification; price capture starts after the user opens the app and taps the
microphone. This is less convenient than automatic listening, but it keeps the
prototype aligned with privacy expectations and Android policy.
