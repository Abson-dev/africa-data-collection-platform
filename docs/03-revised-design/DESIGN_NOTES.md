# Design notes: v1 → v2

The `_v2.svg` files revise the original diagrams. The originals are kept unchanged for comparison.

| File | What it shows |
|---|---|
| `africa_data_platform_roadmap_v2.svg` | 24-month plan, 4 phases |
| `mvp_system_architecture_v2.svg` | Phase 1 components and how they connect |
| `offline_sync_flow_v2.svg` | Path of one submission from phone to server |

## Changes and why

### 1. Encryption model clarified
- **v1:** "end-to-end encrypt" on the backend.
- **v2:** TLS in transit, encryption at rest on the server, encrypted database on the phone (SQLCipher). End-to-end encryption is **opt-in per project** (Phase 3, for sensitive health data).
- **Why:** with true end-to-end encryption the server cannot read the data. That breaks data review, outlier alerts, dashboards, webhooks and Power BI. Projects that opt in accept losing those features.

### 2. SMS fallback limited to small text payloads
- **v1:** SMS is the general "no internet" path.
- **v2:** SMS is optional, switched on per project, and for urgent records only. It sends **key text fields only** (compressed, signed, ≤ 3 messages). The server stores it as a *partial* record keyed by UUID. The full record and media stay in the outbox and upload later; the server merges them by UUID.
- **Why:** one SMS carries ~140 bytes. Photos, audio and video can't be sent, a full survey needs dozens of messages, and SMS is unencrypted and costs money per message. An SMS gateway (Africa's Talking, Twilio) is now drawn on the backend side, and Phase 2 adds SMS cost controls.

### 3. Sync made safe on bad networks
- The record and its media upload **separately**. Media uploads are chunked and resumable, with a Wi-Fi-only option.
- Uploads are **idempotent**: sending the same UUID twice returns the same acknowledgement and never creates a duplicate.
- "Internet available?" became "**Usable connection?**", meaning the server is reachable, not just that the phone shows signal bars.
- Retry happens when the network changes and every 15 minutes (tune this in the pilot).

### 4. Conflict resolution defined
Conflicts are designed out rather than resolved:
1. Submissions are append-only and keyed by a UUID generated on the phone.
2. Each submission carries its **form version**; the server validates it against that version.
3. After sync the device copy is read-only. Corrections happen on the server and are audit-logged.

### 5. Form versioning added (Phase 1)
Enumerators keep working on old form versions while offline, so the server must accept data from any published version. This was missing in v1 and is the main real source of sync problems.

### 6. Architecture: backend is the hub
- **v1** drew an arrow from the web platform straight to the mobile app.
- **v2:** both talk only to the backend over HTTPS.
- **New boxes:**
  - **Workers + queue:** media processing, exports, SMS decoding, data-quality checks. These keep slow jobs out of the API.
  - **Device security:** remote wipe for lost or stolen phones, PIN unlock, consent capture.

### 7. Phase 1 made shippable
- **Android first.** Android runs on most phones in sub-Saharan Africa, many of them low-end; target Android 8+ and 2 GB RAM. iOS moves to Phase 2, or earlier if a pilot partner needs it.
- **XLSForm/ODK import first, drag-and-drop builder in months 5–6.** Users already know XLSForm from ODK, KoboToolbox and SurveyCTO, so adopting it costs them little.
- **GPS point capture moved from Phase 2 to Phase 1.** Almost every survey records a location. Polygons, geo-fencing and offline maps stay in Phase 2.
- **Milestone:** a month-6 pilot with 1–2 partner organisations.

### 8. Compliance moved earlier
- Consent capture, audit trail and remote wipe are now in Phase 1. The audit trail was in Phase 4.
- In-region hosting moved from Phase 4 to Phase 2. Architecture assumes an African cloud region (e.g. AWS af-south-1) or in-country hosting.
- **Why:** the Phase 3 health features (patient IDs, biometrics) and government clients need these. So do laws such as POPIA (South Africa), the NDPA (Nigeria) and Kenya's Data Protection Act.

### 9. Revenue starts in Phase 2
Paid plans and **mobile money billing** moved from Phase 4 to Phase 2, so the platform has income before the costly sector modules.

### 10. Other smaller changes
- USSD is marked as needing its **own engine**: USSD works as live menu sessions, not offline forms.
- Phase 3 export adds R.
- Phase 4 adds ISO 27001, which government tenders often ask for.
- what3words is paired with free plus codes.

## Open decisions (need your input)

> **Decided on 9 November 2026:** NGOs, researchers and ministries · 2 developers · Kotlin · AWS · extend ODK.
> See [step 4: Phase 1 spec](../04-phase1-spec/PHASE1_SPEC.md) for the consequences.

1. **First users:** NGOs, a ministry, or researchers? This sets which Phase 2/3 features come first.
2. **Team size and budget:** this decides whether a 6-month Phase 1 is realistic.
3. **Mobile stack:** Kotlin (native Android, best on low-end phones) or Flutter/React Native (easier iOS later).
4. **Hosting:** AWS af-south-1, another provider, or in-country data centres for specific governments.
5. **Build vs. extend:** write a new form engine, or build on open ODK components (XForms spec, ODK Collect/Central) and differentiate on the Africa-specific layers.
