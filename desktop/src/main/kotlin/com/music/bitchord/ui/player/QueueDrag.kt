package com.music.bitchord.ui.player

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.music.bitchord.data.model.Song
import kotlin.math.abs

internal data class QueueTrack(val id: String, val song: Song)

/** Adaptação do arrasto da fila original, usando IDs em vez de índices mutáveis. */
@Composable
internal fun rememberQueueDrag(
    listState: LazyListState,
    queueId: Long,
    currentId: String,
    tracks: List<QueueTrack>,
    onMove: (String, String, Long) -> Unit,
): QueueDrag {
    // Trocar a fila ou a faixa atual encerra o gesto que estava em andamento.
    val drag = remember(listState, queueId, currentId) { QueueDrag(listState, queueId) }
    drag.rows = tracks
    drag.onMove = onMove
    with(LocalDensity.current) {
        drag.edgeZone = 40.dp.toPx()
        drag.edgeSpeed = 340.dp.toPx()
    }
    DisposableEffect(drag) { onDispose { drag.cancel() } }
    LaunchedEffect(drag, tracks) {
        val held = drag.held
        if (held != null && tracks.none { it.id == held.id }) drag.cancel()
    }

    // Perto das bordas, a lista rola sob a música segurada pelo mouse.
    val direction = drag.autoScrollDir
    LaunchedEffect(drag, direction) {
        if (direction == 0) return@LaunchedEffect
        listState.scroll {
            var previous = withFrameNanos { it }
            while (drag.held != null) {
                val now = withFrameNanos { it }
                val seconds = ((now - previous) / 1_000_000_000f).coerceAtMost(1f / 30f)
                previous = now
                if (scrollBy(drag.autoScrollSpeed * seconds) == 0f) break
                drag.scrolled()
            }
        }
    }
    return drag
}

internal class QueueDrag(private val listState: LazyListState, private val queueId: Long) {
    var rows: List<QueueTrack> = emptyList()
    var onMove: (String, String, Long) -> Unit = { _, _, _ -> }
    var edgeZone = 0f
    var edgeSpeed = 0f

    var held by mutableStateOf<QueueTrack?>(null)
        private set
    var heldTop by mutableFloatStateOf(0f)
        private set
    var autoScrollDir by mutableIntStateOf(0)
        private set
    var autoScrollSpeed = 0f
        private set

    private var heldCenter = 0f
    private var heldSize = 0
    private var awaitingIndex: Int? = null

    fun start(id: String) {
        val track = rows.firstOrNull { it.id == id } ?: return
        val item = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.key == id } ?: return
        cancel()
        heldSize = item.size
        heldCenter = item.offset + item.size / 2f
        heldTop = item.offset.toFloat()
        held = track
    }

    fun drag(deltaY: Float) = settle(deltaY)
    fun scrolled() = settle(0f)

    fun end(id: String) {
        if (held?.id == id) cancel()
    }

    fun cancel() {
        held = null
        awaitingIndex = null
        setAutoScroll(0f)
    }

    private fun settle(deltaY: Float) {
        val id = held?.id ?: return
        if (rows.none { it.id == id }) return cancel()
        val info = listState.layoutInfo
        val items = info.visibleItemsInfo
        val half = heldSize / 2f
        var center = heldCenter + deltaY
        rows.firstOrNull()?.let { first ->
            items.firstOrNull { it.key == first.id }?.let { center = center.coerceAtLeast(it.offset + half) }
        }
        rows.lastOrNull()?.let { last ->
            items.firstOrNull { it.key == last.id }?.let { center = center.coerceAtMost(it.offset + it.size - half) }
        }
        heldCenter = center
        val top = center - half
        val minTop = info.viewportStartOffset.toFloat()
        heldTop = top.coerceIn(minTop, (info.viewportEndOffset - heldSize).toFloat().coerceAtLeast(minTop))

        val dragged = items.firstOrNull { it.key == id } ?: return setAutoScroll(0f)
        aimAutoScroll(top, id)
        awaitingIndex?.let {
            // Aguarda o player publicar a troca antes de decidir a próxima.
            if (dragged.index != it) return
            awaitingIndex = null
        }
        val target = items
            .filter { item -> item.key != id && rows.any { it.id == item.key } }
            .minByOrNull { abs(it.offset + it.size / 2f - center) }
            ?: return
        if (abs(center - (target.offset + target.size / 2f)) > target.size / 2f) return
        if (target.index == listState.firstVisibleItemIndex && listState.canScrollBackward) return
        val targetId = rows.firstOrNull { it.id == target.key }?.id ?: return
        onMove(id, targetId, queueId)
        awaitingIndex = target.index
    }

    private fun aimAutoScroll(top: Float, id: String) {
        val info = listState.layoutInfo
        val speed = edgeScrollSpeed(
            top, top + heldSize, info.viewportStartOffset, info.viewportEndOffset, edgeZone, edgeSpeed,
        )
        val blocked = when {
            speed < 0f -> rows.firstOrNull()?.id == id || !listState.canScrollBackward
            speed > 0f -> rows.lastOrNull()?.id == id || !listState.canScrollForward
            else -> true
        }
        setAutoScroll(if (blocked) 0f else speed)
    }

    private fun setAutoScroll(speed: Float) {
        autoScrollSpeed = speed
        autoScrollDir = when {
            speed > 0f -> 1
            speed < 0f -> -1
            else -> 0
        }
    }
}

private fun edgeScrollSpeed(
    top: Float, bottom: Float, viewportStart: Int, viewportEnd: Int, zone: Float, speed: Float,
): Float {
    if (zone <= 0f) return 0f
    val intoStart = viewportStart + zone - top
    val intoEnd = bottom - (viewportEnd - zone)
    val reach = when {
        intoStart > 0f && intoEnd <= 0f -> -intoStart
        intoEnd > 0f && intoStart <= 0f -> intoEnd
        else -> return 0f
    }
    val ramp = speed * (0.2f + 0.8f * (abs(reach) / zone).coerceAtMost(1f))
    return if (reach < 0f) -ramp else ramp
}
