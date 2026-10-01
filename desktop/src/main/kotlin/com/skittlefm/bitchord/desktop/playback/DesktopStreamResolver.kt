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

class DesktopStreamResolver(network: OkHttpClient) {
    private val http = HttpClient(OkHttp) {
        engine { preconfigured = network }

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