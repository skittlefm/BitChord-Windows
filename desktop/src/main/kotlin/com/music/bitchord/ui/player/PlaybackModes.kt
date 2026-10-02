package com.music.bitchord.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.music.bitchord.ui.icons.BitChordIcons
import com.skittlefm.bitchord.desktop.playback.PlaybackState
import com.skittlefm.bitchord.desktop.playback.RepeatMode

/** Cápsula e ícones do PlayerActionRow original, exibidos com a fila aberta. */
@Composable
internal fun PlaybackModes(
    state: PlaybackState,
    onToggleShuffle: () -> Unit,
    onCycleRepeat: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier.height(44.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.12f)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ModeSegment(
            description = "Embaralhar",
            status = if (state.shuffleEnabled) "Ativado" else "Desativado",
            clickLabel = if (state.shuffleEnabled) "Restaurar a ordem das próximas músicas" else "Embaralhar as próximas músicas",
            active = state.shuffleEnabled,
            icon = BitChordIcons.Shuffle,
            onClick = onToggleShuffle,
        )
        Box(Modifier.width(1.dp).fillMaxHeight().background(Color.White.copy(alpha = 0.20f)))
        ModeSegment(
            description = "Repetir",
            status = when (state.repeatMode) {
                RepeatMode.OFF -> "Desativado"
                RepeatMode.ALL -> "Repetir a fila"
                RepeatMode.ONE -> "Repetir uma música"
            },
            clickLabel = when (state.repeatMode) {
                RepeatMode.OFF -> "Repetir a fila"
                RepeatMode.ALL -> "Repetir uma música"
                RepeatMode.ONE -> "Desativar repetição"
            },
            active = state.repeatMode != RepeatMode.OFF,
            icon = if (state.repeatMode == RepeatMode.ONE) null else BitChordIcons.Repeat,
            onClick = onCycleRepeat,
        )
    }
}

@Composable
private fun ModeSegment(
    description: String,
    status: String,
    clickLabel: String,
    active: Boolean,
    icon: ImageVector?,
    onClick: () -> Unit,
) {
    Box(
        Modifier.width(52.dp).height(44.dp)
            .background(if (active) Color.White.copy(alpha = 0.14f) else Color.Transparent)
            .clickable(
                interactionSource = remember { MutableInteractionSource() }, indication = null,
                role = Role.Button, onClickLabel = clickLabel, onClick = onClick,
            )
            .semantics {
                contentDescription = description
                stateDescription = status
                selected = active
            },
        contentAlignment = Alignment.Center,
    ) {
        val tint = Color.White.copy(alpha = if (active) 1f else 0.75f)
        if (icon != null) {
            Icon(icon, null, Modifier.size(24.dp), tint = tint)
        } else {
            Text("1", color = tint, fontSize = 17.sp, fontWeight = FontWeight.Bold)
        }
    }
}
