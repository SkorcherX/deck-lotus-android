# deck-lotus-android

Android companion app for [deck-lotus](https://github.com/SkorcherX/deck-lotus) — a
high-speed optical capture engine for Magic: The Gathering card scanning.

## Why this exists

The deck-lotus web scanner (`scan.js`) resolves card **names** at 99%+ accuracy but is
bottlenecked by browser camera limitations and cannot run real-time OCR. It cannot tell
apart the ~30 reprints of *Cultivate* without a set code and collector number.

This app targets a fixed physical rig — **Google Pixel 10 Pro + Card Slinger 3.0
motorized feeder** — where the phone and card are rigidly motionless. That lets a native
app do what the browser can't:

- Lock focus, shutter (1/500s), ISO, and white balance via Camera2 — no focus hunting,
  no exposure drift, no drop-motion blur.
- Run **ML Kit Text Recognition v2** on the Tensor G5 NPU in ~15–25ms to read the
  collector block (set code + number) on every card.
- Push per-card latency from ~635ms (browser) down to ~85ms, unlocking 2–3 cards/sec
  unattended feeding.

## Architecture (Option B — Native Capture Bridge)

The app is **not** a reimplementation of deck-lotus. It captures, hashes, OCRs, and
POSTs to the existing deck-lotus server, which owns matching, inventory, and decks.

```
[ Pixel 10 Pro companion ]
  Camera2: fixed focus, 1/500s, ISO 100
  ML Kit (Tensor G5): collector block + set code  (~20ms)
  Native OpenCV: 680px rectified art + 256-bit DCT hash  (~6ms)
        │
        ▼  low-latency HTTP/WebSocket POST over LAN
[ deck-lotus server (Unraid / Docker) ]
  Matches hash + set + collector against SQLite printings
  Commits to inventory / deck in real time
```

See [`docs/ENGINEERING_BRIEF.md`](docs/ENGINEERING_BRIEF.md) for the full evaluation and
[`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md) for the capture pipeline and server
contract.

## Status

Greenfield. Scaffolding and handoff docs only. Initial implementation is being kicked
off with Gemini (Google's model, chosen for Pixel/Tensor hardware familiarity) — see
[`docs/GETTING_STARTED.md`](docs/GETTING_STARTED.md) and [`GEMINI.md`](GEMINI.md).

## Relationship to deck-lotus

Separate repo, separate lifecycle. The server-side ingest endpoint this app depends on
lives in the deck-lotus repo. Keep the hashing constants (`HASH_HEIGHT = 680`, the DCT
sizes, the framing ladder) in lockstep with `src/shared/` there — a drift breaks
matching silently.

## License

Not yet chosen. Treat as all-rights-reserved until a LICENSE file lands.
