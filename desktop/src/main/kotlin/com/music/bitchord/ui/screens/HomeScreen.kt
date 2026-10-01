package com.music.bitchord.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
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
import coil3.compose.LocalPlatformContext
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.music.bitchord.data.model.*
import com.music.bitchord.ui.components.*

@Composable
fun HomeScreen(
    state: UiState<HomeFeed>,
    listState: LazyListState,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            top = TopBarContentHeight + TopBarContentGap,
            bottom = 140.dp,
        ),
    ) {
        item(key = "home:title") {
            Text(
                text = "Ouvir agora",
                style = MaterialTheme.typography.displayLarge,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.padding(
                    horizontal = PAGE_GUTTER,
                    vertical = 8.dp,
                ),
            )
        }

        when (val result = state) {
            UiState.Loading -> feedSkeleton()

            is UiState.Error -> {
                item(key = "home:error") {
                    HomeMessage(
                        message = result.message,
                        onRetry = onRetry,
                    )
                }
            }

            is UiState.Success -> {
                val shelves = result.data.shelves.filter {
                    it.items.isNotEmpty()
                }

                if (shelves.isEmpty()) {
                    item(key = "home:empty") {
                        HomeMessage(
                            message = "Nenhum conteúdo disponível agora.",
                            onRetry = onRetry,
                        )
                    }
                } else {
                    itemsIndexed(
                        items = shelves,
                        key = { index, shelf ->
                            "home:shelf:$index:${shelf.title}"
                        },
                    ) { index, shelf ->
                        HomeShelfRow(
                            shelf = shelf,
                            hero = index == 0,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun HomeShelfRow(
    shelf: HomeShelf,
    hero: Boolean,
) {
    Column(modifier = Modifier.padding(bottom = 26.dp)) {
        Column(
            modifier = Modifier
                .padding(horizontal = PAGE_GUTTER, vertical = 10.dp)
                .fillMaxWidth(),
        ) {
            Text(
                text = shelf.title,
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )

            if (shelf.subtitle.isNotBlank()) {
                Text(
                    text = shelf.subtitle,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        BoxWithConstraints {
            val cardWidth = if (hero) {
                heroCardWidth(maxWidth)
            } else {
                SHELF_CARD_WIDTH
            }

            LazyRow(
                contentPadding = PaddingValues(horizontal = PAGE_GUTTER),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                items(shelf.items) { item ->
                    if (hero) {
                        HomeHeroCard(
                            item = item,
                            modifier = Modifier.width(cardWidth),
                        )
                    } else {
                        HomeShelfCard(
                            item = item,
                            modifier = Modifier.width(cardWidth),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun HomeHeroCard(
    item: ShelfItem,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(18.dp)

    Box(
        modifier = modifier
            .aspectRatio(HERO_CARD_RATIO)
            .clip(shape)
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.onBackground.copy(
                    alpha = 0.15f,
                ),
                shape = shape,
            )
            .background(MaterialTheme.colorScheme.surfaceVariant),
    ) {
        CoverImage(
            item = item,
            sizePx = HEADER_ART_PX,
            modifier = Modifier.fillMaxSize(),
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomStart)
                .background(
                    Brush.verticalGradient(
                        listOf(
                            Color.Transparent,
                            Color.Black.copy(alpha = 0.78f),
                        ),
                    ),
                )
                .padding(
                    start = 16.dp,
                    end = 16.dp,
                    top = 34.dp,
                    bottom = 14.dp,
                ),
        ) {
            Text(
                text = item.title,
                style = MaterialTheme.typography.titleLarge,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )

            if (item.subtitle.isNotBlank()) {
                Text(
                    text = item.subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.72f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun HomeShelfCard(
    item: ShelfItem,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(12.dp)

    Column(modifier = modifier) {
        CoverImage(
            item = item,
            sizePx = CARD_ART_PX,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(shape)
                .border(
                    width = 1.dp,
                    color = MaterialTheme.colorScheme.onBackground.copy(
                        alpha = 0.15f,
                    ),
                    shape = shape,
                )
                .background(MaterialTheme.colorScheme.surfaceVariant),
        )

        Spacer(Modifier.height(10.dp))

        Text(
            text = item.title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )

        Text(
            text = item.subtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun CoverImage(
    item: ShelfItem,
    sizePx: Int,
    modifier: Modifier,
) {
    val context = LocalPlatformContext.current
    val url = item.thumbnailUrl.artworkAt(sizePx)

    val request = remember(context, url) {
        ImageRequest.Builder(context)
            .data(url)
            .crossfade(200)
            .build()
    }

    AsyncImage(
        model = request,
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = modifier,
    )
}

@Composable
private fun HomeMessage(
    message: String,
    onRetry: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                horizontal = PAGE_GUTTER + 12.dp,
                vertical = 48.dp,
            ),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )

        Button(onClick = onRetry) {
            Text("Tentar novamente")
        }
    }
}