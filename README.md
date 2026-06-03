# Switchback

Switchback is an Android overlay app for the Peloton Bike+. It adds a real-time HUD, GPX route import, simulated route riding, and experimental resistance control.

> This project is experimental and unaffiliated with Peloton Interactive. It uses undocumented internal interfaces and may break with Peloton OS updates. Use at your own risk.

## Screenshots

| Ride Dashboard | Routes |
|---|---|
| ![Ride Dashboard](docs/screenshots/Ride%20-%20Home%20Screen.png) | ![Routes](docs/screenshots/Routes%20Screen.png) |

| History | Settings |
|---|---|
| ![History](docs/screenshots/History%20Screen.png) | ![Settings](docs/screenshots/Settings%20Screen.png) |

**HUD overlay — full stats bar with route map and elevation profile**
![HUD Full](docs/screenshots/HUD%20Full%20-%20Elevation%20Map%20Full.png)

**HUD minimized — compact bar with route map panel**
![HUD Minimized Map](docs/screenshots/HUD%20Minimized%20-%20Map%20Full.png)

**HUD minimized — compact bar only**
![HUD Minimized](docs/screenshots/HUD%20Minimized%20-%20Map%20Minimized.png)

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

## License

Switchback is released under the [Business Source License 1.1](LICENSE).

- Free for non-commercial personal use to monitor or supplement indoor cycling on your own Peloton device.
- You may not redistribute it, offer it as a hosted service, or use it to build a competing commercial product.
- On 2030-05-22, this project converts to the MIT License.

---

## Reporting issues

Do not contact Peloton about this app. Use the repository issue tracker.

This project is not affiliated with or endorsed by Peloton Interactive, Inc.
