# Switchback

Switchback is a fork of Grupetto for Peloton Bike+ that provides a lightweight overlay HUD, route riding foundation, GPX import, and experimental Bike+ resistance control.

> This project is experimental and unaffiliated with Peloton. It relies on undocumented internal interfaces and may break with OS updates.

- [How to install Switchback](#how-to-install-switchback)
- [How to import GPX files today](#how-to-import-gpx-files-today)
- [Bike+ resistance warnings](#bike-resistance-warnings)
- [What route simulation does and does not do](#what-route-simulation-does-and-does-not)
- [Current MVP status](#current-mvp-status)
- [Route import product spec](#route-import-product-spec)
- [Grade-to-resistance tuning proposal](#grade-to-resistance-tuning-proposal)
- [Stats schema review](#stats-schema-review)
- [Troubleshooting](#troubleshooting)

## How to install Switchback

Switchback lives in `grupetto/app` and builds with Gradle.

### Build a debug APK

From the `grupetto` folder:

```powershell
cd "c:\Peloton Project\grupetto"
.\gradlew.bat assembleDebug
```

The APK is written to:

```text
app/build/outputs/apk/debug/app-debug.apk
```

### Install with ADB

```powershell
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

If you see `INSTALL_FAILED_VERSION_DOWNGRADE`, uninstall the existing `com.spop.poverlay` first.

### Install on the Peloton Bike

- Enable sideloading / developer mode on the bike.
- Install the APK with `adb install`.
- Grant the overlay permission when prompted.

The installed package is still `com.spop.poverlay` internally.

### Notes

- The overlay permission is required for the app to function.
- If the overlay blocks the bike UI, use:

```powershell
adb shell am force-stop com.spop.poverlay
```

- This app is intended for Bike+ hardware only for resistance write support.

## How to import GPX files today

Switchback currently supports GPX import through the app and a local fallback folder.

### Preferred flow

1. Open Switchback.
2. Go to the `Routes` tab.
3. Tap `Import GPX`.
4. Select a valid GPX file.
5. The route appears in the saved route list.

### Fallback flow on Peloton

Many Peloton devices do not support the standard Android document picker.
If the picker is unavailable, the app falls back to scanning a local import folder.

1. Push GPX files via ADB:

```powershell
adb push "MyRoute.gpx" "/sdcard/Android/data/com.spop.poverlay/files/route_imports/"
```

2. Open Switchback.
3. Tap `Import GPX` again.

### Supported GPX content

- GPX `<trk>` track points
- GPX `<rte>` route points
- namespaced GPX files such as RideWithGPS
- missing elevation values are handled gracefully

### Import error states

- `Could not import GPX route` — generic failure.
- `Unable to parse GPX` — invalid or malformed GPX file.
- `No compatible document picker found` — use the local fallback folder.
- `Imported route "<name>"` — success.

### If import fails repeatedly

- Confirm the file has valid track or route points.
- Confirm the file extension is `.gpx`.
- If the bike does not support file selection, use the `route_imports` fallback path above.

## Bike+ resistance warnings

Switchback includes experimental Bike+ resistance control that is:

- bike model constrained to Bike+ (`Build.MODEL == "PLTN-TTR01"`)
- disabled by default in settings
- gated behind a user preference and a build flag
- clamped to `0..100`
- rate limited to avoid rapid writes
- limited to the known Binder transaction `7` for `setResistance(int)` only

### Important safety notes

- Do not use this feature on non-Bike+ hardware.
- This is not safety-certified product functionality.
- This is not a replacement for Peloton's official resistance control.
- The app does not perform motor calibration, homing, bootloader, serial control, or error injection.

## What route simulation does and does not do

### What it does today

- imports GPX routes into the app
- parses route metadata: distance, climb, max grade, average grade, elevation profile
- saves routes locally
- starts and resumes active route progress from recorded ride distance
- displays route progress in the overlay and route detail screens
- maps smoothed grade into resistance adjustments
- records `routePositionMeters` inside ride samples
- supports route start, clear, and resume semantics

### What it does not do

- it is not a full 3D virtual route world
- it does not model exact speed or power output from terrain alone
- it does not map GPS location in real time
- it does not replace a structured workout builder yet
- it does not provide companion app syncing or cloud route sharing yet

## Current MVP status

### Completed

- buildable Switchback app with overlay permission flow
- Bike+ telemetry reading and HUD overlay
- user settings and runtime persistence
- ride session recording engine
- saved ride history and summary view
- GPX import and route metadata parsing
- route start/resume and active route persistence
- experimental Bike+ resistance control UI

### Partial / in progress

- route resistance simulation
- route HUD layout and map overlay polish
- GPX import UX on locked-down Peloton devices
- route attempt semantics and resume handling
- companion/import alternatives

### Next

- improve user-facing route import flow
- add route preview and difficulty/effort estimates
- add manual override and automation suspend behavior for resistance
- finalize release packaging and application ID strategy
- add ride session export/share
- add companion app or inline upload flow

## Route import product spec

### Goal

Make GPX import easy and resilient on Peloton devices.

### First user-friendly flow

- user taps `Import GPX`
- if the device supports a picker, select a file
- if not, the app shows a fallback to the local import folder
- imported routes appear in the Routes list

### Error states and UI copy

- `GPX import failed: no compatible document picker available. Push GPX files to /sdcard/Android/data/com.spop.poverlay/files/route_imports/ and retry.`
- `GPX import failed: file could not be parsed. Check that the GPX file contains valid track or route points.`
- `GPX import failed: route data contained no points. Use a file with at least one track or route point.`
- `Imported route "<name>"` — success.

## GPX import pathways

| Path | Pros | Cons |
|---|---|---|
| Android document picker | Best user experience | may be unavailable on Peloton OS |
| Local web upload / browser | Works if browser supports file selection | unreliable on locked-down bike |
| Companion app | Best long-term user flow | requires separate paired app |
| `adb push` to route_imports | Most reliable today | requires USB/ADB access |

### Recommended first path

- use the Android document picker when available
- otherwise use the `route_imports` fallback folder
- reserve companion app support for a later phase

## Grade-to-resistance tuning proposal

### Current formula

The current resistance mapping uses the route grade and a baseline resistance to compute a target value:

- uphill: `baseline + 1.4 * grade%`
- downhill: `baseline + 0.8 * grade%`
- limit each write to ±2 resistance points
- clamp to `0..100`
- rate limit writes to at least 5 seconds apart

### Conservative presets

- `Easy`: uphill 0.8×, downhill 0.4×
- `Normal`: uphill 1.2×, downhill 0.8×
- `Hard`: uphill 1.6×, downhill 1.0×

### Safety constraints

- never write outside `0..100`
- never jump more than 2 resistance points per update
- never write more often than every 5 seconds
- only apply when a route is active
- preserve manual user control and avoid overriding quick taps

### Example

If the baseline resistance is `30`:

- 3% uphill → normal target `34`
- 6% uphill → hard target `39`
- 2% downhill → easy target `29`

## Stats schema review

### Fields saved long-term

Each ride sample should persist:

- `timestampMs`
- `powerWatts`
- `cadenceRpm`
- `resistance`
- `speedMph`
- `distanceMiles`
- `heartRateBpm`
- `routePositionMeters`

### Session summary fields

- `id`
- `name`
- `startedAtMs`
- `completedAtMs`
- `durationMs`
- `sampleCount`
- `averagePowerWatts`
- `maxPowerWatts`
- `averageCadenceRpm`
- `distanceMiles`
- `averageHeartRateBpm`
- `maxHeartRateBpm`
- `totalWorkKilojoules`
- `estimatedCalories`

### Route attempt representation

- route attempts are stored as ride sessions with route position stamps
- active route ID and saved route position are persisted separately
- resumed route segments may be treated as a single attempt when the same route is continued
- in the current design, explicit restarts or clears create separate sessions unless merged later

### Long-term guidance

Keep raw samples and route position data long-term so summaries can be recomputed if formulas change.

## Troubleshooting

- `INSTALL_FAILED_VERSION_DOWNGRADE` — uninstall the previous `com.spop.poverlay` first.
- `Unable to import GPX` — verify the file is valid and use the fallback folder.
- `Overlay permission denied` — grant `Display over other apps`.
- `Overlay blocked the UI` — stop the app with `adb shell am force-stop com.spop.poverlay`.

---

## Reporting Issues

Please do not approach Peloton with issues related to this app. Use the repository issue tracker instead.
