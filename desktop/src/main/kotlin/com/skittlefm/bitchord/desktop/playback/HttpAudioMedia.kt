package com.skittlefm.bitchord.desktop.playback

import com.metrolist.innertubex.extraction.ExtractedStream
import okhttp3.Call
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import uk.co.caprica.vlcj.media.callback.seekable.SeekableCallbackMedia
import java.io.IOException
import java.util.concurrent.atomic.AtomicReference

class HttpAudioMedia(
    private val stream: ExtractedStream,
    private val network: OkHttpClient,
) : SeekableCallbackMedia(64 * 1024) {

    private val size = requireNotNull(
        stream.contentLengthBytes?.takeIf { it > 0 }
            ?: stream.audioUrl.toHttpUrl().queryParameter("clen")
                ?.toLongOrNull()?.takeIf { it > 0 }
    ) { "O stream não informou o tamanho do áudio." }

    private val chunkSize =
        stream.rangeChunkSizeBytes.coerceIn(16_384L, 1_048_576L)

    private val activeCall = AtomicReference<Call?>()

    private var position = 0L
    private var blockStart = -1L
    private var block = ByteArray(0)

    @Volatile
    private var cancelled = false

    @Volatile
    var failure: String? = null
        private set

    override fun onGetSize(): Long = size

    @Synchronized
    override fun onOpen(): Boolean {
        position = 0
        blockStart = -1
        block = ByteArray(0)
        return !cancelled
    }

    @Synchronized
    override fun onRead(buffer: ByteArray, bufferSize: Int): Int {
        if (cancelled || position >= size) return -1

        try {
            if (position < blockStart || position >= blockStart + block.size) {
                block = fetchBlock(position)
                blockStart = position
            }

            val offset = (position - blockStart).toInt()
            val count = minOf(bufferSize, block.size - offset)

            block.copyInto(buffer, 0, offset, offset + count)
            position += count

            return count
        } catch (error: Exception) {
            if (!cancelled && failure == null) {
                failure = "Falha ao ler o áudio: ${error.javaClass.simpleName}."
            }

            throw IOException(failure ?: "Leitura cancelada.")
        }
    }

    private fun fetchBlock(start: Long): ByteArray {
        val end = start + minOf(chunkSize, size - start) - 1

        val request = Request.Builder()
            .url(stream.audioUrl)
            .apply {
                stream.headers.forEach { (name, value) ->
                    header(name, value)
                }

                header("Accept-Encoding", "identity")
                header("Range", "bytes=$start-$end")
            }
            .build()

        val call = network.newCall(request)
        activeCall.set(call)

        if (cancelled) call.cancel()

        try {
            return call.execute().use { response ->
                if (response.code == 200 && start == 0L && end == size - 1) {
                    return@use response.body.source().readByteArray(size)
                }

                if (response.code != 206) {
                    fail(
                        "HTTP ${response.code}: " +
                                "o servidor não entregou o bloco de áudio."
                    )
                }

                val range = Regex("bytes (\\d+)-(\\d+)/(\\d+)")
                    .matchEntire(response.header("Content-Range").orEmpty())
                    ?: fail("Resposta sem Content-Range válido.")

                val from = range.groupValues[1].toLong()
                val to = range.groupValues[2].toLong()
                val total = range.groupValues[3].toLong()

                if (from != start || to !in start..end || total != size) {
                    fail(
                        "O servidor devolveu um intervalo de áudio " +
                                "diferente do solicitado."
                    )
                }

                response.body.source().readByteArray(to - from + 1)
            }
        } finally {
            activeCall.compareAndSet(call, null)
        }
    }

    private fun fail(message: String): Nothing {
        failure = message
        throw IOException(message)
    }

    @Synchronized
    override fun onSeek(offset: Long): Boolean {
        if (cancelled || offset !in 0..size) return false

        position = offset
        return true
    }

    @Synchronized
    override fun onClose() {
        activeCall.get()?.cancel()
        block = ByteArray(0)
    }

    fun cancel() {
        cancelled = true
        activeCall.get()?.cancel()
    }
}