package com.music.bitchord.ui.screens

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.music.bitchord.ui.components.PAGE_GUTTER
import com.music.bitchord.ui.components.TopBarContentGap
import com.music.bitchord.ui.components.TopBarContentHeight
import com.music.bitchord.ui.components.feedSkeleton

@Composable
fun HomeScreen(
    listState: LazyListState,
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

        // Exibe o estado de carregamento original nesta etapa da migração.
        feedSkeleton()
    }
}