package com.skittlefm.bitchord.desktop.playback

import com.metrolist.innertubex.InnerTube
import com.metrolist.innertubex.cipher.PlayerConfigRepository
import com.metrolist.innertubex.cipher.RemotePlayerConfigStore
import com.metrolist.innertubex.cipher.YouTubeCipherService
import com.metrolist.innertubex.extraction.ContentHints
import com.metrolist.innertubex.extraction.ExtractedStream
import com.metrolist.innertubex.extraction.InnerTubeExtractor
import com.metrolist.innertubex.extraction.YtConfigParserImpl
import com.metrolist.innertubex.models.YouTubeLocale
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient

class YouTubeVerificationRequiredException : java.net.ProtocolException(
    "O YouTube solicitou uma verificação para este acesso.",
)

class DesktopStreamResolver(network: OkHttpClient) {
    private val http = HttpClient(OkHttp) {
        engine {
            preconfigured = network

            addNetworkInterceptor { chain ->
                val request = chain.request()
                val response = chain.proceed(request)

                if (response.code in listOf(301, 302, 303, 307, 308)) {
                    val from = request.url
                    val target = response.header("Location")?.let { from.resolve(it) }
                    val origin = "${from.scheme}://${from.host}:${from.port}${from.encodedPath}"
                    val destination = target?.let {
                        "${it.scheme}://${it.host}:${it.port}${it.encodedPath}"
                    } ?: "Location ausente ou inválido"

                    println("[HTTP] ${response.code}: $origin -> $destination")

                    if (
                        target != null &&
                        target.host in setOf("www.google.com", "google.com") &&
                        target.encodedPath.startsWith("/sorry/")
                    ) {
                        response.close()
                        throw YouTubeVerificationRequiredException()
                    }
                }

                response
            }
        }

        install(ContentNegotiation) {
            json(Json {
                ignoreUnknownKeys = true
                explicitNulls = false
                encodeDefaults = true
            })
        }

        install(HttpTimeout) {
            requestTimeoutMillis = 30_000
            connectTimeoutMillis = 15_000
            socketTimeoutMillis = 20_000
        }

        expectSuccess = false
    }

    private val configs = object : PlayerConfigRepository {
        override val enabled = true
        override val sourceUrl =
            "https://raw.githubusercontent.com/ZemerTeam/zemer-cipher/master/library/src/main/assets/player_configs.json"

        override val defaultSourceUrl = sourceUrl
        override var cachedJson = ""
        override var cachedAtMs = 0L
        override var cachedSourceUrl = ""
        override var cachedEtag = ""
    }

    private val innerTube = InnerTube(http).apply {
        locale = YouTubeLocale(gl = "BR", hl = "pt")
    }

    private val remoteStore = RemotePlayerConfigStore(http, configs)
    private val cipher = YouTubeCipherService(http, remoteStore)

    private val extractor = InnerTubeExtractor(
        configParser = YtConfigParserImpl(http, innerTube, remoteStore),
        cipherService = cipher,
        innerTube = innerTube,
    )

    suspend fun resolve(videoId: String): ExtractedStream =
        withContext(Dispatchers.IO) {
            withTimeout(90_000) {
                extractor.extract(
                    videoId = videoId,
                    hints = ContentHints().withStreamCapabilities(
                        allowHls = false,
                        allowSabr = false,
                        allowBoundedRange = true,
                    ),
                ) ?: error("Nenhum áudio disponível para essa música.")
            }
        }

    suspend fun close() {
        innerTube.close()

        try {
            cipher.dispose()
        } finally {
            http.close()
        }
    }
}