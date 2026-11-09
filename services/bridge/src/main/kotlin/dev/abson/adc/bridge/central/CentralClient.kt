package dev.abson.adc.bridge.central

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.http.encodeURLPathPart
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** The parts of the ODK Central API the Bridge uses. */
interface CentralClient {
    /**
     * Submits an XForm submission. Succeeds if it is stored or was already stored
     * (Central answers 409 for an instanceID it already has).
     */
    suspend fun submit(projectId: Int, xmlFormId: String, xml: String)

    /** @param instanceId as Central knows it, e.g. `uuid:0b1c...`. */
    suspend fun submissionExists(projectId: Int, xmlFormId: String, instanceId: String): Boolean
}

class CentralException(message: String) : Exception(message)

/**
 * Talks to ODK Central's REST API with a web-user session.
 * Use a dedicated Central web user with the Project Manager role on the relevant projects only.
 */
class KtorCentralClient(
    private val baseUrl: String,
    private val email: String,
    private val password: String,
    private val http: HttpClient = defaultHttpClient(),
) : CentralClient {
    private val lock = Mutex()
    private var token: String? = null

    override suspend fun submit(projectId: Int, xmlFormId: String, xml: String) {
        val url = "$baseUrl/v1/projects/$projectId/forms/${xmlFormId.encodeURLPathPart()}/submissions"
        val response = authorized { t ->
            http.post(url) {
                bearerAuth(t)
                contentType(ContentType.Application.Xml)
                setBody(xml)
            }
        }
        if (response.status.isSuccess() || response.status == HttpStatusCode.Conflict) return
        throw CentralException("Submit to $xmlFormId failed: ${response.status} ${response.bodyAsText().take(300)}")
    }

    override suspend fun submissionExists(projectId: Int, xmlFormId: String, instanceId: String): Boolean {
        val url = "$baseUrl/v1/projects/$projectId/forms/${xmlFormId.encodeURLPathPart()}" +
            "/submissions/${instanceId.encodeURLPathPart()}"
        val response = authorized { t -> http.get(url) { bearerAuth(t) } }
        return when (response.status) {
            HttpStatusCode.OK -> true
            HttpStatusCode.NotFound -> false
            else -> throw CentralException("Lookup of $instanceId failed: ${response.status}")
        }
    }

    /** Runs [call] with a session token; logs in again once if the token has expired. */
    private suspend fun authorized(call: suspend (String) -> HttpResponse): HttpResponse {
        val first = call(currentToken())
        if (first.status != HttpStatusCode.Unauthorized) return first
        lock.withLock { token = null }
        return call(currentToken())
    }

    private suspend fun currentToken(): String = lock.withLock { token ?: login().also { token = it } }

    private suspend fun login(): String {
        val response = http.post("$baseUrl/v1/sessions") {
            contentType(ContentType.Application.Json)
            setBody(SessionRequest(email, password))
        }
        if (!response.status.isSuccess()) throw CentralException("Central login failed: ${response.status}")
        return response.body<SessionResponse>().token
    }

    @Serializable
    private data class SessionRequest(val email: String, val password: String)

    @Serializable
    private data class SessionResponse(val token: String)

    companion object {
        fun defaultHttpClient() = HttpClient(CIO) {
            install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
            expectSuccess = false
        }
    }
}
