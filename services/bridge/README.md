# Bridge service

Receives SMS fallbacks from Africa's Talking, decrypts them, and writes them into ODK Central as *partial records*. It then marks each partial as merged once the full record arrives in Central over the internet. See [Phase 1 spec, section 3.3](../../docs/04-phase1-spec/PHASE1_SPEC.md#33-bridge-service-ours).

Kotlin + Ktor. It uses the shared [`libs/sms-codec`](../../libs/sms-codec/).

## Endpoints

| Method | Path | Purpose |
|---|---|---|
| `GET` | `/health` | Liveness check |
| `POST` | `/sms/inbound?token=…` | Africa's Talking inbound SMS callback (form-encoded `from`, `text`, …). The token must equal `SMS_WEBHOOK_TOKEN`. |

The response body names the outcome: `not_data`, `waiting_for_parts`, `rejected`, `duplicate`, `written` or `write_failed`. Valid callbacks always get 200, so the gateway doesn't resend them.

## Run locally

From the repository root (JDK 17):

```bash
./gradlew :services:bridge:test            # tests
./gradlew :services:bridge:run             # needs the variables from .env.example
./gradlew :services:bridge:buildFatJar     # build/libs/bridge-all.jar
docker build -f services/bridge/Dockerfile -t adc-bridge .
```

## Code map

| Package | What |
|---|---|
| `sms` | `InboundSmsHandler` (parse → assemble parts → decrypt → write), `FrameAssembler` |
| `central` | `KtorCentralClient` (sessions, submissions), `PartialXml` (builds the `sms_partial` submission) |
| `merge` | `MergeJob`: retries failed writes, detects arrival of full records |
| `registry` | Forms with SMS fields, and device keys |
| `store` | Partial-record status |

## Not done yet (Phase 1 plan)

- **Month 3:** PostgreSQL implementations of `Registry`, `PartialStore` and pending SMS parts (in-memory now, so a restart loses state).
- **Month 3:** sync forms automatically from Central (read `adc:sms` binds) and create device keys when app users are provisioned (stored encrypted with KMS).
- **Month 3:** usage metering for Phase 2 billing.
- **Spike S1:** confirm Central's status codes for duplicate submissions; `KtorCentralClient.submit` treats 409 as "already stored".
