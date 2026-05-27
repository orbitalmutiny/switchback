# Claude Handoff Prompt: Manual Resistance Guidance MVP (Route-Driven)

## Status Update (Implemented)
- Implementation completed on branch `manual-resistance-guidance-mvp-handoff`.
- New files added:
  - `app/src/main/java/com/spop/poverlay/route/ManualResistanceTolerance.kt`
  - `app/src/main/java/com/spop/poverlay/route/ManualResistanceGuidanceState.kt`
  - `app/src/main/java/com/spop/poverlay/route/ManualResistanceGuidanceEngine.kt`
  - `app/src/test/java/com/spop/poverlay/route/ManualResistanceGuidanceEngineTest.kt` (18 tests)
- Existing models updated with typed guidance state:
  - `RouteHudState`
  - `LiveRideDashboardState`
- Settings added to `ConfigurationRepository` with defaults:
  - Guidance enabled = `true`
  - Tolerance = `normal` (±4)
  - Warning = `10` seconds
- ViewModel/runtime wiring completed:
  - `ConfigurationViewModel`
  - `OverlaySensorViewModel`
- UI integration completed:
  - Settings: new non-Bike+ `Manual Resistance Guidance` section
  - Ride page: guidance card
  - Overlay HUD: compact guidance line/summary integration
- Bike+ guidance suppressed for MVP; Bike+ write paths/guardrails unchanged.
- Verification already run:
  - `.\gradlew.bat testDebugUnitTest` PASSED
  - `.\gradlew.bat assembleDebug` PASSED

## Requested next step for Claude
Perform a focused review pass only:
- bugs
- behavioral regressions
- test gaps
You are implementing a new feature in the Android app at `C:\Peloton Project\switchback`.

## Branch and workspace context
- Work on branch: `manual-resistance-guidance-mvp-handoff`
- Current branch contains unrelated local modifications from previous work (README, AndroidManifest, ConfigurationPage, ConfigurationViewModel, OverlayService, and one untracked HR monitor file).
- Do **not** revert unrelated existing changes.
- Keep edits tightly scoped to this feature.

## Product objective
Implement a deterministic, non-blocking **Manual Resistance Guidance** system for non-Bike+ auto-control contexts.

MVP scope is locked:
- Source priority: **Route-only** (no HR/workouts/manual target yet)
- Surfaces: **Ride page + Overlay HUD**
- Default upcoming warning: **10 seconds**
- Default tolerance mode: **Normal (±4)**

This is advisory guidance only. No modals, no blocking UI, no forced actions.

## Existing architecture to reuse
- Route state model: `app/src/main/java/com/spop/poverlay/route/RouteHudState.kt`
- In-app dashboard state: `app/src/main/java/com/spop/poverlay/LiveRideDashboardState.kt`
- In-app runtime wiring: `app/src/main/java/com/spop/poverlay/ConfigurationViewModel.kt`
  - Current visual cue function exists (`visualResistanceCue(...)`) and is grade-based string output.
  - `dashboardRouteHudState(...)` already computes smoothed route grade.
- Overlay runtime wiring: `app/src/main/java/com/spop/poverlay/overlay/OverlaySensorViewModel.kt`
  - Similar `visualResistanceCue(...)` currently used in overlay route HUD state.
- Settings persistence: `app/src/main/java/com/spop/poverlay/ConfigurationRepository.kt`
- UI rendering:
  - Ride UI in `ConfigurationPage.kt` (Live Ride dashboard sections)
  - Overlay UI in `overlay/composables/OverlayMainContent.kt` and related overlay composables.

## Required implementation

### 1) Add guidance domain model + engine (pure Kotlin)
Create a new deterministic engine in `route` or `ride` domain that outputs a typed guidance state:
- `Neutral`
- `Upcoming`
- `AdjustmentNeeded`
- `InRange`
- `Stale`

Include in output:
- current resistance
- target range (min/max)
- signed delta + direction
- optional time-to-next-change seconds
- reason/source metadata as needed for UI text

Implement anti-flap behavior:
- debounce/hysteresis around tolerance boundaries
- stale transition after prolonged out-of-range non-adjustment

### 2) Target generation (route-only)
Use existing route progression + smoothed grade to derive target resistance center, then convert to range using tolerance mode:
- Tight ±2
- Normal ±4 (default)
- Loose ±6

Upcoming state logic:
- Trigger when next target transition ETA <= warning setting (default 10s)
- If warning setting is Off, suppress upcoming state

### 3) Wire into dashboard + overlay runtimes
Replace/augment current `visualResistanceCue` string path with typed guidance state:
- In `ConfigurationViewModel` live dashboard update loop
- In `OverlaySensorViewModel` route progress update path

Coexistence rules:
- Keep existing Bike+ automatic write control logic intact
- Do not weaken any existing guardrails/rate limits
- Guidance should still be visual/advisory and not interfere with write paths

### 4) UI rendering updates
Ride page:
- Render full guidance states with concise text
- Include directional cue and target range/current context
- Use non-blocking in-card presentation

Overlay HUD:
- Add compact guidance presentation compatible with existing full/minimized modes
- Show direction + target/current/delta clearly

Visual design rules:
- Color + text redundancy (not color-only)
- No flashing/aggressive animation
- No modal dialogs or full-screen interruptions

### 5) Settings + persistence
Add manual guidance settings (persisted in `ConfigurationRepository`):
- Enable Manual Resistance Guidance (default ON)
- Target Tolerance: Tight/Normal/Loose (default Normal)
- Advance Warning Timing: Off/5/10/15 seconds (default 10)

Expose flows + setters and wire to existing ViewModel/UI settings patterns.

### 6) Update notes
Append an entry to `C:\Peloton Project\FORK_NOTES.md` documenting:
- feature added
- locked defaults
- deferred sources (HR/workout/manual target)
- verification commands run

## API/type changes expected
- Add typed `ManualResistanceGuidanceState` (or equivalent sealed type)
- Extend `LiveRideDashboardState` and `RouteHudState` (or equivalent UI state container) with typed guidance state
- Add config preference keys/flows/setters for guidance enable/tolerance/warning
- Keep Bike+ resistance control APIs unchanged

## Testing requirements
Add/extend unit tests:

1. Guidance engine tests
- Neutral when no active target
- Upcoming when transition ETA is within warning window
- AdjustmentNeeded for below/above range
- InRange when within tolerance
- Stale after configured out-of-range duration
- Hysteresis/debounce around boundary oscillation
- Tolerance mode coverage (±2/±4/±6)

2. Integration-level tests (pure Kotlin where possible)
- Route smoothed grade -> stable target range mapping
- Signed delta and direction formatting/mapping
- Coexistence behavior with Bike+ auto-control toggles

3. UI/state tests (lightweight)
- Ride and overlay state mapping for all guidance states
- Ensure no blocking/modal behavior introduced

## Verification commands
Run and report:
- `./gradlew.bat testDebugUnitTest`
- `./gradlew.bat assembleDebug`

## Constraints
- Preserve existing behavior outside this feature.
- No broad refactor.
- Follow existing naming/patterns in this codebase.
- Keep changes small and reversible.

## Done criteria
- Guidance system is route-driven, deterministic, and visible on Ride + Overlay.
- Settings are functional and persisted with correct defaults.
- Tests pass and build succeeds.
- `FORK_NOTES.md` updated with key decisions and risks.