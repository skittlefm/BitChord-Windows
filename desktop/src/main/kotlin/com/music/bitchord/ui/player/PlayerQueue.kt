package com.music.bitchord.ui.player

import androidx.compose.foundation.*
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DragHandle
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.IntOffset
import coil3.compose.AsyncImage
import com.music.bitchord.data.model.ROW_ART_PX
import com.music.bitchord.data.model.Song
import com.music.bitchord.data.model.artworkAt
import com.music.bitchord.ui.icons.BitChordIcons
import com.skittlefm.bitchord.desktop.playback.PlaybackState
import kotlin.math.roundToInt

/** Fila dentro do player, com as medidas e a tipografia do BitChord original. */
@Composable
internal fun PlayerQueue(
    state: PlaybackState,
    onSelect: (String, Long) -> Unit,
    onRemove: (String, Long) -> Unit,
    onMove: (String, String, Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val currentSong = state.song ?: return
    val currentId = currentSong.queueEntryId ?: return
    val listState = rememberLazyListState()
    val queueId = state.queueId
    val tracks = remember(state.queue, state.index) {
        state.queue.drop(state.index + 1).mapNotNull { song ->
            song.queueEntryId?.let { QueueTrack(it, song) }
        }
    }
    val drag = rememberQueueDrag(listState, queueId, currentId, tracks, onMove)
    val contextSong = tracks.firstOrNull()?.song ?: currentSong
    val source = contextSong.playbackSource?.takeIf { it.isNotBlank() }
        ?: contextSong.albumName?.takeIf { it.isNotBlank() }

    // Editar a ordem não desloca a lista. Trocar a música atual volta ao início.
    LaunchedEffect(queueId, currentId) { listState.scrollToItem(0) }

    Column(modifier) {
        Text("Fila", style = MaterialTheme.typography.titleLarge, color = Color.White)
        Spacer(Modifier.height(4.dp))
        Box(Modifier.weight(1f).fillMaxWidth()) {
            Box(Modifier.fillMaxSize().padding(end = 10.dp).queueFadingEdges()) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(top = 12.dp, bottom = 28.dp),
                ) {
                    item(key = "current-heading", contentType = "heading") {
                        QueueHeading("Tocando agora", Modifier.padding(bottom = 6.dp))
                    }
                    item(key = currentId, contentType = "track") {
                        QueueRow(
                            song = currentSong,
                            currentState = state,
                            onClick = { onSelect(currentId, queueId) },
                            onRemove = { onRemove(currentId, queueId) },
                        )
                    }
                    if (tracks.isNotEmpty()) {
                        item(key = "upcoming-heading", contentType = "heading") {
                            QueueHeading(
                                if (source == null) "A seguir" else "A seguir: $source",
                                Modifier.padding(top = 16.dp, bottom = 6.dp),
                            )
                        }
                        items(tracks, key = { it.id }, contentType = { "track" }) { track ->
                            val held = drag.held?.id == track.id
                            if (held) {
                                DisposableEffect(drag, track.id) { onDispose { drag.end(track.id) } }
                            }
                            QueueRow(
                                song = track.song,
                                currentState = null,
                                onClick = { onSelect(track.id, queueId) },
                                onRemove = { onRemove(track.id, queueId) },
                                handle = { DragHandle(track, drag) },
                                modifier = if (held) Modifier.alpha(0f) else Modifier.animateItem(
                                    fadeInSpec = null, fadeOutSpec = null,
                                    placementSpec = tween(200, easing = FastOutSlowInEasing),
                                ),
                            )
                        }
                    } else {
                        item(key = "end", contentType = "heading") {
                            Text(
                                "Fim da fila",
                                color = Color.White.copy(alpha = 0.55f),
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.padding(top = 16.dp),
                            )
                        }
                    }
                }
                // A cópia acompanha o mouse, enquanto a vaga permanece na lista.
                drag.held?.let { held ->
                    QueueRow(
                        song = held.song, currentState = null, onClick = {}, onRemove = {},
                        handle = { DragHandle(held, drag, interactive = false) },
                        modifier = Modifier.offset { IntOffset(0, drag.heldTop.roundToInt()) }
                            .clearAndSetSemantics {},
                        enabled = false, lifted = true,
                    )
                }
            }
            VerticalScrollbar(
                adapter = rememberScrollbarAdapter(listState),
                modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight().width(4.dp),
                style = LocalScrollbarStyle.current.copy(
                    unhoverColor = Color.White.copy(alpha = 0.18f),
                    hoverColor = Color.White.copy(alpha = 0.45f),
                ),
            )
        }
    }
}

