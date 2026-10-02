package com.music.bitchord.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.music.bitchord.data.model.ROW_ART_PX
import com.music.bitchord.data.model.artworkAt
import com.skittlefm.bitchord.desktop.playback.PlaybackState
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.materials.ExperimentalHazeMaterialsApi
import dev.chrisbanes.haze.materials.HazeMaterials
import androidx.compose.foundation.clickable

@OptIn(ExperimentalHazeMaterialsApi::class)
@Composable
fun MiniPlayer(
    state: PlaybackState,
    hazeState: HazeState,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onExpand: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val song = state.song ?: return
    val colors = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(percent = 50)
    val artShape = RoundedCornerShape(8.dp)

    Row(
        modifier = modifier
            .padding(horizontal = PAGE_GUTTER)
            .clip(shape)
            .optimizedHazeEffect(
                state = hazeState,
                style = HazeMaterials.thin(colors.surface),
            )
            .border(0.5.dp, Color.White.copy(alpha = 0.10f), shape)
            .clickable(
                interactionSource = null,
                indication = null,
                onClickLabel = "Abrir player",
                onClick = onExpand,
            )
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AsyncImage(
            model = song.thumbnailUrl.artworkAt(ROW_ART_PX),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(40.dp)
                .clip(artShape)
                .border(
                    1.dp,
                    colors.onBackground.copy(alpha = 0.15f),
                    artShape,
                )
                .background(colors.surfaceVariant),
        )

        Spacer(Modifier.width(10.dp))

        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (song.isExplicit == true) {
                    Text(
                        text = "E",
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier
                            .border(
                                1.dp,
                                colors.onBackground.copy(alpha = 0.72f),
                                RoundedCornerShape(2.dp),
                            )
                            .padding(horizontal = 3.dp),
                    )
                    Spacer(Modifier.width(6.dp))
                }

                Text(
                    text = song.title,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
            }

            Text(
                text = song.artist,
                style = MaterialTheme.typography.bodyMedium,
                color = colors.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        if (state.isLoading) {
            Box(
                modifier = Modifier.size(40.dp),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(22.dp),
                    color = colors.onBackground,
                    strokeWidth = 2.dp,
                )
            }
        } else {
            IconButton(
                onClick = onPlayPause,
                modifier = Modifier.size(40.dp),
            ) {
                Icon(
                    imageVector = if (state.isPlaying) {
                        Icons.Rounded.Pause
                    } else {
                        Icons.Rounded.PlayArrow
                    },
                    contentDescription = when {
                        state.error != null -> "Tentar novamente"
                        state.isPlaying -> "Pausar"
                        else -> "Reproduzir"
                    },
                    tint = colors.onBackground,
                    modifier = Modifier.size(32.dp),
                )
            }
        }

        Spacer(Modifier.width(8.dp))

        IconButton(
            onClick = onNext,
            enabled = state.hasNext,
            modifier = Modifier.size(40.dp),
        ) {
            Icon(
                imageVector = Icons.Rounded.SkipNext,
                contentDescription = "Próxima música",
                tint = colors.onBackground.copy(
                    alpha = if (state.hasNext) 1f else 0.3f,
                ),
                modifier = Modifier.size(32.dp),
            )
        }
    }
}