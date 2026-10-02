package com.music.bitchord.ui.player

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.VolumeDown
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.*
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.*
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.compose.LocalPlatformContext
import coil3.request.ImageRequest
import com.music.bitchord.data.model.PLAYER_ART_PX
import com.music.bitchord.data.model.artworkAt
import com.music.bitchord.ui.icons.BitChordIcons
import com.skittlefm.bitchord.desktop.playback.PlaybackState
import kotlin.math.roundToInt

@Composable
fun NowPlayingScreen(
    state: PlaybackState,
    onClose: () -> Unit,
    onPlayPause: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onSeek: (Long, Long) -> Unit,
    onVolume: (Int) -> Unit,
    onQueueSelect: (Int, Long) -> Unit,
) {
    // Esta tela apenas lê o estado e envia comandos ao DesktopPlayer existente.
    val song = state.song ?: return
    val focus = remember { FocusRequester() }
    var queueOpen by rememberSaveable { mutableStateOf(false) }
    var scrub by remember(state.trackId) { mutableStateOf<Float?>(null) }
    val fraction = scrub ?: if (state.durationMs > 0) {
        state.positionMs.toFloat() / state.durationMs
    } else 0f
    val context = LocalPlatformContext.current
    val cover = song.thumbnailUrl.artworkAt(PLAYER_ART_PX)
    val backdrop = remember(context, cover) {
        ImageRequest.Builder(context).data(cover).size(128).build()
    }
    val artScale by animateFloatAsState(
        targetValue = when {
            !state.isPlaying -> 0.88f
            scrub != null -> 0.94f
            else -> 1f
        },
        animationSpec = tween(500, easing = CubicBezierEasing(0.215f, 0.61f, 0.355f, 1f)),
        label = "artworkScale",
    )

    LaunchedEffect(Unit) { focus.requestFocus() }
    LaunchedEffect(state.canSeek) { if (!state.canSeek) scrub = null }

    val queueAction: @Composable () -> Unit = {
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
            QueueToggle(open = queueOpen, onClick = { queueOpen = !queueOpen })
        }
    }

    val artwork: @Composable (Modifier) -> Unit = { modifier ->
        Box(
            modifier.graphicsLayer { scaleX = artScale; scaleY = artScale }
                .shadow(14.dp, RoundedCornerShape(10.dp))
                .clip(RoundedCornerShape(10.dp))
                .background(Color.Black.copy(alpha = 0.18f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(BitChordIcons.MusicNote, null, Modifier.size(64.dp), tint = Color.White.copy(alpha = 0.3f))
            AsyncImage(cover, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        }
    }

    val controls: @Composable (Boolean) -> Unit = { compact ->
        Column(Modifier.widthIn(max = 560.dp).fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (song.isExplicit == true) {
                    Text(
                        "E", style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.border(1.dp, Color.White, RoundedCornerShape(2.dp))
                            .padding(horizontal = 3.dp),
                    )
                    Spacer(Modifier.width(6.dp))
                }
                Text(
                    song.title, color = Color.White,
                    style = MaterialTheme.typography.titleLarge, maxLines = 1,
                    modifier = Modifier.weight(1f).basicMarquee(),
                )
            }
            Spacer(Modifier.height(2.dp))
            Text(
                song.artist, color = Color.White.copy(alpha = 0.55f),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium, maxLines = 1,
                modifier = Modifier.fillMaxWidth().basicMarquee(),
            )
            Spacer(Modifier.height(if (compact) 2.dp else 10.dp))

            key(state.trackId) {
                ThinSlider(
                    value = fraction.coerceIn(0f, 1f),
                    enabled = state.canSeek,
                    loading = state.isLoading,
                    onValueChange = { scrub = it },
                    onValueChangeFinished = {
                        scrub?.let { onSeek((it * state.durationMs).toLong(), state.trackId) }
                        scrub = null
                    },
                    modifier = Modifier.semantics { contentDescription = "Posição da música" },
                )
            }
            val displayedMs = (fraction * state.durationMs).toLong()
            Row(Modifier.fillMaxWidth().offset(y = (-9).dp), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(playerTime(displayedMs), color = Color.White.copy(alpha = 0.55f), style = MaterialTheme.typography.labelMedium)
                Text(
                    if (state.durationMs > 0) "-${playerTime(state.durationMs - displayedMs)}" else "--:--",
                    color = Color.White.copy(alpha = 0.55f), style = MaterialTheme.typography.labelMedium,
                )
            }
            Spacer(Modifier.height(if (compact) 0.dp else 8.dp))

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceAround, verticalAlignment = Alignment.CenterVertically) {
                TransportGlyph(
                    "previous", "Música anterior", onPrevious,
                    glyph = if (compact) 44.dp else 53.dp, touch = 53.dp,
                    enabled = !state.isLoading, scaleY = 0.85f,
                )
                Box(Modifier.size(if (compact) 76.dp else 92.dp), contentAlignment = Alignment.Center) {
                    if (state.isLoading) {
                        CircularProgressIndicator(Modifier.size(if (compact) 30.dp else 38.dp), color = Color.White, strokeWidth = 3.dp)
                    } else {
                        TransportGlyph(
                            if (state.isPlaying) "pause" else "play",
                            when {
                                state.error != null -> "Tentar novamente"
                                state.isPlaying -> "Pausar"
                                else -> "Reproduzir"
                            },
                            onPlayPause,
                            glyph = if (compact) 58.dp else 74.dp,
                            touch = if (compact) 76.dp else 92.dp,
                        )
                    }
                }
                TransportGlyph(
                    "next", "Próxima música", onNext,
                    glyph = if (compact) 44.dp else 53.dp, touch = 53.dp,
                    enabled = state.hasNext, scaleY = 0.85f,
                )
            }
            Spacer(Modifier.height(if (compact) 0.dp else 12.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.AutoMirrored.Rounded.VolumeDown, null, Modifier.size(20.dp), tint = Color.White.copy(alpha = 0.5f))
                Spacer(Modifier.width(10.dp))
                ThinSlider(
                    value = state.volume / 100f,
                    onValueChange = { onVolume((it * 100).roundToInt()) },
                    idleHeight = 6.dp, activeHeight = 10.dp,
                    modifier = Modifier.weight(1f).semantics { contentDescription = "Volume" },
                )
                Spacer(Modifier.width(10.dp))
                Icon(Icons.AutoMirrored.Rounded.VolumeUp, null, Modifier.size(20.dp), tint = Color.White.copy(alpha = 0.5f))
            }
        }
    }

    BoxWithConstraints(
        modifier = Modifier.fillMaxSize().background(Color.Black).clipToBounds()
            .pointerInput(Unit) { detectTapGestures(onTap = {}) }
            .focusRequester(focus)
            .onPreviewKeyEvent {
                if (it.type == KeyEventType.KeyDown && it.key == Key.Escape) {
                    if (queueOpen) queueOpen = false else onClose()
                    true
                } else false
            }
            .focusable(),
        contentAlignment = Alignment.Center,
    ) {
        AsyncImage(
            backdrop, null, contentScale = ContentScale.Crop,
            modifier = Modifier.matchParentSize()
                .graphicsLayer { scaleX = 1.2f; scaleY = 1.2f }.blur(48.dp),
        )
        Box(Modifier.matchParentSize().background(Brush.verticalGradient(
            0f to Color.Black.copy(alpha = 0.34f),
            0.55f to Color.Black.copy(alpha = 0.48f),
            1f to Color.Black.copy(alpha = 0.64f),
        )))

        val compact = maxHeight < 440.dp
        val gutter = if (compact) 20.dp else 30.dp
        // Os limites e as duas colunas seguem o layout horizontal do BitChord.
        if (maxWidth > maxHeight && maxWidth >= 560.dp) {
            Row(Modifier.widthIn(max = 1100.dp).fillMaxSize().padding(top = 24.dp, bottom = if (compact) 8.dp else 20.dp)) {
                Column(
                    Modifier.weight(1f).fillMaxHeight().padding(horizontal = gutter),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    BoxWithConstraints(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                        artwork(Modifier.size(minOf(maxWidth, maxHeight)))
                    }
                    Spacer(Modifier.height(if (compact) 12.dp else 24.dp))
                    queueAction()
                }
                AnimatedContent(
                    targetState = queueOpen,
                    transitionSpec = { fadeIn(tween(220, delayMillis = 90)) togetherWith fadeOut(tween(140)) },
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                    label = "playerQueuePane",
                ) { showQueue ->
                    if (showQueue) {
                        PlayerQueue(
                            state, onQueueSelect,
                            Modifier.fillMaxSize().padding(horizontal = gutter),
                        )
                    } else {
                        Box(
                            Modifier.fillMaxSize().padding(horizontal = gutter)
                                .verticalScroll(rememberScrollState()),
                            contentAlignment = Alignment.Center,
                        ) { controls(compact) }
                    }
                }
            }
        } else if (queueOpen) {
            // Em uma janela estreita a fila ocupa o espaço da capa,
            // mantendo os controles de reprodução abaixo, como no original.
            val paneHeight = maxHeight.coerceAtLeast(480.dp)
            val compactPortrait = maxHeight < 650.dp
            Column(
                Modifier.widthIn(max = 620.dp).fillMaxSize()
                    .verticalScroll(rememberScrollState()),
            ) {
                Column(Modifier.fillMaxWidth().height(paneHeight).padding(horizontal = gutter, vertical = 32.dp)) {
                    PlayerQueue(state, onQueueSelect, Modifier.weight(1f).fillMaxWidth())
                    Spacer(Modifier.height(12.dp))
                    controls(compactPortrait)
                    Spacer(Modifier.height(12.dp))
                    queueAction()
                }
            }
        } else {
            val artSize = minOf(maxWidth - gutter * 2, (maxHeight - 386.dp).coerceAtLeast(150.dp), 560.dp)
                .coerceAtLeast(1.dp)
            Column(
                Modifier.widthIn(max = 620.dp).fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = gutter, vertical = 32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                artwork(Modifier.size(artSize))
                Spacer(Modifier.height(24.dp))
                controls(false)
                Spacer(Modifier.height(12.dp))
                queueAction()
            }
        }

        Box(
            Modifier.align(Alignment.TopCenter).size(width = 96.dp, height = 32.dp)
                .clickable(onClickLabel = "Recolher player", onClick = onClose),
            contentAlignment = Alignment.TopCenter,
        ) {
            Box(Modifier.padding(top = 8.dp).size(width = 38.dp, height = 5.dp)
                .shadow(2.dp, RoundedCornerShape(3.dp))
                .background(Color.White.copy(alpha = 0.7f), RoundedCornerShape(3.dp)))
        }
    }
}

