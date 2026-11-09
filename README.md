# Africa-focused data collection platform

An offline-first platform for collecting field data in low-connectivity areas across Africa. It serves NGOs, governments and researchers in health, agriculture and other sectors.

**Status:** Phase 1 spec drafted. No code yet.

**Approach:** extend ODK (stock ODK Central + a branded fork of ODK Collect), written in Kotlin and hosted on AWS af-south-1. Built by a team of 2 developers.

## Current design

![Phase 1 architecture](docs/04-phase1-spec/phase1_architecture_odk.svg)

- [Phase 1 technical spec](docs/04-phase1-spec/PHASE1_SPEC.md): components, SMS protocol, security, 6-month plan, pilot targets
- [Roadmap (24 months)](docs/03-revised-design/africa_data_platform_roadmap_v2.svg)
- [Offline sync flow](docs/03-revised-design/offline_sync_flow_v2.svg)
- [Design notes](docs/03-revised-design/DESIGN_NOTES.md)

## How the design evolved

Each step has its own folder, and each step is a separate commit in the git history.

| Step | Folder | What happened |
|---|---|---|
| 1 | [docs/01-initial-design](docs/01-initial-design/) | First roadmap, architecture and sync flow |
| 2 | [docs/02-design-review](docs/02-design-review/DESIGN_REVIEW.md) | Consistency review: 10 issues found |
| 3 | [docs/03-revised-design](docs/03-revised-design/) | Revised diagrams fixing those issues |
| 4 | [docs/04-phase1-spec](docs/04-phase1-spec/PHASE1_SPEC.md) | Decisions made; Phase 1 technical spec built on ODK |

## Roadmap at a glance

| Phase | Months | Focus |
|---|---|---|
| 1. MVP core | 1–6 | ODK Central on AWS, branded Collect with SMS fallback, consent and audit trail; pilot at month 6 |
| 2. Field intelligence | 7–12 | Maps and GIS, team management, live monitoring, iOS, mobile money billing, in-region hosting |
| 3. Sector depth | 13–18 | Health (DHIS2), agriculture, analytics and export |
| 4. Platform and scale | 19–24 | Public API and SDK, on-premise and white-label, USSD, more local regions |

## Next steps

1. Month 1: run the four spikes (S1–S4) in the [spec](docs/04-phase1-spec/PHASE1_SPEC.md#9-spikes-month-1-each--3-days).
2. Set up the code layout: `services/bridge`, `libs/sms-codec`, `infra`, `forms/templates`; fork ODK Collect to `Abson-dev/collect`.
3. Choose 1–2 pilot partners (NGO or research team).
