# Gemini agent context — deck-lotus-android

You are bootstrapping a **native Android companion app** for deck-lotus. Read
`docs/ENGINEERING_BRIEF.md` first (the full engineering evaluation), then
`docs/ARCHITECTURE.md` and `docs/GETTING_STARTED.md`.

## What this app is

A dedicated optical capture engine for one fixed rig: **Google Pixel 10 Pro** in a
**Card Slinger 3.0** motorized card feeder / fixed cradle. It captures MTG cards,
extracts an art hash and the collector-block text, and POSTs results to the existing
deck-lotus server over the LAN. It does **not** reimplement inventory, decks, trades, or
matching — the server owns all of that.

## Hard constraints

- **Target device only.** Optimize for the Pixel 10 Pro / Tensor G5. No need to support
  other phones, tablets, or Android TV. minSdk can be high (34+).
- **Camera2 / CameraX with full manual control.** Fixed `LENS_FOCUS_DISTANCE`,
  `CONTROL_AF_MODE_OFF`, `CONTROL_AE_MODE_OFF`, `SENSOR_EXPOSURE_TIME` ~1/500s,
  `SENSOR_SENSITIVITY` locked, physical camera ID pinned to the 50MP 1x wide sensor,
  tone-mapping controlled. This determinism is the whole point — see brief §2.
- **ML Kit Text Recognition v2** for the collector block. On-device, NPU-accelerated.
- **Hashing must match deck-lotus `src/shared/` exactly.** `HASH_HEIGHT = 680`, 256-bit
  DCT art hash, 64-bit frame hash, framing ladder `[0.84, 0.88, 0.92, 0.96, 1.00]`.
  Port the algorithm faithfully; do not "improve" it — a silent drift breaks matching.
  Port OpenCV C++ via JNI or use the OpenCV Android SDK.
- **Kotlin** for app code. C++/JNI only where OpenCV or perf demands it.
- **Secrets never committed.** API base URL + token go in `local.properties` /
  `BuildConfig` or an in-app settings screen, not source.

## Workflow

- Work on branches, not `main`. Conventional-ish names: `feat/…`, `fix/…`, `chore/…`.
- Keep commits scoped and message-first.
- Update `docs/` when you make an architectural decision so the next session has it.
- The server-side ingest endpoint does not exist yet. Define the contract you need in
  `docs/ARCHITECTURE.md` and stub a local mock; the deck-lotus side will be built to
  match.

## First milestone

See `docs/GETTING_STARTED.md` — a walking skeleton: manual-control camera preview →
single capture → rectify + hash + OCR → POST to a mock endpoint → show the JSON
response. Feeder automation (USB-OTG / BLE to the Card Slinger ESP32) comes later.
