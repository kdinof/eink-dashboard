package com.eink.dashboard.remote

import android.content.Context
import android.net.ConnectivityManager
import android.net.LinkProperties
import android.net.Network
import android.net.NetworkCapabilities
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.ApplicationCallPipeline
import io.ktor.server.application.call
import io.ktor.server.application.install
import io.ktor.server.engine.ApplicationEngine
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.origin
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.request.host
import io.ktor.server.request.receive
import io.ktor.server.response.header
import io.ktor.server.response.respond
import io.ktor.server.response.respondBytes
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.put
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.Json
import java.net.Inet4Address

sealed interface RemoteServerState {
    data object Stopped : RemoteServerState
    data object NoWifi : RemoteServerState
    data class Running(val url: String) : RemoteServerState
    data class Failed(val message: String) : RemoteServerState
}

/** Foreground-only HTTP server bound to the active Wi-Fi IPv4 address. */
class RemoteWebServer(
    private val context: Context,
    private val settings: RemoteSettingsService,
    private val pairing: PairingManager,
) {
    private val connectivity = context.getSystemService(ConnectivityManager::class.java)
    private val _state = MutableStateFlow<RemoteServerState>(RemoteServerState.Stopped)
    val state: StateFlow<RemoteServerState> = _state.asStateFlow()
    private var requested = false
    private var callbackRegistered = false
    private var engine: ApplicationEngine? = null
    private var boundHost: String? = null

    private val callback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) = refreshBinding()
        override fun onLinkPropertiesChanged(network: Network, linkProperties: LinkProperties) = refreshBinding()
        override fun onLost(network: Network) = refreshBinding()
    }

    @Synchronized
    fun start() {
        if (requested) return
        requested = true
        if (!callbackRegistered) {
            runCatching { connectivity.registerDefaultNetworkCallback(callback) }
                .onSuccess { callbackRegistered = true }
        }
        refreshBinding()
    }

    @Synchronized
    fun stop() {
        requested = false
        stopEngine()
        if (callbackRegistered) {
            runCatching { connectivity.unregisterNetworkCallback(callback) }
            callbackRegistered = false
        }
        _state.value = RemoteServerState.Stopped
    }

    @Synchronized
    private fun refreshBinding() {
        if (!requested) return
        val host = wifiIpv4()
        if (host == null) {
            stopEngine()
            _state.value = RemoteServerState.NoWifi
            return
        }
        if (host == boundHost && engine != null) return
        stopEngine()
        val url = "http://$host:$PORT"
        runCatching {
            embeddedServer(Netty, host = host, port = PORT) {
                configureRemoteApi(context, settings, pairing, host, url)
            }.also { it.start(wait = false) }
        }.onSuccess {
            engine = it
            boundHost = host
            _state.value = RemoteServerState.Running(url)
        }.onFailure {
            _state.value = RemoteServerState.Failed("Port $PORT is unavailable")
        }
    }

    private fun wifiIpv4(): String? {
        val network = connectivity.activeNetwork ?: return null
        val capabilities = connectivity.getNetworkCapabilities(network) ?: return null
        if (!capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) return null
        return connectivity.getLinkProperties(network)?.linkAddresses
            ?.map { it.address }
            ?.filterIsInstance<Inet4Address>()
            ?.firstOrNull { !it.isLoopbackAddress }
            ?.hostAddress
    }

    private fun stopEngine() {
        engine?.stop(200, 1_000)
        engine = null
        boundHost = null
    }

    companion object { const val PORT = 8787 }
}

