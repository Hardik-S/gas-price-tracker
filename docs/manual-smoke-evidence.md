# Manual Smoke Evidence Template

Run date: 2026-05-11

## Purpose

This template turns a physical-device Gas Price Tracker demo into evidence a
recruiter can inspect. Unit tests prove parsing and cooldown logic; this file
documents the manual checks that require Google Play Services, permissions, and
device sensors.

## Evidence To Capture

| Check | Required evidence | Why it matters |
| --- | --- | --- |
| Permission sequence | Screenshot of foreground location and activity recognition grants. | Shows monitoring is permission-gated. |
| Monitoring state | Screenshot of the dashboard after monitoring starts. | Shows the state machine is visible. |
| Station prompt | Notification or capture-screen screenshot after a geofence event. | Shows station detection routes to explicit capture. |
| Price capture | Screenshot after entering a cents-per-litre value. | Shows Canadian price normalization path. |
| Local history | Screenshot of the saved observation list. | Shows local-first persistence without backend claims. |

## Claims Boundary

- Do not claim Activity Recognition fired unless tested on a physical device.
- Do not claim live Places lookup unless a restricted API key was used.
- Do not record or upload API keys, addresses, or precise home locations.
- No background microphone capture is part of this prototype.

## Reviewer Notes

Attach this file or its completed copy beside screenshots. If a run used only an
emulator, mark Activity Recognition as unverified and use the manual monitoring
button as the tested path.
