# Switchback

Switchback is an Android overlay app for the Peloton Bike+. It adds a real-time HUD, GPX route import, simulated route riding, and experimental resistance control.

> This project is experimental and unaffiliated with Peloton Interactive. It uses undocumented internal interfaces and may break with Peloton OS updates. Use at your own risk.

---

## Contents

- [Hardware requirements](#hardware-requirements)
- [Community](#community)
- [How to install](#how-to-install)
- [How to import a GPX route](#how-to-import-a-gpx-route)
- [What the overlay does](#what-the-overlay-does)
- [What route simulation does and does not do](#what-route-simulation-does-and-does-not-do)
- [Experimental Bike+ resistance control](#experimental-bike-resistance-control)
- [Troubleshooting](#troubleshooting)
- [License](#license)

---

## Hardware requirements

- Peloton Bike+ (`PLTN-TTR01`) for full resistance control support
- Standard Peloton Bike is supported for overlay and HUD features only
- ADB access or developer mode to sideload the APK
- A Windows, Mac, or Linux machine with `adb` installed for the initial install

---

## Community

Switchback is built for the Peloton DIY and community hardware space. If you are modding your bike, looking for GPX routes, or want to discuss this project, visit:

**[openpelo.com](https://openpelo.com)**

---

## How to install

### 1. Build the APK

From the `grupetto` folder:

```powershell
cd "C:\Peloton Project\grupetto"
.\gradlew.bat assembleDebug
```

The APK is written to:

```
app/build/outputs/apk/debug/app-debug.apk
```

### 2. Enable developer/sideload mode on the bike

On most Peloton firmware, go to **Settings → About** and tap the build number several times to enable developer options. Then enable **USB debugging**.

### 3. Connect ADB

```powershell
adb devices
```

Confirm the bike appears in the device list.

### 4. Install the APK

```powershell
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

If you see `INSTALL_FAILED_VERSION_DOWNGRADE`, uninstall the existing package first:

```powershell
adb uninstall com.spop.poverlay
adb install app/build/outputs/apk/debug/app-debug.apk
```

### 5. Grant overlay permission

Launch Switchback on the bike. When prompted, grant the **Display over other apps** permission. This is required for the HUD to work.

### 6. If the overlay covers the Peloton UI

Force-stop the app from another device via ADB:

```powershell
adb shell am force-stop com.spop.poverlay
```

---

## How to import a GPX route

### Option A — In-app import (preferred)

1. Open Switchback on the bike.
2. Go to the **Routes** tab.
3. Tap **Import GPX**.
4. Select a `.gpx` file from the file picker.
5. The route appears in your saved route list.

### Option B — ADB push (fallback for locked-down Peloton firmware)

Many Peloton devices do not support the standard Android document picker. If the in-app picker fails, push your GPX file directly:

```powershell
adb push "MyRoute.gpx" "/sdcard/Android/data/com.spop.poverlay/files/route_imports/"
```

Then open Switchback and tap **Import GPX** again to scan the import folder.

### Supported GPX formats

- `<trk>` track elements
- `<rte>` route elements
- Namespaced GPX exports (including RideWithGPS)
- Missing elevation values are handled gracefully

### Import error messages

| Message | Meaning |
|---|---|
| `Imported route "<name>"` | Success |
| `Could not import GPX route` | Generic failure — check the file |
| `Unable to parse GPX` | Invalid or malformed GPX |
| `No compatible document picker found` | Use the ADB push fallback |

---

## What the overlay does

Switchback renders a HUD overlay on top of the Peloton ride screen. You can configure which metrics appear and where the overlay docks.

### Available HUD fields

- Power (watts)
- Cadence (rpm)
- Resistance
- Speed (mph)
- Distance (miles)
- Elapsed time
- Calories / work estimate
- Heart rate (if paired)
- Route grade (when route is active)
- Route progress (when route is active)

### Overlay Designer

Open the **HUD** tab in Switchback to:

- Start or stop the overlay
- Toggle individual metric tiles on and off
- Change the dock position (top, bottom, left, right)
- Adjust scale and opacity
- Show or hide the timer when the app is minimized

---

## What route simulation does and does not do

### What it does

- Imports GPX routes and parses distance, elevation, grade, and difficulty
- Saves routes locally on the bike
- Tracks your position along the route as you ride based on recorded distance
- Displays current grade, remaining distance, and route progress in the HUD
- On Bike+, maps route grade to a resistance target using a configurable multiplier
- Records `routePositionMeters` in each ride sample

### What it does not do

- Not a 3D virtual world or video environment
- Does not model exact physics from terrain
- Does not use real-time GPS positioning
- Does not replace Peloton's structured workout system
- Does not sync with the Peloton cloud or companion app

---

## Experimental Bike+ resistance control

Switchback includes opt-in resistance automation for the Peloton Bike+.

### To enable

1. Open **Settings** in Switchback.
2. Under **Bike+ Controls**, enable **Experimental Bike+ resistance control**.
3. Optionally enable **Simulate route grade with resistance** to let an active GPX route drive the target resistance.

### Safety constraints

- Hardware-gated: only active on `PLTN-TTR01` (Bike+)
- Disabled by default
- Resistance writes are clamped to `0–100`
- Writes are rate-limited (no more than one per 5 seconds)
- Step size is limited to ±2 resistance points per update
- Only applies when a route is active and simulation is enabled
- Manual control is never blocked — you can tap the bike's resistance knob at any time

### Important warnings

- This is not certified or endorsed by Peloton
- Do not use on non-Bike+ hardware
- This is not a safety-certified product feature
- The app does not perform motor calibration, homing, OTA, bootloader, or serial control

---

## Troubleshooting

| Problem | Fix |
|---|---|
| `INSTALL_FAILED_VERSION_DOWNGRADE` | Uninstall existing `com.spop.poverlay` first |
| Overlay permission denied | Go to Settings → Apps → Switchback → Display over other apps |
| Overlay covers the Peloton UI and you can't dismiss it | `adb shell am force-stop com.spop.poverlay` |
| GPX import fails repeatedly | Confirm the file has valid `<trk>` or `<rte>` points; try the ADB push fallback |
| Bike+ resistance control not appearing | Enable it explicitly in Settings → Bike+ Controls |
| App crashes on launch | Check that overlay permission is granted |

---

## License

Switchback is released under the [Business Source License 1.1](LICENSE).

- Free for non-commercial personal use to monitor or supplement indoor cycling on your own Peloton device.
- You may not redistribute it, offer it as a hosted service, or use it to build a competing commercial product.
- On 2030-05-22, this project converts to the MIT License.

---

## Reporting issues

Do not contact Peloton about this app. Use the repository issue tracker.

This project is not affiliated with or endorsed by Peloton Interactive, Inc.
