# Africa-focused data collection platform

An offline-first platform for collecting field data in low-connectivity areas across Africa. It serves NGOs, governments and researchers in health, agriculture and other sectors.

**Status:** Phase 1, month 1. The code is scaffolded: the SMS codec and Bridge service are built and tested; the infrastructure is written and validated but not yet deployed.

**Approach:** extend ODK (stock ODK Central + a branded fork of ODK Collect), written in Kotlin and hosted on AWS af-south-1. Built by a team of 2 developers.

## Current design

![Phase 1 architecture](docs/04-phase1-spec/phase1_architecture_odk.svg)

- [Phase 1 technical spec](docs/04-phase1-spec/PHASE1_SPEC.md): components, SMS protocol, security, 6-month plan, pilot targets
- [Roadmap (24 months)](docs/03-revised-design/africa_data_platform_roadmap_v2.svg)
- [Offline sync flow](docs/03-revised-design/offline_sync_flow_v2.svg)
- [Design notes](docs/03-revised-design/DESIGN_NOTES.md)

## Repository layout

| Path | What | Docs |
|---|---|---|
| [`libs/sms-codec`](libs/sms-codec/) | Encrypted SMS encoding shared by the app and the Bridge (Kotlin, no dependencies) | [README](libs/sms-codec/README.md) |
| [`services/bridge`](services/bridge/) | Receives SMS fallbacks and writes them into ODK Central (Kotlin + Ktor) | [README](services/bridge/README.md) |
| [`infra`](infra/) | Terraform for AWS af-south-1, plus the Central and Bridge setup runbook | [README](infra/README.md) |
| [`forms`](forms/) | XLSForm templates: consent, SMS partial records, example with SMS fields | [README](forms/README.md) |
| [`docs`](docs/) | Design history (steps 1–4) and the Phase 1 spec | below |
| `Abson-dev/collect` *(separate repo, to create)* | Branded fork of ODK Collect | spec, section 3.2 |

### Developer quick start

You need JDK 17. Gradle downloads itself through the wrapper.

```bash
./gradlew test                          # codec + Bridge tests
./gradlew :services:bridge:buildFatJar  # services/bridge/build/libs/bridge-all.jar
python forms/build_templates.py         # regenerate XLSForms (needs openpyxl)
```

CI (GitHub Actions) runs the Kotlin tests, `terraform validate` and XLSForm validation on every push.

## How the design evolved

Each step has its own folder, and each step is a separate commit in the git history.

| Step | Folder | What happened |
|---|---|---|
| 1 | [docs/01-initial-design](docs/01-initial-design/) | First roadmap, architecture and sync flow |
| 2 | [docs/02-design-review](docs/02-design-review/DESIGN_REVIEW.md) | Consistency review: 10 issues found |
| 3 | [docs/03-revised-design](docs/03-revised-design/) | Revised diagrams fixing those issues |
| 4 | [docs/04-phase1-spec](docs/04-phase1-spec/PHASE1_SPEC.md) | Decisions made; Phase 1 technical spec built on ODK |
| 5 | `libs/`, `services/`, `infra/`, `forms/` | Code scaffold: SMS codec, Bridge service, Terraform, form templates, CI |

## Roadmap at a glance

| Phase | Months | Focus |
|---|---|---|
| 1. MVP core | 1–6 | ODK Central on AWS, branded Collect with SMS fallback, consent and audit trail; pilot at month 6 |
| 2. Field intelligence | 7–12 | Maps and GIS, team management, live monitoring, iOS, mobile money billing, in-region hosting |
| 3. Sector depth | 13–18 | Health (DHIS2), agriculture, analytics and export |
| 4. Platform and scale | 19–24 | Public API and SDK, on-premise and white-label, USSD, more local regions |

## Next steps

1. Create the AWS account, enable af-south-1, and deploy staging ([infra/README.md](infra/README.md)).
2. Run the four month-1 spikes (S1–S4) in the [spec](docs/04-phase1-spec/PHASE1_SPEC.md#9-spikes-month-1-each--3-days) against staging.
3. Fork ODK Collect to `Abson-dev/collect`; choose a product name before branding.
4. Choose 1–2 pilot partners (NGO or research team).
