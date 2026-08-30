# Getting started

## Environment

- Android Studio (latest stable), AGP + Gradle to match.
- Kotlin, Jetpack Compose for UI.
- Target: Pixel 10 Pro. minSdk 34+, targetSdk latest. Test on the physical device —
  the emulator cannot exercise Camera2 manual controls or the Tensor NPU.
- Dependencies you'll need: CameraX + Camera2 interop, ML Kit Text Recognition v2
  (`com.google.mlkit:text-recognition`), OpenCV Android SDK (or a prebuilt AAR),
  a networking client (Retrofit/OkHttp or Ktor).

## Milestone 1 — walking skeleton

Goal: prove the full path end to end on-device, against a mock server.

1. **Project scaffold** — single-activity Compose app, one `CaptureScreen`.
2. **Manual-control camera preview** — Camera2 session with AF/AE off, fixed focus,
   locked exposure (~1/500s) and ISO, 1x wide physical camera pinned. Show the preview
   plus a readout of the actual capture-result metadata so you can confirm the locks
   held.
3. **Single capture** — button press grabs one `ImageReader` frame (YUV_420_888).
4. **Vision pipeline** — OpenCV: detect card contour, perspective-rectify, crop to
   680px height. Compute the 256-bit art hash and 64-bit frame hash using constants
   from `docs/ARCHITECTURE.md`. Unit-test the hash against known vectors pulled from
   deck-lotus.
5. **OCR** — run ML Kit v2 on the rectified crop's bottom-left region; parse set code +
   collector number. Log latency.
6. **Ingest client** — POST the payload from `docs/ARCHITECTURE.md` to a configurable
   base URL. Ship a tiny mock (a local Ktor server, or MockWebServer in tests) that
   echoes a `confident` response.
7. **Result UI** — show tier, resolved printing, hash distance, round-trip latency.
   Distinct haptics for confident vs. needs-attention.

Settings screen: base URL + bearer token, persisted (DataStore). Never hard-code.

## Milestone 2 — throughput

- Continuous capture loop (drop → detect settle via frame hash delta → capture) without
  waiting on web-style stillness streaks.
- Batch or WebSocket ingest.
- Session view: running count, unresolved queue, per-card timing histogram.

## Milestone 3 — feeder automation

- USB-OTG serial or BLE link to the Card Slinger 3.0 controller.
- Trigger the capture loop from feeder events; "feed next" pulse from the app.

## Server side (tracked in deck-lotus, not here)

`POST /api/scan/ingest` per `docs/ARCHITECTURE.md`. Until it exists, everything runs
against the mock. Flag the contract early if the app needs a field the draft omits.

## Conventions

- Branch per change; never commit to `main`.
- Record architectural decisions in `docs/` as you make them.
- Keep hashing constants in lockstep with deck-lotus `src/shared/`.
