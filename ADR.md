# Architecture Decision Record — Gas Price Tracker V1

## ADR-001: Geofencing + Activity Recognition as the Detection Strategy

### Context

The app needs to detect when a user is near a gas station while driving, without draining the battery or getting rejected by Google Play for excessive background activity.

Several approaches were considered:

| Approach | Problem |
|---|---|
| Continuous GPS polling in a background service | High battery drain; likely rejected by Play for unjustified background location; violates Android 14+ FGS type rules |
| WorkManager periodic queries | Minimum 15-minute interval; too coarse for real-time detection while driving |
| Bluetooth beacons at stations | Requires hardware infrastructure not available in V1 |
| Geofencing + Activity Recognition | System-managed, battery-efficient, compliant with Play policy |

### Decision

Use the **Activity Recognition Transition API** to gate monitoring. When the API fires an `IN_VEHICLE` enter event, a **Foreground Service** starts and begins querying location and registering **Geofences** around nearby gas stations using the Google Play Services `GeofencingClient`.

Geofences are managed as a **rolling window**: when the user moves >200m, old geofences are cleared and new ones are registered based on freshly queried Places results. The number of active geofences is capped at 20 (well under Android's 100-geofence limit).

### Consequences

**Good:**
- Battery impact is proportional to driving time, not clock time
- Geofencing is handled by the OS — the app does not need to be running constantly
- Activity Recognition automatically stops monitoring when the user exits the vehicle
- Compliant with Google Play background location policy (foreground service visible to user, correct FGS type declared)

**Bad / Tradeoffs:**
- Activity Recognition is unreliable on emulators — developers must use the manual "Start Monitoring" toggle for testing
- Geofence accuracy depends on GPS quality (typically 30–50m in urban areas)
- The 200m movement threshold may miss stations if the user drives very slowly or parks and re-enters

---

## ADR-002: Microphone Access Only After Explicit In-App User Interaction

### Context

The app needs to capture a spoken gas price. Voice capture could theoretically happen:
1. **Automatically when a geofence fires** (no user interaction)
2. **From a notification action** (background microphone access)
3. **After the user taps a button inside the app** (foreground, user-initiated)

### Decision

Microphone access happens **only** in option 3: the user must tap the microphone button inside the Price Capture screen. The app never starts audio recording from a background service, a broadcast receiver, or a notification action.

This is enforced architecturally:
- `GeofenceBroadcastReceiver.onReceive()` only posts a notification — it has no reference to audio APIs
- `MonitoringForegroundService` has no microphone logic
- `SpeechRecognizer` is created and destroyed inside `PriceCaptureViewModel`, which is scoped to the Price Capture screen lifecycle

### Consequences

**Good:**
- Compliant with Android policy — `RECORD_AUDIO` is only used from a visible foreground Activity
- Avoids any perception of covert microphone access
- `SpeechRecognizer` API requires a foreground context anyway; starting it from a background service causes errors on Android 12+

**Bad / Tradeoffs:**
- User must tap a button rather than having the app auto-prompt with TTS + auto-listen
- Some user convenience is sacrificed for compliance and privacy

---

## ADR-003: Local-First Storage with Room; Repository Interface for Future Sync

### Context

V1 has no backend. All data must persist locally. V2 may add cloud sync or crowdsourced pricing.

### Decision

All gas price observations are stored in a **Room** database. A `GasPriceRepository` interface abstracts the data layer so that:
- `GasPriceRepositoryImpl` currently delegates to Room DAOs only
- A future `SyncingGasPriceRepository` or `RemoteGasPriceRepository` can be substituted via Hilt without changing callers

Room's `Flow`-backed DAO queries drive reactive UI updates via `StateFlow` in ViewModels.

### Consequences

**Good:**
- No network dependency in V1
- Clean separation allows sync to be added without touching UI or domain code
- `Flow` + `StateFlow` eliminates manual refresh logic

**Bad / Tradeoffs:**
- `fallbackToDestructiveMigration()` is used in V1 (acceptable for early development; replace with proper migrations before wide release)

---

## ADR-004: Single Activity + Compose Navigation

### Context

The app needs to handle deep links from notifications (tapping "gas station detected" notification must open Price Capture directly) and normal navigation between screens.

### Decision

A **single Activity** (`MainActivity`) with **Jetpack Compose Navigation** handles all screens. Deep link from the station notification passes station data as `Intent` extras. `onNewIntent()` captures these when the activity is already running (singleTop launch mode).

`NavHost` routes: `onboarding`, `dashboard`, `history`, `settings`, `price_capture`.

### Consequences

**Good:**
- Simple to reason about — no fragment back stack complexity
- Compose navigation handles deep-link `NavGraph` start destinations cleanly
- `singleTop` + `onNewIntent` correctly handles the case where the user is already in the app when the notification fires

**Bad / Tradeoffs:**
- Station data is passed via Intent extras (strings/doubles) rather than a shared ViewModel — acceptable for V1 given the simple data shape

---

## Known Edge Cases and Limitations

| Edge case | Current behaviour | Future mitigation |
|---|---|---|
| User drives through a station without stopping | Dwell geofence won't fire (requires ~30s inside radius by default). Enter geofence may fire. | Tune `dwellDelaySeconds` in settings |
| Multiple stations in one geofence area | First non-cooldown station wins per trigger event | Rank by distance in trigger handler |
| App killed while monitoring | FGS keeps geofences alive at OS level; next trigger fires receiver even if app was killed | Receiver re-inflates via Hilt `@AndroidEntryPoint` |
| User revokes location permission mid-session | `GeofencingClient` will return errors; monitoring degrades silently | Add permission-check before each location poll; show warning in dashboard |
| Emulator: Activity Recognition never fires | Manual start button provided | Document in README |
| Canadian vs. US price normalization | Values < 2.0 treated as $/L, multiplied by 100 | Make normalization mode a setting |
| Places API quota exhausted | Returns empty list; no geofences registered; logs warning | Add exponential backoff; cache last known stations |
