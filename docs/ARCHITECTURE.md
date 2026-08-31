# Architecture

## Chosen option: B+ — Native Capture Engine with 100% On-Device Offline Matching

The app operates as a standalone, ultra-fast capture and identification engine, shipping
with the packed Scryfall 256-bit perceptual hash binary (`card-hashes.bin` — 6.3MB) and
a high-speed indexed SQLite identity database (`card-identities.db` — 8.3MB).

It performs sub-10ms card resolution on-device completely offline. The `deck-lotus` server
is used for syncing inventory, collection modifications, deck updates, and trades.

```
┌─ Pixel 10 Pro companion ───────────────────────────────────────────────────────────┐
│  CaptureService                                                                    │
│   • Camera2 session: AF/AE toggles, fixed focus diopters, exposure & ISO locks    │
│   • Preview settle detection: ~150ms settled drop trigger, peripheral screen flash  │
│                                                                                    │
│  Vision & On-Device Matching Pipeline (100% Offline)                               │
│   • Rectify: perspective warp to 487x680 standard frame                            │
│   • 256-bit DCT Art Hash + 64-bit Frame Hash                                       │
│   • ML Kit Text Recognition v2: Multi-line OCR name verification & token scoring   │
│   • SQLite Card Database: 35,016 card names & 112,815 printings with TCGPlayer $   │
│   • LocalCardResolver: ~10ms offline resolution with session set biasing           │
│                                                                                    │
│  Session Tray & Ingest Client                                                      │
│   • 60+ FPS batch drawer with live USD Batch Total, card counts & foil toggles     │
│   • HTTP POST to deck-lotus server for collection commits and trade batches        │
└────────────────────────────────────────────────────────────────────────────────────┘
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
