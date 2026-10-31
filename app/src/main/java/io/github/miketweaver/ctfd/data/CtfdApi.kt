package io.github.miketweaver.ctfd.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit

class CtfdException(message: String) : IOException(message)

/**
 * A CTFd instance's REST API, called as the person with their access token.
 * Every call runs on the IO dispatcher and throws [CtfdException] with a message fit to show.
 */
class CtfdApi(
    val baseUrl: HttpUrl,
    private val token: String,
    private val client: OkHttpClient = sharedClient,
) {
    suspend fun me(): Me = get("users/me", Me.serializer())

    suspend fun teamMe(): TeamMe = get("teams/me", TeamMe.serializer())

    suspend fun status(): Status {
        val me = me()
        val team = if (me.teamId != null) teamMe() else null
        return Status(me, team)
    }

    suspend fun challenges(): List<ChallengeSummary> =
        get("challenges", ListSerializer(ChallengeSummary.serializer()))

    suspend fun challenge(id: Int): ChallengeDetail = get("challenges/$id", ChallengeDetail.serializer())

    suspend fun attempt(challengeId: Int, submission: String): AttemptResult =
        post(
            "challenges/attempt",
            json.encodeToString(AttemptRequest.serializer(), AttemptRequest(challengeId, submission)),
            AttemptResult.serializer(),
            // CTFd answers a rate-limited attempt with 429 and a normal body.
            acceptStatus = setOf(429),
        )!!

    suspend fun unlockHint(hintId: Int) {
        post<Unit>("unlocks", json.encodeToString(UnlockRequest.serializer(), UnlockRequest(hintId)), null)
    }

    suspend fun hint(hintId: Int): Hint = get("hints/$hintId", Hint.serializer())

    suspend fun scoreboard(): List<Standing> = get("scoreboard", ListSerializer(Standing.serializer()))

    suspend fun solves(accountType: String, accountId: Int): List<Solve> {
        val kind = if (accountType == "team") "teams" else "users"
        return get("$kind/$accountId/solves", ListSerializer(Solve.serializer()))
    }

    /** The absolute URL of a challenge file, which carries its own download token. */
    fun fileUrl(path: String): HttpUrl? = baseUrl.resolve(path)

    /**
     * Downloads an image a description shows, returning its bytes and content type. Images on
     * the CTF's own host need the token (CTFd 403s without a session); no other host ever sees
     * it, and OkHttp drops it on a redirect to another host.
     */
    suspend fun fetch(url: HttpUrl, maxBytes: Long = 15L * 1024 * 1024): Pair<ByteArray, String?> =
        withContext(Dispatchers.IO) {
            val builder = Request.Builder().url(url)
            if (url.host == baseUrl.host && url.scheme == baseUrl.scheme && url.port == baseUrl.port) {
                builder.header("Authorization", "Token $token").header("Content-Type", "application/json")
            }
            val response = try {
                client.newCall(builder.build()).execute()
            } catch (e: IOException) {
                throw CtfdException("Can't reach ${url.host}: ${e.message ?: e.javaClass.simpleName}")
            }
            response.use {
                if (!it.isSuccessful) throw CtfdException("HTTP ${it.code}")
                val length = it.body.contentLength()
                if (length > maxBytes) throw CtfdException("too large (${length / 1024 / 1024} MB)")
                val bytes = it.body.source().use { source ->
                    source.request(maxBytes + 1)
                    if (source.buffer.size > maxBytes) throw CtfdException("too large")
                    source.buffer.readByteArray()
                }
                bytes to it.header("Content-Type")
            }
        }

    private suspend fun <T> get(path: String, serializer: KSerializer<T>): T =
        call(request(path).get().build(), serializer, emptySet())!!

    private suspend fun <T> post(
        path: String,
        body: String,
        serializer: KSerializer<T>?,
        acceptStatus: Set<Int> = emptySet(),
    ): T? = call(request(path).post(body.toRequestBody(jsonType)).build(), serializer, acceptStatus)

    private fun request(path: String): Request.Builder {
        val url = baseUrl.newBuilder().addPathSegments("api/v1/$path").build()
        return Request.Builder()
            .url(url)
            .header("Authorization", "Token $token")
            // CTFd only accepts token auth on JSON requests.
            .header("Content-Type", "application/json")
            .header("Accept", "application/json")
    }

    private suspend fun <T> call(request: Request, serializer: KSerializer<T>?, acceptStatus: Set<Int>): T? =
        withContext(Dispatchers.IO) {
            val response = try {
                client.newCall(request).execute()
            } catch (e: IOException) {
                throw CtfdException("Can't reach ${baseUrl.host}: ${e.message ?: e.javaClass.simpleName}")
            }
            response.use {
                val text = it.body.string()
                val isJson = it.header("Content-Type").orEmpty().contains("json")
                if (!it.isSuccessful && it.code !in acceptStatus) {
                    throw CtfdException(describeFailure(it.code, if (isJson) text else null, it.request.url.encodedPath))
                }
                if (!isJson) {
                    // A bad token sends us to the login page rather than failing with 401.
                    throw CtfdException("${baseUrl.host} sent its login page; the token isn't valid for this CTF")
                }
                if (serializer == null) return@use null
                try {
                    val envelope = json.decodeFromString(Envelope.serializer(serializer), text)
                    envelope.data ?: throw CtfdException(envelope.message ?: "CTFd returned no data")
                } catch (e: SerializationException) {
                    throw CtfdException("Unexpected answer from CTFd: ${e.message?.take(200)}")
                }
            }
        }

    private fun describeFailure(code: Int, body: String?, path: String): String {
        val detail = body?.let { runCatching { json.parseToJsonElement(it).jsonObject }.getOrNull() }
            ?.let { o -> o["message"]?.jsonPrimitive?.content ?: o["errors"]?.toString() }
        return when (code) {
            401, 403 -> detail ?: "Access denied (HTTP $code): the token is wrong or expired, or the CTF isn't running"
            404 -> detail ?: "Not found (HTTP 404): $path"
            429 -> "Rate limited; wait a minute before trying again"
            else -> "CTFd error HTTP $code" + (detail?.let { ": $it" } ?: "")
        }
    }

    companion object {
        val json = Json {
            ignoreUnknownKeys = true
            coerceInputValues = true
            explicitNulls = false
        }
        private val jsonType = "application/json".toMediaType()

        val sharedClient: OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()

        /**
         * Turns what the person typed into an instance URL, the way `ctfd add` does:
         * https is assumed and plain http is refused except for a local test server.
         */
        fun normalizeUrl(input: String): HttpUrl {
            var text = input.trim().trimEnd('/')
            if (text.isEmpty()) throw CtfdException("Enter the CTF's address")
            if (!text.contains("://")) text = "https://$text"
            val url = text.toHttpUrlOrNull() ?: throw CtfdException("That isn't a web address")
            if (url.scheme == "http" && url.host !in localHosts) {
                throw CtfdException("Refusing plain http; use the https address")
            }
            return url.newBuilder().query(null).fragment(null).build()
        }

        private val localHosts = setOf("localhost", "127.0.0.1", "10.0.2.2")

        /** The saved name for an instance: the host label that says which CTF it is. */
        fun defaultName(url: HttpUrl): String {
            val labels = url.host.split('.').let { if (it.size > 1) it.dropLast(1) else it }
            val generic = setOf("ctf", "www", "ctfd", "play", "demo")
            return labels.firstOrNull { it !in generic && it.isNotBlank() } ?: labels.first()
        }
    }
}
