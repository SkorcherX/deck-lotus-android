# Architecture

## Chosen option: B — Native Capture Bridge

The app is a thin, high-performance capture client. The deck-lotus server keeps
ownership of the reference index, printing/set/collector matching, inventory, decks,
trades, and audit. This avoids porting the deck-lotus web UI and business logic to
Android, and keeps a single source of truth for card data.

```
┌─ Pixel 10 Pro companion ───────────────────────────────┐
│  CaptureService                                        │
│   • Camera2 session: AF off, AE off, fixed focus,      │
│     SENSOR_EXPOSURE_TIME ~1/500s, ISO locked,          │
│     physical 1x wide sensor pinned, tone-map fixed     │
│   • ImageReader YUV_420_888 → Y-plane (no RGBA copy)   │
│                                                        │
│  VisionPipeline                                        │
│   • OpenCV: contour → perspective rectify → 680px crop │
│   • DCT: 256-bit art hash + 64-bit frame hash          │
│     (constants identical to deck-lotus src/shared/)    │
│   • ML Kit Text Recognition v2 → collector block       │
│     parse: set code, collector number, language, foil? │
│                                                        │
│  IngestClient  ── HTTP POST (or WS) over LAN ──────────┼──►
└────────────────────────────────────────────────────────┘
                                                          │
┌─ deck-lotus server (Unraid / Docker) ──────────────────◄┘
│  POST /api/scan/ingest  (NEW — to be built in deck-lotus)
│   • match art hash against binary index (localIndex.js)
│   • constrain / confirm with set + collector from OCR
│   • return resolved printing + confidence tier
│   • optional: commit to inventory / target deck
└────────────────────────────────────────────────────────┘
```

## Server contract (draft — implement the mock to this shape)

`POST {baseUrl}/api/scan/ingest`
Auth: `Authorization: Bearer <token>` (LAN, but still token-gated like the web API).

Request:
```json
{
  "artHash":   "<256-bit hex, 64 chars>",
  "frameHash": "<64-bit hex, 16 chars>",
  "ocr": {
    "setCode":     "ECC",
    "collector":   "0001",
    "language":    "EN",
    "rawLines":    ["ECC • EN", "0001", "..."],
    "confidence":  0.94
  },
  "capture": {
    "exposureNs": 2000000,
    "iso":        100,
    "focusDist":  4.2,
    "device":     "pixel-10-pro",
    "rig":        "card-slinger-3.0"
  },
  "commit": { "mode": "inventory", "deckId": null, "isFoil": false }
}
```

Response:
```json
{
  "tier": "confident",           // confident | probable | pick-printing | unresolved
  "printing": {
    "uuid": "…", "name": "Cultivate", "setCode": "ECC",
    "collector": "0001", "isFoil": false
  },
  "candidates": [ /* when tier = pick-printing */ ],
  "committed": true,
  "hashDistanceBits": 34
}
```

Notes:
- The art hash still does the heavy lifting; OCR set+collector is a **constraint /
  tiebreaker** that lets reprints reach `confident` instead of `pick-printing`.
- `frameHash` lets the server dedupe accidental double-drops.
- Batch variant (`POST /api/scan/ingest/batch`) or a WebSocket stream is a later
  optimization for full Card Slinger throughput; start with one request per card.

## Hashing parity (do not drift)

| Constant | Value | Source |
| --- | --- | --- |
| `HASH_HEIGHT` | 680 | deck-lotus `src/shared/cardCapture.js` |
| Art hash | 256-bit DCT | `src/shared/cardHash.js` |
| Frame hash | 64-bit DCT | `src/shared/cardHash.js` |
| Framing ladder | `[0.84, 0.88, 0.92, 0.96, 1.00]` | `src/shared/cardCapture.js` |
| Match threshold | ~77 bits | `src/shared/scanFusion.js` |

When deck-lotus changes any of these, this app must change with it in the same PR
cycle. Consider extracting the constants into a tiny shared JSON both repos read.

## Deferred: feeder automation

Phone ↔ Card Slinger 3.0 microcontroller (ESP32/Arduino) over USB-OTG serial or BLE:
phone sends "feed next" → card drops → optical sensor trips → phone captures → loop.
Out of scope for milestone 1; design the capture loop so a trigger source can be
swapped from "manual button" to "feeder event" without restructuring.
