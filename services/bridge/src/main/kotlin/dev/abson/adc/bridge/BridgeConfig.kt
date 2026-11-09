package dev.abson.adc.bridge

/** All settings come from environment variables (see services/bridge/.env.example). */
data class BridgeConfig(
    val port: Int,
    val centralUrl: String,
    val centralEmail: String,
    val centralPassword: String,
    val partialForm: PartialFormRef,
    val smsWebhookToken: String,
    val mergeIntervalMinutes: Long,
    val seedFile: String?,
) {
    companion object {
        fun fromEnv(env: Map<String, String> = System.getenv()): BridgeConfig {
            fun required(name: String) =
                env[name]?.takeIf { it.isNotBlank() } ?: error("Missing environment variable $name")

            val token = required("SMS_WEBHOOK_TOKEN")
            require(token.length >= 32) { "SMS_WEBHOOK_TOKEN must be at least 32 characters" }

            return BridgeConfig(
                port = env["PORT"]?.toInt() ?: 8080,
                centralUrl = required("CENTRAL_URL").trimEnd('/'),
                centralEmail = required("CENTRAL_EMAIL"),
                centralPassword = required("CENTRAL_PASSWORD"),
                partialForm = PartialFormRef(
                    projectId = required("SMS_PARTIAL_PROJECT_ID").toInt(),
                    formId = env["SMS_PARTIAL_FORM_ID"] ?: "sms_partial",
                    version = required("SMS_PARTIAL_FORM_VERSION"),
                ),
                smsWebhookToken = token,
                mergeIntervalMinutes = env["MERGE_INTERVAL_MINUTES"]?.toLong() ?: 10,
                seedFile = env["BRIDGE_SEED_FILE"]?.takeIf { it.isNotBlank() },
            )
        }
    }
}

/** Where partial (SMS) records are written in Central: the `sms_partial` form from forms/templates. */
data class PartialFormRef(val projectId: Int, val formId: String, val version: String)
