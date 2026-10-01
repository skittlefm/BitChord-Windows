package com.skittlefm.bitchord.desktop.playback

import com.music.bitchord.data.model.Song
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import okhttp3.OkHttpClient
import uk.co.caprica.vlcj.factory.MediaPlayerFactory
import uk.co.caprica.vlcj.player.base.MediaPlayer
import uk.co.caprica.vlcj.player.base.State
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

data class PlaybackState(
    val queue: List<Song> = emptyList(),
    val index: Int = -1,
    val isPlaying: Boolean = false,
    val isLoading: Boolean = false,
    val error: String? = null,
) {
    val song: Song? get() = queue.getOrNull(index)
    val hasNext: Boolean get() = index >= 0 && index < queue.lastIndex
}

class DesktopPlayer {
    private val dispatcher = Executors.newSingleThreadExecutor { task ->
        Thread(task, "BitChord audio")
    }.asCoroutineDispatcher()

    private val scope = CoroutineScope(SupervisorJob() + dispatcher)
    private val closing = AtomicBoolean(false)
    private val mutableState = MutableStateFlow(PlaybackState())

    val state = mutableState.asStateFlow()

    private val network = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .callTimeout(30, TimeUnit.SECONDS)
        .build()

    private var resolver: DesktopStreamResolver? = null
    private var factory: MediaPlayerFactory? = null
    private var player: MediaPlayer? = null
    private var media: HttpAudioMedia? = null
    private var playbackJob: Job? = null

    fun play(songs: List<Song>, index: Int) {
        val queue = songs.toList()
        if (index !in queue.indices) return

        command {
            mutableState.value = PlaybackState(
                queue = queue,
                index = index,
                isLoading = true,
            )
            startTrack(index)
        }
    }

    fun next() = command {
        if (state.value.hasNext) startTrack(state.value.index + 1)
    }

    fun retry() = command {
        if (state.value.song != null) startTrack(state.value.index)
    }

    fun togglePlayPause() = command {
        val current = state.value
        if (current.song == null || current.isLoading) return@command

        if (
            current.error != null ||
            player?.status()?.state() in
            listOf(State.ENDED, State.STOPPED, State.NOTHING_SPECIAL)
        ) {
            startTrack(current.index)
        } else {
            player?.controls()?.setPause(current.isPlaying)
            mutableState.value = current.copy(isPlaying = !current.isPlaying)
        }
    }

    private fun command(action: () -> Unit) {
        if (closing.get()) return

        scope.launch {
            if (closing.get()) return@launch

            try {
                action()
            } catch (error: Exception) {
                fail(error)
            }
        }
    }

    private fun startTrack(index: Int) {
        playbackJob?.cancel()
        stopMedia()

        mutableState.value = state.value.copy(
            index = index,
            isPlaying = false,
            isLoading = true,
            error = null,
        )

        val song = requireNotNull(state.value.song)

        playbackJob = scope.launch {
            try {
                val source = resolver ?: DesktopStreamResolver(network).also {
                    resolver = it
                }

                val stream = source.resolve(song.videoId)
                ensureActive()

                val engine = player ?: run {
                    val engineFactory = factory ?: MediaPlayerFactory(
                        "--no-video",
                        "--no-metadata-network-access",
                    ).also { factory = it }

                    engineFactory.mediaPlayers().newMediaPlayer().also {
                        player = it
                    }
                }

                val input = HttpAudioMedia(stream, network)
                media = input

                check(engine.media().play(input)) {
                    "O VLC recusou o áudio."
                }

                val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(30)
                var started = false

                while (isActive) {
                    check(input.failure == null) {
                        input.failure.orEmpty()
                    }

                    val nativeState = engine.status().state()

                    check(nativeState != State.ERROR) {
                        "O VLC informou um erro."
                    }

                    when (nativeState) {
                        State.PLAYING, State.PAUSED -> {
                            started = true
                            mutableState.value = state.value.copy(
                                isPlaying = nativeState == State.PLAYING,
                                isLoading = false,
                            )
                        }

                        State.OPENING, State.BUFFERING -> {
                            mutableState.value = state.value.copy(
                                isLoading = true,
                            )
                        }

                        State.ENDED -> if (started) {
                            mutableState.value = state.value.copy(
                                isPlaying = false,
                                isLoading = false,
                            )

                            if (state.value.hasNext) {
                                startTrack(state.value.index + 1)
                            }

                            return@launch
                        }

                        State.STOPPED -> if (started) {
                            mutableState.value = state.value.copy(
                                isPlaying = false,
                                isLoading = false,
                            )
                            return@launch
                        }

                        else -> Unit
                    }

                    check(started || System.nanoTime() < deadline) {
                        "O áudio não começou em 30 segundos."
                    }

                    delay(200)
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                if (isActive) fail(error)
            } catch (error: LinkageError) {
                if (isActive) fail(error)
            }
        }
    }

    private fun stopMedia() {
        media?.cancel()
        player?.controls()?.stop()
        media = null
    }

    private fun fail(error: Throwable) {
        val message = media?.failure
            ?: "Não foi possível reproduzir esta música. Tente novamente."

        println("[Player] Falha: ${error.javaClass.simpleName}")

        playbackJob?.cancel()
        runCatching { stopMedia() }

        mutableState.value = state.value.copy(
            isPlaying = false,
            isLoading = false,
            error = message,
        )
    }

    suspend fun close() {
        if (!closing.compareAndSet(false, true)) return

        try {
            withContext(NonCancellable + dispatcher) {
                media?.cancel()
                scope.coroutineContext.job.cancelAndJoin()

                try {
                    try {
                        stopMedia()
                    } finally {
                        try {
                            player?.release()
                        } finally {
                            factory?.release()
                        }
                    }
                } finally {
                    try {
                        resolver?.close()
                    } finally {
                        network.dispatcher.executorService.shutdown()
                        network.connectionPool.evictAll()
                    }
                }
            }
        } finally {
            dispatcher.close()
        }
    }
}