@Composable
private fun QueueToggle(open: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.size(44.dp).clip(CircleShape)
            .background(if (open) Color.White.copy(alpha = 0.20f) else Color.Transparent)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null, role = Role.Button, onClick = onClick,
            )
            .semantics { selected = open },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            BitChordIcons.Queue, if (open) "Fechar fila" else "Abrir fila",
            Modifier.size(26.dp), tint = Color.White.copy(alpha = if (open) 1f else 0.75f),
        )
    }
}

@Suppress("DEPRECATION")
@Composable
private fun TransportGlyph(
    name: String,
    description: String,
    onClick: () -> Unit,
    glyph: Dp,
    touch: Dp,
    enabled: Boolean = true,
    scaleY: Float = 1f,
) {
    Box(
        Modifier.size(touch).clickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = null, enabled = enabled, onClick = onClick,
        ), contentAlignment = Alignment.Center,
    ) {
        Icon(
            painterResource("drawable/ic_player_$name.xml"), description,
            modifier = Modifier.size(glyph).graphicsLayer { this.scaleY = scaleY },
            tint = Color.White.copy(alpha = if (enabled) 1f else 0.3f),
        )
    }
}

private fun playerTime(ms: Long): String {
    val seconds = ms.coerceAtLeast(0) / 1_000
    return "${seconds / 60}:${(seconds % 60).toString().padStart(2, '0')}"
}
