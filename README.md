# Gas Price Tracker — Android

An Android app that automatically detects when you pass a gas station while driving and prompts you to log the displayed price via voice or manual entry.

---

## Features (V1)

- **Automatic driving detection** via Activity Recognition Transition API (IN_VEHICLE enter/exit)
- **Rolling geofence window** — registers geofences for nearby gas stations ahead of your route; no bulk preloading
- **Station detection** via Google Places Nearby Search (filtered to `gas_station` type)
- **High-priority notification** when a geofence triggers; tapping opens the price capture screen
- **Voice price capture** — tap a microphone button inside the app; speech recognizer parses spoken prices
- **Canadian gas price support** — handles cents-per-litre formats (e.g. "one sixty seven point nine" → 167.9¢/L)
- **Manual typed fallback** entry
- **Cooldown / spam control** — per-station and global cooldowns, session prompt cap
- **Local-first storage** via Room; no backend required
- **State machine** with visible status: Idle / Driving Detected / Monitoring Stations / Awaiting Price Input
- **Graceful permission degradation** — app works with partial permissions; clearly shows reduced capability

---

## Requirements

| Tool | Version |
|---|---|
| Android Studio | Hedgehog or newer |
| Kotlin | 2.0+ |
| Min SDK | 29 (Android 10) |
| Target SDK | 35 (Android 15) |
| Google Play Services | Required on device |

---

## Setup

### 1. Clone the repository

```bash
git clone <repo-url>
cd GasPriceTracker
```

### 2. Get a Google API key

You need a Google Cloud project with these APIs enabled:

- **Places API (New)** — for gas station nearby search
- **Maps SDK for Android** — required by the Places SDK
- **Geocoding API** — optional, for address resolution

