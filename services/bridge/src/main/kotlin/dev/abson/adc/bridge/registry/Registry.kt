package dev.abson.adc.bridge.registry

import dev.abson.adc.smscodec.FormKey
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.util.Base64

/** A form version that has fields marked for SMS (`bind::adc:sms = yes` in the XLSForm). */
data class FormInfo(
    val projectId: Int,
    val xmlFormId: String,
    val version: String,
    /** Names of SMS-marked fields, in form order: the order of values in an SMS. */
    val smsFields: List<String>,
) {
    val formKey: Int get() = FormKey.of(xmlFormId, version)
}

/** A per-app-user AES key, provisioned to the phone via the Central QR code. */
class DeviceKey(val keyId: Int, val appUser: String, val key: ByteArray, val revoked: Boolean = false)

/** Lookup of forms and device keys. */
interface Registry {
    fun form(formKey: Int): FormInfo?
    fun key(keyId: Int): DeviceKey?
}

/**
 * In-memory registry for development and tests.
 *
 * TODO(phase1, month 3): replace with a PostgreSQL-backed registry. Forms should sync automatically
 * from Central (read each published form's XML and collect `adc:sms` binds); keys should be stored
 * encrypted with KMS and created when an app user is provisioned.
 */
class InMemoryRegistry(forms: List<FormInfo> = emptyList(), keys: List<DeviceKey> = emptyList()) : Registry {
    private val forms = forms.associateBy { it.formKey }
    private val keys = keys.associateBy { it.keyId }

    override fun form(formKey: Int) = forms[formKey]
    override fun key(keyId: Int) = keys[keyId]

    companion object {
        /** Loads a JSON seed file (format: services/bridge/seed.example.json). Development only: keys are in plain text. */
        fun fromSeedFile(path: String): InMemoryRegistry {
            val seed = Json { ignoreUnknownKeys = true }.decodeFromString<Seed>(File(path).readText())
            return InMemoryRegistry(
                forms = seed.forms.map { FormInfo(it.projectId, it.xmlFormId, it.version, it.smsFields) },
                keys = seed.keys.map {
                    DeviceKey(it.keyId, it.appUser, Base64.getDecoder().decode(it.keyBase64), it.revoked)
                },
            )
        }
    }

    @Serializable
    private data class Seed(val forms: List<SeedForm> = emptyList(), val keys: List<SeedKey> = emptyList())

    @Serializable
    private data class SeedForm(val projectId: Int, val xmlFormId: String, val version: String, val smsFields: List<String>)

    @Serializable
    private data class SeedKey(val keyId: Int, val appUser: String, val keyBase64: String, val revoked: Boolean = false)
}
