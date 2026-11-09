# SMS codec

Encodes a form's SMS-marked fields into encrypted, GSM-7-safe text messages and decodes them again. It's pure Kotlin/JVM with no dependencies. The same code runs in the Collect fork (Android 8+) and in the Bridge service.

```kotlin
val record = SmsRecord(keyId = 7, formKey = FormKey.of("household_survey", "2026110901"),
                       instanceId = uuid, values = listOf("Ouagadougou", "Secteur 12", null))

val frames = SmsCodec.encode(record, key, SmsMode.SEPARATE)   // send each frame.toText() as one SMS
val body   = SmsCodec.encode(record, key, SmsMode.CONCATENATED).toSmsBody()  // or one pre-filled SMS

val decoded = SmsCodec.decode(SmsFrame.parseAll(text)) { keyId -> keyStore[keyId] }
```

`instanceId` is the UUID part of Collect's `instanceID`, without the `uuid:` prefix.

## Wire format (protocol version 1)

**Binary payload:**

| Bytes | Field |
|---|---|
| 1 | protocol version |
| 2 | key ID |
| 4 | form key: first 4 bytes of SHA-256(`formId` + NUL + `version`) |
| 16 | instanceID UUID |
| 12 | AES-GCM nonce |
| n | AES-256-GCM ciphertext of: 1 flag byte (0 raw, 1 deflate) + values |
| 16 | AES-GCM tag |

- The first 23 bytes are authenticated as associated data, so the header can't be altered without detection.
- **Values:** varint(count), then for each value varint(UTF-8 length + 1) followed by its bytes. Length 0 means unanswered.

**Text frames:** Base64 (no padding), split into frames of the form `#A` + part (1 digit) + total (1 digit) + 4-character message ref + chunk. Every character is in the GSM-7 alphabet, so an SMS holds the full 160 characters.

| Mode | When | Limit | Max payload | Data after 51-byte overhead |
|---|---|---|---|---|
| `SEPARATE` | Managed devices, auto-send | ≤ 3 SMS × 160 chars | 342 bytes | ≈ 291 bytes |
| `CONCATENATED` | Play Store build, pre-filled SMS | 1 body ≤ 459 chars (3 segments) | 338 bytes | ≈ 287 bytes |

Frames may arrive in any order, duplicated, or joined in one text separated by whitespace. `encode` refuses records that don't fit and tells the form designer to mark fewer fields.

## Tests

```bash
./gradlew :libs:sms-codec:test
```
