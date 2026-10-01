package com.music.bitchord.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.music.bitchord.data.DebugLog
import com.music.bitchord.data.DetailRepository
import com.music.bitchord.data.model.*
import com.music.bitchord.ui.components.PAGE_GUTTER
import com.music.bitchord.ui.components.songListSkeleton
import kotlinx.coroutines.CancellationException
import androidx.compose.foundation.clickable
import androidx.compose.ui.unit.Dp

@Composable
fun DetailScreen(
    item: ShelfItem,
    onPlay: (List<Song>, Int) -> Unit,
    bottomPadding: Dp = 140.dp,
    modifier: Modifier = Modifier,
) {
    val browseId = requireNotNull(item.browseId)

    var page by remember(browseId) {
        mutableStateOf<DetailRepository.SongPage?>(null)
    }
    var loading by remember(browseId) { mutableStateOf(true) }
    var errorMessage by remember(browseId) {
        mutableStateOf<String?>(null)
    }
    var request by remember(browseId) { mutableIntStateOf(0) }

    LaunchedEffect(browseId, request) {
        loading = true
        errorMessage = null

        try {
            val previous = page

            val received = if (previous == null) {
                DetailRepository.browseSongs(browseId)
            } else {
                val token = previous.continuation
                    ?: return@LaunchedEffect

                DetailRepository.moreSongs(token)
            }

            page = if (previous == null) {
                received
            } else {
                previous.copy(
                    songs = (previous.songs + received.songs)
                        .distinctBy { it.videoId },
                    continuation = received.continuation,
                    suggested = (previous.suggested + received.suggested)
                        .distinctBy { it.videoId },
                )
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            DebugLog.e("Detail", "Falha ao carregar as faixas", error)
            errorMessage =
                "Não foi possível carregar as músicas. Tente novamente."
        } finally {
            loading = false
        }
    }

    val current = page
    val songs = current?.songs.orEmpty()
    val title = current?.header?.title ?: item.title
    val subtitle = current?.header?.subtitle ?: item.subtitle
    val cover = current?.header?.thumbnailUrl ?: item.thumbnailUrl
    val numbered = browseId.startsWith("MPREb")
    val background = MaterialTheme.colorScheme.background

    BoxWithConstraints(modifier.fillMaxSize()) {
        val artHeight = minOf(maxWidth / 0.92f, maxHeight * 0.6f)

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = bottomPadding),
        ) {
            item(key = "detail:header") {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(artHeight + 44.dp),
                ) {
                    AsyncImage(
                        model = cover.artworkAt(HEADER_ART_PX),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(artHeight)
                            .background(
                                MaterialTheme.colorScheme.surfaceVariant,
                            ),
                    )

                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(artHeight)
                            .background(
                                Brush.verticalGradient(
                                    0.55f to Color.Transparent,
                                    1f to background,
                                ),
                            ),
                    )

                    Column(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp, vertical = 14.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.headlineMedium,
                            textAlign = TextAlign.Center,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )

                        if (subtitle.isNotBlank()) {
                            Spacer(Modifier.height(5.dp))
                            Text(
                                text = subtitle,
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }

            if (current == null && loading) {
                songListSkeleton()
            }

            itemsIndexed(
                items = songs,
                key = { index, song -> "detail:$index:${song.videoId}" },
            ) { index, song ->
                DetailSongRow(
                    song = song,
                    trackNumber = if (numbered) index + 1 else null,
                    fallbackCover = cover,
                    onClick = {
                        onPlay(
                            songs.map {
                                it.copy(thumbnailUrl = it.thumbnailUrl ?: cover)
                            },
                            index,
                        )
                    },
                )
            }

            item(key = "detail:footer") {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    errorMessage?.let { message ->
                        Text(
                            text = message,
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }

                    when {
                        loading && current != null -> {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                strokeWidth = 2.dp,
                            )
                        }

                        !loading && (
                                errorMessage != null ||
                                        current?.continuation != null
                                ) -> {
                            TextButton(
                                onClick = {
                                    loading = true
                                    request++
                                },
                            ) {
                                Text(
                                    if (errorMessage != null) {
                                        "Tentar novamente"
                                    } else {
                                        "Carregar mais"
                                    },
                                )
                            }
                        }

                        !loading && songs.isEmpty() -> {
                            Text(
                                text = "Esta lista está vazia.",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DetailSongRow(
    song: Song,
    trackNumber: Int?,
    fallbackCover: String?,
    onClick: () -> Unit,
) {
    val secondary = MaterialTheme.colorScheme.onSurfaceVariant

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = PAGE_GUTTER, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (trackNumber != null) {
            Box(
                modifier = Modifier.size(52.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = trackNumber.toString(),
                    style = MaterialTheme.typography.bodyLarge,
                    color = secondary,
                )
            }
        } else {
            val shape = RoundedCornerShape(8.dp)

            AsyncImage(
                model = (song.thumbnailUrl ?: fallbackCover)
                    .artworkAt(ROW_ART_PX),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(52.dp)
                    .clip(shape)
                    .border(
                        1.dp,
                        MaterialTheme.colorScheme.onBackground.copy(alpha = 0.15f),
                        shape,
                    )
                    .background(MaterialTheme.colorScheme.surfaceVariant),
            )
        }

        Spacer(Modifier.width(14.dp))

        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (song.isExplicit == true) {
                    Text(
                        text = "E",
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier
                            .border(
                                1.dp,
                                MaterialTheme.colorScheme.onBackground.copy(
                                    alpha = 0.72f,
                                ),
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

            Spacer(Modifier.height(2.dp))

            Text(
                text = song.artist,
                style = MaterialTheme.typography.bodyMedium,
                color = secondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        song.durationText?.let { duration ->
            Spacer(Modifier.width(8.dp))
            Text(
                text = duration,
                style = MaterialTheme.typography.labelMedium,
                color = secondary,
            )
        }
    }
}