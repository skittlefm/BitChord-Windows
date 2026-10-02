package com.skittlefm.bitchord.desktop.playback

import com.music.bitchord.data.model.Song
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import okhttp3.OkHttpClient
import uk.co.caprica.vlcj.factory.MediaPlayerFactory
import uk.co.caprica.vlcj.player.base.MediaPlayer
import uk.co.caprica.vlcj.player.base.State
import java.io.File
import java.net.URI
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

data class PlaybackState(
    val queue: List<Song> = emptyList(),
    val index: Int = -1,
    val isPlaying: Boolean = false,
    val isLoading: Boolean = false,
    val error: String? = null,
    val requiresVerification: Boolean = false,
    // Identifica esta reprodução, inclusive quando a mesma música é repetida.
    val trackId: Long = 0,
    val positionMs: Long = 0,
    val durationMs: Long = 0,
    val isSeekable: Boolean = false,
    val volume: Int = 100,
) {
    val song: Song? get() = queue.getOrNull(index)
    val hasNext: Boolean get() = index >= 0 && index < queue.lastIndex
    val canSeek: Boolean
        get() = isSeekable && durationMs > 0 && !isLoading && error == null
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
    private var seekTarget: Long? = null
    private var seekDeadline = 0L

    fun play(songs: List<Song>, index: Int) {
        val queue = songs.toList()
        if (index !in queue.indices) return

        command {
            mutableState.value = PlaybackState(
                queue = queue,
                index = index,
                isLoading = true,
                trackId = state.value.trackId,
                volume = state.value.volume,
            )
            startTrack(index)
        }
    }

    fun next() = command {
        if (state.value.hasNext) startTrack(state.value.index + 1)
    }

    fun playFiles(files: List<File>) {
        if (files.isEmpty()) return

        val songs = files.map { selected ->
            val file = selected.absoluteFile
            val uri = file.toURI().toString()
            Song(
                videoId = "local:$uri",
                title = file.nameWithoutExtension,
                artist = "Arquivo local",
                thumbnailUrl = null,
                localUri = uri,
            )
        }
        // Reutiliza a mesma fila, o mesmo estado e os mesmos controles.
        play(songs, 0)
    }

    fun previous() = command {
        val current = state.value
        if (current.song == null) return@command

        if (current.positionMs > 3_000 || current.index == 0) {
            if (current.canSeek) seekTo(0, current.trackId)
            else startTrack(current.index)
        } else {
            startTrack(current.index - 1)
        }
    }

    fun seekTo(positionMs: Long, trackId: Long) = command {
        val current = state.value
        // Um arrasto iniciado na faixa anterior não pode alterar a faixa atual.
        if (current.trackId != trackId || !current.canSeek) return@command
        val engine = player ?: return@command
        if (engine.status().state() !in listOf(State.PLAYING, State.PAUSED)) return@command
        val target = positionMs.coerceIn(0, current.durationMs)

        engine.controls().setTime(target)
        seekTarget = target
        seekDeadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(3)
        mutableState.value = current.copy(positionMs = target)
    }

    fun setVolume(value: Int) = command {
        val volume = value.coerceIn(0, 100)
        player?.audio()?.setVolume(volume)
        mutableState.value = state.value.copy(volume = volume)
    }

    private fun readPosition(engine: MediaPlayer, duration: Long): Long {
        val measured = engine.status().time().coerceIn(0, duration)
        // O relógio nativo pode demorar alguns ciclos para confirmar uma busca.
        seekTarget?.let { target ->
            if (kotlin.math.abs(measured - target) <= 1_000 ||
                System.nanoTime() >= seekDeadline
            ) {
                seekTarget = null
            }
        }
        return seekTarget ?: measured
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
        seekTarget = null

        mutableState.value = state.value.copy(
            index = index,
            isPlaying = false,
            isLoading = true,
            error = null,
            requiresVerification = false,
            trackId = state.value.trackId + 1,
            positionMs = 0,
            durationMs = 0,
            isSeekable = false,
        )

        val song = requireNotNull(state.value.song)

        playbackJob = scope.launch {
            try {
                val localFile = song.localUri?.let { location ->
                    withContext(Dispatchers.IO) {
                        val uri = URI(location)
                        require(uri.scheme.equals("file", ignoreCase = true)) {
                            "O endereço do arquivo local não é válido."
                        }
                        File(uri).also { file ->
                            check(file.isFile && file.canRead()) {
                                "O arquivo local não foi encontrado ou não pode ser lido."
                            }
                        }
                    }
                }

                val stream = if (localFile == null) {
                    val source = resolver ?: DesktopStreamResolver(network).also {
                        resolver = it
                    }
                    source.resolve(song.videoId)
                } else {
                    null
                }
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

                val input = stream?.let { HttpAudioMedia(it, network) }
                media = input

                val accepted = if (localFile != null) {
                    // O VLC recebe o caminho real, com espaços e acentos decodificados.
                    engine.media().play(localFile.absolutePath)
                } else {
                    engine.media().play(requireNotNull(input))
                }
                check(accepted) {
                    "O VLC recusou o áudio."
                }
                engine.audio().setVolume(state.value.volume)

                val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(30)
                var started = false

                while (isActive) {
                    check(input?.failure == null) {
                        input?.failure.orEmpty()
                    }

                    val nativeState = engine.status().state()

                    check(nativeState != State.ERROR) {
                        "O VLC informou um erro."
                    }

                    when (nativeState) {
                        State.PLAYING, State.PAUSED -> {
                            started = true
                            val duration = engine.status().length()
                                .takeIf { it > 0 } ?: state.value.durationMs
                            mutableState.value = state.value.copy(
                                isPlaying = nativeState == State.PLAYING,
                                isLoading = false,
                                positionMs = readPosition(engine, duration),
                                durationMs = duration,
                                isSeekable = engine.status().isSeekable(),
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
                                positionMs = state.value.durationMs,
                                isSeekable = false,
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
                                isSeekable = false,
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
        val verification = generateSequence(error) { it.cause }
            .take(16)
            .any { it is YouTubeVerificationRequiredException }

        val message = if (verification) {
            "O YouTube solicitou uma verificação para este acesso. Abra a música no site."
        } else {
            media?.failure
                ?: "Não foi possível reproduzir esta música. Tente novamente."
        }

        println("[Player] Falha: ${error.javaClass.simpleName}")
        error.printStackTrace()

        playbackJob?.cancel()
        runCatching { stopMedia() }

        mutableState.value = state.value.copy(
            isPlaying = false,
            isLoading = false,
            error = message,
            requiresVerification = verification,
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