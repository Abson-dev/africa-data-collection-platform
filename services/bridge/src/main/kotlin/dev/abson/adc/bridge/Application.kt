package dev.abson.adc.bridge

import dev.abson.adc.bridge.central.KtorCentralClient
import dev.abson.adc.bridge.merge.MergeJob
import dev.abson.adc.bridge.registry.InMemoryRegistry
import dev.abson.adc.bridge.sms.InboundSmsHandler
import dev.abson.adc.bridge.sms.SmsOutcome
import dev.abson.adc.bridge.store.InMemoryPartialStore
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.application.log
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.plugins.calllogging.CallLogging
import io.ktor.server.request.httpMethod
import io.ktor.server.request.path
import io.ktor.server.request.receiveParameters
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import java.security.MessageDigest
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

fun main() {
    val config = BridgeConfig.fromEnv()
    val registry = config.seedFile?.let(InMemoryRegistry::fromSeedFile) ?: InMemoryRegistry()
    val store = InMemoryPartialStore()
    val central = KtorCentralClient(config.centralUrl, config.centralEmail, config.centralPassword)

    val handler = InboundSmsHandler(registry, store, central, config.partialForm)
    val mergeJob = MergeJob(store, central, registry, config.partialForm)

    embeddedServer(Netty, port = config.port) {
        bridgeModule(handler, config.smsWebhookToken, mergeJob, config.mergeIntervalMinutes.minutes)
    }.start(wait = true)
}

fun Application.bridgeModule(
    handler: InboundSmsHandler,
    webhookToken: String,
    mergeJob: MergeJob? = null,
    mergeInterval: Duration = 10.minutes,
) {
    install(CallLogging) {
        // Path only: the query string carries the webhook token and must not reach the logs.
        format { call -> "${call.response.status()}: ${call.request.httpMethod.value} ${call.request.path()}" }
    }

    routing {
        get("/health") { call.respondText("ok") }

        // Inbound SMS callback from Africa's Talking (form-encoded: from, to, text, date, id, ...).
        // Configure the callback URL as https://<host>/sms/inbound?token=<SMS_WEBHOOK_TOKEN>.
        post("/sms/inbound") {
            val token = call.request.queryParameters["token"]
            if (token == null || !MessageDigest.isEqual(token.toByteArray(), webhookToken.toByteArray())) {
                call.respond(HttpStatusCode.Forbidden)
                return@post
            }
            val params = call.receiveParameters()
            val from = params["from"]
            val text = params["text"]
            if (from.isNullOrBlank() || text == null) {
                call.respond(HttpStatusCode.BadRequest)
                return@post
            }

            val outcome = handler.handle(from, text)
            val log = call.application.log
            when (outcome) {
                is SmsOutcome.Rejected -> log.warn("SMS rejected: {}", outcome.reason)
                is SmsOutcome.Failed -> log.warn("Partial {} not written: {}", outcome.instanceId, outcome.reason)
                else -> log.info("SMS {}", outcome.label)
            }
            // Always 200 for valid callbacks, so the gateway does not resend messages we have already handled.
            call.respondText(outcome.label)
        }
    }

    mergeJob?.start(this, mergeInterval)
}