@Composable
private fun DragHandle(track: QueueTrack, drag: QueueDrag, interactive: Boolean = true) {
    Icon(
        Icons.Rounded.DragHandle,
        if (interactive) "Arrastar ${track.song.title} para reordenar" else null,
        tint = Color.White.copy(alpha = 0.4f),
        modifier = Modifier.size(20.dp).offset(x = (-4).dp).then(
            if (interactive) Modifier.pointerInput(drag, track.id) {
                detectDragGestures(
                    onDragStart = { drag.start(track.id) },
                    onDragEnd = { drag.end(track.id) },
                    onDragCancel = { drag.end(track.id) },
                    onDrag = { change, amount -> change.consume(); drag.drag(amount.y) },
                )
            } else Modifier,
        ),
    )
    Spacer(Modifier.width(4.dp))
}

@Composable
private fun QueueHeading(title: String, modifier: Modifier = Modifier) {
    Text(
        title, modifier = modifier.fillMaxWidth(),
        style = MaterialTheme.typography.titleMedium,
        color = Color.White.copy(alpha = 0.75f),
        maxLines = 2, overflow = TextOverflow.Ellipsis,
    )
}

@Composable
private fun QueueRow(
    song: Song,
    currentState: PlaybackState?,
    onClick: () -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
    handle: (@Composable () -> Unit)? = null,
    enabled: Boolean = true,
    lifted: Boolean = false,
) {
    val isCurrent = currentState != null
    val status = when {
        currentState == null -> null
        currentState.error != null -> "Falha na reprodução"
        currentState.isLoading -> "Carregando"
        currentState.isPlaying -> "Reproduzindo"
        else -> "Parado ou pausado"
    }
    val artShape = RoundedCornerShape(6.dp)
    Row(
        modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp))
            .background(if (lifted) Color.White.copy(alpha = 0.06f) else Color.Transparent)
            .clickable(enabled = enabled, onClickLabel = "Reproduzir ${song.title}", onClick = onClick)
            .semantics {
                selected = isCurrent
                if (status != null) stateDescription = status
            }
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        handle?.invoke()
        Box(
            Modifier.size(44.dp).clip(artShape)
                .background(Color.White.copy(alpha = 0.08f))
                .border(0.5.dp, Color.White.copy(alpha = 0.10f), artShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(BitChordIcons.MusicNote, null, Modifier.size(24.dp), tint = Color.White.copy(alpha = 0.3f))
            AsyncImage(
                song.thumbnailUrl.artworkAt(ROW_ART_PX), null,
                Modifier.fillMaxSize(), contentScale = ContentScale.Crop,
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (song.isExplicit == true) {
                    Text(
                        "E", style = MaterialTheme.typography.labelSmall, color = Color.White,
                        modifier = Modifier.border(1.dp, Color.White, RoundedCornerShape(2.dp))
                            .padding(horizontal = 3.dp),
                    )
                    Spacer(Modifier.width(6.dp))
                }
                Text(
                    song.title, style = MaterialTheme.typography.titleMedium,
                    color = Color.White.copy(alpha = if (isCurrent) 1f else 0.92f),
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                song.artist, style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.55f),
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
        if (currentState != null) {
            Spacer(Modifier.width(10.dp))
            if (currentState.isLoading) {
                CircularProgressIndicator(Modifier.size(18.dp), color = Color.White, strokeWidth = 2.dp)
            } else {
                Icon(
                    when {
                        currentState.error != null -> Icons.Rounded.ErrorOutline
                        currentState.isPlaying -> Icons.Rounded.GraphicEq
                        else -> Icons.Rounded.Pause
                    },
                    "Faixa atual", Modifier.size(18.dp), tint = Color.White,
                )
            }
            Spacer(Modifier.width(10.dp))
        }
        Box(
            Modifier.size(32.dp).clip(CircleShape)
                .clickable(enabled = enabled, role = Role.Button, onClick = onRemove),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Rounded.Close, "Remover ${song.title} da fila",
                Modifier.size(18.dp), tint = Color.White.copy(alpha = 0.55f),
            )
        }
    }
}

/** O mesmo degradê suave nas bordas usado pela fila original. */
private fun Modifier.queueFadingEdges(): Modifier = this
    .graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)
    .drawWithContent {
        drawContent()
        val fade = minOf(28.dp.toPx(), size.height / 2)
        drawRect(
            Brush.verticalGradient(listOf(Color.Transparent, Color.Black), 0f, fade),
            blendMode = BlendMode.DstIn,
        )
        drawRect(
            Brush.verticalGradient(listOf(Color.Black, Color.Transparent), size.height - fade, size.height),
            blendMode = BlendMode.DstIn,
        )
    }