Steps:
1. Go to [Google Cloud Console](https://console.cloud.google.com/)
2. Create a project (or use an existing one)
3. Enable: **Places API (New)**, **Maps SDK for Android**
4. Go to **Credentials → Create API Key**
5. Restrict the key to Android apps using your package name `com.gasprice` and your debug keystore SHA-1

### 3. Add your API key to `local.properties`

Open (or create) `local.properties` in the project root and add:

```
GOOGLE_API_KEY=AIzaSy...your-key-here...
sdk.dir=/Users/you/Library/Android/sdk
```

> ⚠️ Never commit `local.properties`. It is in `.gitignore`.

The key is injected via `manifestPlaceholders` and `BuildConfig`. Both the manifest `<meta-data>` tag (for Places SDK initialization) and `BuildConfig.GOOGLE_API_KEY` (for runtime checks) are populated from this single value.

### 4. Build and run

```bash
./gradlew assembleDebug
# or open in Android Studio and press Run
```

---

## Project Structure

```
app/src/main/kotlin/com/gasprice/
├── GasPriceApp.kt                  # Application class, Hilt entry point, notification channels
├── MainActivity.kt                 # Single activity, Compose NavGraph
├── di/
│   └── AppModules.kt               # Hilt modules: Database, Location, Repository
├── domain/
│   ├── model/Models.kt             # Domain models: GasPriceObservation, GasStation, MonitoringState…
│   └── usecase/
│       ├── GasPriceParsing.kt      # Price string parser (spoken and numeric)
│       └── CooldownAndRanking.kt   # CooldownTracker, StationRanker
├── data/
│   ├── local/
│   │   ├── GasPriceDatabase.kt     # Room database
│   │   ├── dao/                    # DAOs
│   │   └── entity/                 # Room entities + mappers
│   └── repository/
│       ├── GasPriceRepository.kt   # Interface
│       ├── GasPriceRepositoryImpl.kt
│       └── PlacesRepository.kt     # Places Nearby Search wrapper
├── service/
│   ├── MonitoringController.kt     # Core state machine: driving → geofences → prompts
│   ├── MonitoringForegroundService.kt # FGS (location type) for background monitoring
│   ├── GeofenceManager.kt          # Rolling geofence window management
│   ├── GeofenceBroadcastReceiver.kt
│   ├── ActivityRecognitionManager.kt
│   └── ActivityTransitionReceiver.kt
└── ui/
    ├── theme/Theme.kt
    ├── onboarding/OnboardingScreen.kt  # Sequential permission flow
    ├── dashboard/                      # Main status + start/stop
    ├── capture/                        # Voice + manual price entry
    ├── history/                        # Scrollable list of saved prices
    └── settings/                       # Sliders for detection parameters
```

---

## Permissions

| Permission | Why |
|---|---|
| `ACCESS_FINE_LOCATION` | Core — required for geofencing |
| `ACCESS_BACKGROUND_LOCATION` | Geofences must trigger when app is backgrounded |
| `ACTIVITY_RECOGNITION` | Detect enter/exit vehicle to gate monitoring |
| `POST_NOTIFICATIONS` | Station detected alerts |
| `RECORD_AUDIO` | Voice price entry — only after explicit user tap, never from background |
| `FOREGROUND_SERVICE` + `FOREGROUND_SERVICE_LOCATION` | FGS with location type (Android 14+ requirement) |

Background location is requested separately after fine location, with explicit rationale text explaining it is used only during driving monitoring.

---

## Testing on a Device vs. Emulator

### Real device (recommended)

Activity recognition and geofencing work best on physical devices with Google Play Services. All features should work as expected.

### Emulator limitations

| Feature | Emulator behaviour |
|---|---|
| Activity Recognition | **Does not work** — transitions never fire. Use the manual "Start Monitoring" button instead. |
| Geofence triggers | Works with simulated locations (see below). |
| GPS / Fused Location | Works via Extended Controls → Location |
| Voice recognition | Works if the emulator has a Google account signed in and network access |

### Simulating a location and geofence trigger

1. In Android Studio, open **Extended Controls** (the `...` button in the emulator toolbar)
2. Go to **Location** and set a coordinate near a known gas station
3. The app will query Places and register geofences
4. Move the simulated location inside the geofence radius to trigger the prompt

**Or** use ADB:
```bash
# Set location
adb emu geo fix -79.3832 43.6532

# Move into a geofence (adjust coords to match a registered station)
adb emu geo fix -79.3835 43.6535
```

### Simulating driving state (bypassing Activity Recognition)

Since the emulator cannot trigger Activity Recognition:
1. Open the app
2. Tap **Start Monitoring** on the dashboard — this manually triggers `MonitoringController.startMonitoringManually()`
3. The state machine advances to DRIVING_DETECTED and begins location polling

---

## Running Tests

For a recruiter-facing verification path that avoids device-only assumptions,
see [docs/recruiter-verification.md](docs/recruiter-verification.md).

```bash
# Unit tests (no device required)
./gradlew :app:test

# Instrumentation tests (device/emulator required)
./gradlew :app:connectedAndroidTest
```

Unit test coverage includes:
- `GasPriceParsingTest` — 13 cases: numeric formats, spoken words, Canadian pump prices, edge cases
- `CooldownRankingTest` — station/global/session cooldowns, ranking by distance + bearing + recency

---

## Known Limitations

1. **Activity Recognition on emulators** — does not fire; use manual toggle
2. **Background location prompt sequence** — Android requires fine location to be granted before you can even request background location. The onboarding flow requests them sequentially, but if the user denies fine location, the background location button is unreachable.
3. **Places API billing** — Nearby Search incurs API costs per call. The app queries only when the user has moved >200m while driving. Budget for ~100 calls/driving session in the worst case.
4. **Geofence cap** — Android caps geofences at 100 per app. The default `maxActiveGeofences = 20` keeps you well under this. If you lower the cap, ensure it stays below 100.
5. **Geofence accuracy in urban areas** — GPS accuracy in dense cities can be 30–50m. Setting `geofenceRadiusMeters` below 100m is not recommended.
6. **Voice recognition requires network** — Android's `SpeechRecognizer` sends audio to Google's servers by default. Offline recognition is not available in V1.
7. **Settings not persisted** — The Settings screen sliders are in-memory only in V1. DataStore wiring is marked as TODO.
8. **No export or sharing** — All data is local-only. No CSV export or cloud sync in V1.
9. **Canadian pricing assumption** — The normalizer treats values < 2.0 as dollars/litre and multiplies by 100. US users seeing sub-$2 prices displayed as dollars/gallon will get incorrect normalization. This is configurable via `MonitoringSettings` in a future version.

---

## Architecture Overview

See [ADR.md](ADR.md) for full architecture decisions.

Quick summary:
- **Activity Recognition Transition API** gates all monitoring — no battery drain when not driving
- **Geofencing** (not continuous polling) is the station detection mechanism
- **Rolling geofence window** — refreshed every 200m of movement, max 20 active geofences
- **Foreground Service** with `foregroundServiceType="location"` satisfies Android 14+ requirements
- **Voice capture is in-app only** — microphone is never accessed from a background service
- **Room + Flow** for reactive local data
- **Hilt** for DI throughout

---

## API Keys & Secrets Policy

- API key is read from `local.properties` only — never hardcoded
- `local.properties` is excluded from version control via `.gitignore`
- For CI/CD, inject `GOOGLE_API_KEY` as an environment variable and write it to `local.properties` before the build step:

```bash
echo "GOOGLE_API_KEY=$GOOGLE_API_KEY" >> local.properties
```
