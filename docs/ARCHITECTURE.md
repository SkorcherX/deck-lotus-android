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

## Server Integration & Cloudflare Access Zero Trust

### 1. Cloudflare Access Captive Portal & CookieJar
The server may be hosted behind a Cloudflare Tunnel secured by Cloudflare Zero Trust (Google OAuth / Email OTP):
- **Captive Portal (`CloudflarePortalDialog.kt`)**: Embedded WebView modal configured with mobile Chrome User-Agent so Google OAuth and Email OTP complete smoothly.
- **Cookie Synchronization (`CloudflareCookieJar.kt`)**: Automatically passes `CF_Authorization` session cookies across all OkHttp network requests.

### 2. Authentication
- Headers sent: `X-API-Key: <token>` and `Authorization: Bearer <token>`.
- Connectivity health checked against `GET /api/auth/me`.

### 3. Collection Batch Commits
Batch scans are committed using the database-agnostic inventory bulk-add endpoint:
`POST {baseUrl}/api/inventory/bulk-add`

Request Body:
```json
{
  "source": "scanner",
  "items": [
    {
      "cardName": "Monstrous Rage",
      "setCode": "SOA",
      "collectorNumber": "45",
      "quantity": 1,
      "isFoil": false
    }
  ]
}
```

Response:
```json
{
  "added": 1,
  "failed": 0,
  "errors": []
}
```
*Note: Using `bulk-add` avoids reliance on database-specific auto-increment `printingId`s and seamlessly withstands weekly MTGJSON server database rebuilds.*

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