private fun Application.configureRemoteApi(
    context: Context,
    settings: RemoteSettingsService,
    pairing: PairingManager,
    expectedHost: String,
    url: String,
) {
    install(ContentNegotiation) {
        json(Json { ignoreUnknownKeys = false; encodeDefaults = true })
    }
    install(StatusPages) {
        exception<IllegalArgumentException> { call, _ ->
            call.respond(HttpStatusCode.BadRequest, ApiError("invalid_request"))
        }
        exception<Throwable> { call, _ ->
            call.respond(HttpStatusCode.InternalServerError, ApiError("internal_error"))
        }
    }
    intercept(ApplicationCallPipeline.Plugins) {
        call.response.header("X-Content-Type-Options", "nosniff")
        call.response.header("Referrer-Policy", "no-referrer")
        call.response.header("X-Frame-Options", "DENY")
        call.response.header(
            "Content-Security-Policy",
            "default-src 'self'; script-src 'self'; style-src 'self'; img-src 'self' data:; " +
                "connect-src 'self'; object-src 'none'; base-uri 'none'; frame-ancestors 'none'",
        )
        // Assets ship inside the APK. Prevent phones from retaining old HTML/JS
        // after an update; API responses can also contain private configuration.
        call.response.header(HttpHeaders.CacheControl, "no-store, max-age=0")
    }

    routing {
        get("/") { call.respondAsset(context, "remote/index.html", ContentType.Text.Html) }
        get("/app.js") { call.respondAsset(context, "remote/app.js", ContentType.Application.JavaScript) }
        get("/styles.css") { call.respondAsset(context, "remote/styles.css", ContentType.Text.CSS) }
        get("/ink.css") { call.respondAsset(context, "remote/ink.css", ContentType.Text.CSS) }
        get("/icons.svg") { call.respondAsset(context, "remote/icons.svg", ContentType.Image.SVG) }

        route("/api/v1") {
            post("/pairings") {
                if (!call.isSameOrigin(expectedHost, url)) return@post
                val body = call.receive<PairRequest>()
                when (val result = pairing.pair(body.pin, body.clientName, call.request.origin.remoteHost)) {
                    is PairResult.Success -> call.respond(
                        HttpStatusCode.Created,
                        PairResponse(result.token, result.session.id),
                    )
                    PairResult.Rejected -> call.respond(HttpStatusCode.Unauthorized, ApiError("pairing_rejected"))
                    PairResult.RateLimited -> call.respond(HttpStatusCode.TooManyRequests, ApiError("rate_limited"))
                }
            }

            get("/config") {
                if (!call.authorized(pairing, expectedHost, url)) return@get
                call.respond(settings.snapshot())
            }
            put("/dashboard") {
                if (!call.authorized(pairing, expectedHost, url)) return@put
                settings.updateDashboard(call.receive())
                call.respond(settings.snapshot())
            }
            put("/calendar") {
                if (!call.authorized(pairing, expectedHost, url)) return@put
                settings.updateCalendar(call.receive())
                call.respond(settings.snapshot())
            }
            put("/todoist") {
                if (!call.authorized(pairing, expectedHost, url)) return@put
                settings.updateTodoist(call.receive())
                call.respond(settings.snapshot())
            }
            put("/taskforge") {
                if (!call.authorized(pairing, expectedHost, url)) return@put
                settings.updateTaskForge(call.receive())
                call.respond(settings.snapshot())
            }
            put("/todoist/token") {
                if (!call.authorized(pairing, expectedHost, url)) return@put
                val verified = settings.updateTodoistToken(call.receive<TodoistTokenUpdate>().token)
                if (verified) call.respond(settings.snapshot())
                else call.respond(HttpStatusCode.UnprocessableEntity, ApiError("token_rejected"))
            }
            delete("/todoist/token") {
                if (!call.authorized(pairing, expectedHost, url)) return@delete
                settings.clearTodoistToken()
                call.respond(settings.snapshot())
            }
            put("/weather") {
                if (!call.authorized(pairing, expectedHost, url)) return@put
                settings.updateWeather(call.receive())
                call.respond(settings.snapshot())
            }
            post("/permissions/{type}") {
                if (!call.authorized(pairing, expectedHost, url)) return@post
                val type = when (call.parameters["type"]) {
                    "calendar" -> RemotePermission.CALENDAR
                    "location" -> RemotePermission.LOCATION
                    "taskforge-file" -> RemotePermission.TASKFORGE_FILE
                    else -> throw IllegalArgumentException("Unknown permission")
                }
                val pending = settings.requestPermission(type)
                if (pending == null) call.respond(HttpStatusCode.Conflict, ApiError("permission_request_pending"))
                else call.respond(HttpStatusCode.Accepted, PermissionResponse(pending.id))
            }
            post("/calendar/google/connect") {
                if (!call.authorized(pairing, expectedHost, url)) return@post
                call.respond(settings.beginGoogleOAuth("$url/"))
            }
            post("/calendar/google/complete") {
                if (!call.authorized(pairing, expectedHost, url)) return@post
                settings.completeGoogleOAuth(call.receive<GoogleCompleteRequest>().handoffId)
                call.respond(settings.snapshot())
            }
            delete("/calendar/google") {
                if (!call.authorized(pairing, expectedHost, url)) return@delete
                settings.disconnectGoogle()
                call.respond(settings.snapshot())
            }
            get("/sessions") {
                if (!call.authorized(pairing, expectedHost, url)) return@get
                call.respond(pairing.sessions.value.map(TrustedSession::toDto))
            }
            delete("/sessions") {
                if (!call.authorized(pairing, expectedHost, url)) return@delete
                pairing.revokeAll()
                call.respond(HttpStatusCode.NoContent)
            }
            delete("/sessions/{id}") {
                if (!call.authorized(pairing, expectedHost, url)) return@delete
                pairing.revoke(call.parameters["id"] ?: throw IllegalArgumentException("Missing id"))
                call.respond(HttpStatusCode.NoContent)
            }
        }
    }
}

private suspend fun ApplicationCall.respondAsset(context: Context, path: String, type: ContentType) {
    val bytes = context.assets.open(path).use { it.readBytes() }
    respondBytes(bytes, type)
}

private suspend fun ApplicationCall.isSameOrigin(expectedHost: String, url: String): Boolean {
    val validHost = request.host() == expectedHost
    val origin = request.headers[HttpHeaders.Origin]
    val validOrigin = origin == null || origin == url
    if (!validHost || !validOrigin) {
        respond(HttpStatusCode.Forbidden, ApiError("forbidden_origin"))
        return false
    }
    return true
}

private suspend fun ApplicationCall.authorized(
    pairing: PairingManager,
    expectedHost: String,
    url: String,
): Boolean {
    if (!isSameOrigin(expectedHost, url)) return false
    val header = request.headers[HttpHeaders.Authorization].orEmpty()
    val token = header.takeIf { it.startsWith("Bearer ") }?.removePrefix("Bearer ").orEmpty()
    if (pairing.authenticate(token) == null) {
        respond(HttpStatusCode.Unauthorized, ApiError("unauthorized"))
        return false
    }
    return true
}

private fun TrustedSession.toDto() = SessionDto(id, clientName, createdAtEpochMs, lastUsedAtEpochMs)
