# Step 2: Design review of the initial design

**Question:** are the three initial diagrams (`../01-initial-design/`) consistent with each other and fit for the goal?

**Goal assumed:** reliable data collection for NGOs, governments and researchers working in low-connectivity areas in Africa.

**Verdict:** the direction is right, and the diagrams agree on the core: offline mobile app, local SQLite, sync queue, central API. Four issues had to be fixed before building:
1. the encryption model
2. what SMS fallback can carry
3. form versioning
4. the size of Phase 1

The fixes are in `../03-revised-design/`.

## Contradictions between the diagrams

| # | Issue | Fix applied in step 3 |
|---|---|---|
| 1 | Backend claims **end-to-end encryption**, but the server must read data for review, alerts, dashboards, webhooks and Power BI. | TLS + encryption at rest + encrypted device DB; E2E opt-in per project. |
| 2 | **SMS fallback** can't carry media; a full survey needs dozens of ~140-byte messages; SMS is unencrypted. No server-side SMS gateway was drawn, and duplicates after a later internet sync weren't handled. | SMS limited to key text fields, stored as a partial record, merged by UUID; gateway added. |
| 3 | Sync assumed **yes/no internet**; real networks are intermittent. | "Usable connection?" check; chunked, resumable media upload separate from record data. |

## Gaps for the African field context

| # | Issue | Fix applied in step 3 |
|---|---|---|
| 4 | **Form versioning** missing: the main real source of sync conflicts. "Conflict resolution" was undefined. | Form versions in Phase 1; conflicts designed out (append-only, UUID, read-only after sync). |
| 5 | **GPS point capture** only in Phase 2, but nearly every survey needs it. | Moved to Phase 1. |
| 6 | **Compliance late:** audit trail and data residency in Phase 4, while patient IDs and biometrics come in Phase 3 (POPIA, NDPA, Kenya DPA). | Consent, audit trail, remote wipe in Phase 1; in-region hosting in Phase 2. |
| 7 | **USSD** needs a session-based engine, not offline forms. | Marked as its own engine in Phase 4. |

## Feasibility

| # | Issue | Fix applied in step 3 |
|---|---|---|
| 8 | **Phase 1 too heavy** for 6 months: two mobile platforms, builder, media, sync, SMS, encryption. | Android-first; XLSForm import before drag-and-drop; iOS in Phase 2; month-6 pilot. |
| 9 | **ODK compatibility** only in Phase 4, though users already know ODK, KoboToolbox, SurveyCTO and CommCare. | XLSForm/ODK-compatible from Phase 1. |
| 10 | **No revenue** until Phase 4. | Paid plans + mobile money billing in Phase 2. |
| — | Architecture drew the web platform talking **directly to phones**. | Backend drawn as the hub. |
