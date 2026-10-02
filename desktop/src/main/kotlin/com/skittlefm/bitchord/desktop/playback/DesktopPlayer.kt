package com.skittlefm.bitchord.desktop.playback

import com.music.bitchord.data.model.Song
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import okhttp3.OkHttpClient
import uk.co.caprica.vlcj.factory.MediaPlayerFactory
import uk.co.caprica.vlcj.player.base.MediaPlayer
import uk.co.caprica.vlcj.player.base.MediaPlayerEventAdapter
import uk.co.caprica.vlcj.player.base.State
import java.io.File
import java.net.URI
import java.util.UUID
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

data class PlaybackState(
    val queue: List<Song> = emptyList(),
    // Muda apenas quando outra lista substitui a fila.
    val queueId: Long = 0,
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
    private var playbackEvents: MediaPlayerEventAdapter? = null
    private var media: HttpAudioMedia? = null
    private var playbackJob: Job? = null
    private var seekTarget: Long? = null
    private var seekDeadline = 0L
    private var playWhenReady = true

    fun play(songs: List<Song>, index: Int) {
        // Cada ocorrência recebe um ID, mesmo se a mesma música aparecer duas vezes.
        val queue = songs.map { it.copy(queueEntryId = UUID.randomUUID().toString()) }
        if (index !in queue.indices) return

        command {
            mutableState.value = PlaybackState(
                queue = queue,
                queueId = state.value.queueId + 1,
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

    fun jumpTo(entryId: String, queueId: Long) = command {
        val current = state.value
        if (current.queueId != queueId) return@command
        val index = current.queue.indexOfFirst { it.queueEntryId == entryId }
        if (index < 0) return@command
        startTrack(index)
    }

    fun removeFromQueue(entryId: String, queueId: Long) = command {
        val current = state.value
        if (current.queueId != queueId) return@command
        val removed = current.queue.indexOfFirst { it.queueEntryId == entryId }
        if (removed < 0) return@command
        val queue = current.queue.toMutableList().apply { removeAt(removed) }.toList()

        if (removed != current.index) {
            // Editar outra faixa não toca no VLC nem reinicia o áudio atual.
            mutableState.value = current.copy(
                queue = queue,
                index = if (removed < current.index) current.index - 1 else current.index,
            )
        } else if (removed < queue.size) {
            // A próxima ocupa o lugar da removida. Preserva a intenção de pausa.
            startTrack(removed, autoPlay = playWhenReady, queue = queue)
        } else {
            finishQueue()
        }
    }

    fun moveInQueue(entryId: String, targetId: String, queueId: Long) = command {
        val current = state.value
        if (current.queueId != queueId) return@command
        val from = current.queue.indexOfFirst { it.queueEntryId == entryId }
        val to = current.queue.indexOfFirst { it.queueEntryId == targetId }
        // Só as próximas faixas são arrastáveis; a atual fica no lugar.
        if (from <= current.index || to <= current.index || from == to) return@command
        val queue = current.queue.toMutableList()
        queue.add(to, queue.removeAt(from))
        mutableState.value = current.copy(queue = queue.toList())
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
            playWhenReady = !current.isPlaying
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

    private fun startTrack(index: Int, autoPlay: Boolean = true, queue: List<Song> = state.value.queue) {
        playbackJob?.cancel()
        stopMedia()
        seekTarget = null
        playWhenReady = autoPlay

        mutableState.value = state.value.copy(
            queue = queue,
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
        val trackId = state.value.trackId

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
                observePlayback(engine, trackId)

                val options = if (autoPlay) emptyArray() else arrayOf("start-paused")
                val accepted = if (localFile != null) {
                    // O VLC recebe o caminho real, com espaços e acentos decodificados.
                    engine.media().play(localFile.absolutePath, *options)
                } else {
                    engine.media().play(requireNotNull(input), *options)
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
                            // Mantém a consulta como apoio ao evento de término.
                            trackFinished(trackId)
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

    private fun observePlayback(engine: MediaPlayer, trackId: Long) {
        val listener = object : MediaPlayerEventAdapter() {
            override fun finished(mediaPlayer: MediaPlayer) {
                // O callback nativo só agenda trabalho; os comandos do VLC
                // ficam na nossa thread de áudio.
                command { trackFinished(trackId) }
            }

            override fun error(mediaPlayer: MediaPlayer) {
                command {
                    val current = state.value
                    if (current.trackId == trackId && current.song != null && current.error == null) {
                        fail(IllegalStateException("O VLC informou um erro."))
                    }
                }
            }
        }
        playbackEvents = listener
        engine.events().addMediaPlayerEventListener(listener)
    }

    private fun trackFinished(trackId: Long) {
        val current = state.value
        // Eventos repetidos ou atrasados da faixa anterior não pulam outra música.
        if (current.trackId != trackId || current.song == null || current.error != null) return
        media?.failure?.let {
            fail(IllegalStateException(it))
            return
        }

        if (current.hasNext) {
            println("[Player] Fim da faixa. Avançando na fila.")
            startTrack(current.index + 1)
        } else {
            println("[Player] Fim da fila.")
            finishQueue()
        }
    }

    private fun finishQueue() {
        val current = state.value
        playbackJob?.cancel()
        stopMedia()
        seekTarget = null
        playWhenReady = false
        mutableState.value = PlaybackState(
            queueId = current.queueId,
            trackId = current.trackId + 1,
            volume = current.volume,
        )
    }

    private fun stopMedia() {
        // Desliga os eventos antigos antes da parada ou troca de mídia.
        playbackEvents?.let { player?.events()?.removeMediaPlayerEventListener(it) }
        playbackEvents = null
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
