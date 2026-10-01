package com.skittlefm.bitchord.desktop

import com.skittlefm.bitchord.desktop.playback.DesktopStreamResolver
import com.skittlefm.bitchord.desktop.playback.HttpAudioMedia
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import uk.co.caprica.vlcj.factory.MediaPlayerFactory
import uk.co.caprica.vlcj.player.base.State
import java.util.concurrent.TimeUnit

object StreamCheck {
    @JvmStatic
    fun main(args: Array<String>) = runBlocking {
        val videoId = args.firstOrNull().orEmpty()

        require(Regex("[A-Za-z0-9_-]{11}").matches(videoId)) {
            "Informe o ID de 11 caracteres com -PvideoId=ID_DA_MUSICA."
        }

        val network = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .callTimeout(30, TimeUnit.SECONDS)
            .build()

        val resolver = DesktopStreamResolver(network)

        try {
            println("[Stream] Procurando o áudio. A primeira consulta pode demorar...")

            val stream = resolver.resolve(videoId)

            println("[Stream] Cliente: ${stream.clientName}")
            println("[Stream] Formato: ${stream.mimeType}; codec: ${stream.codecs}")

            val media = HttpAudioMedia(stream, network)

            val factory = MediaPlayerFactory(
                "--no-video",
                "--no-metadata-network-access",
            )

            try {
                val player = factory.mediaPlayers().newMediaPlayer()

                try {
                    check(player.media().play(media)) {
                        "O VLC recusou o áudio."
                    }

                    val deadline =
                        System.nanoTime() + TimeUnit.SECONDS.toNanos(30)

                    while (player.status().state() != State.PLAYING) {
                        check(media.failure == null) {
                            media.failure.orEmpty()
                        }

                        check(
                            player.status().state() !in
                                    listOf(State.ERROR, State.ENDED)
                        ) {
                            "O VLC encerrou antes de começar a tocar."
                        }

                        check(System.nanoTime() < deadline) {
                            "O áudio não começou em 30 segundos."
                        }

                        Thread.sleep(50)
                    }

                    println("[Stream] Reproduzindo por 30 segundos...")

                    val initialTime = player.status().time()

                    repeat(30) {
                        Thread.sleep(1_000)

                        check(media.failure == null) {
                            media.failure.orEmpty()
                        }

                        check(
                            player.status().state() !in
                                    listOf(State.ERROR, State.ENDED)
                        ) {
                            "A reprodução terminou antes do fim do teste."
                        }
                    }

                    check(player.status().time() > initialTime + 20_000) {
                        "O relógio de reprodução não avançou como esperado."
                    }

                    println("[Stream] Teste concluído.")
                } finally {
                    media.cancel()

                    try {
                        player.controls().stop()
                    } finally {
                        player.release()
                    }
                }
            } finally {
                factory.release()
            }
        } finally {
            try {
                withContext(NonCancellable) {
                    resolver.close()
                }
            } finally {
                network.dispatcher.executorService.shutdown()
                network.connectionPool.evictAll()
            }
        }
    }
}