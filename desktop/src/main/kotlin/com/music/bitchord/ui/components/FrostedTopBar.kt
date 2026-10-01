package com.music.bitchord.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.materials.ExperimentalHazeMaterialsApi
import dev.chrisbanes.haze.materials.HazeMaterials

@Suppress("DEPRECATION")
@OptIn(ExperimentalHazeMaterialsApi::class)
@Composable
fun FrostedTopBar(
    hazeState: HazeState,
    modifier: Modifier = Modifier,
    reduceDynamicBlur: Boolean = false,
    actions: @Composable RowScope.() -> Unit = {},
) {
    val container = MaterialTheme.colorScheme.surface

    Column(modifier = modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(TopBarContentHeight)
                .then(
                    if (reduceDynamicBlur) {
                        Modifier.background(container)
                    } else {
                        Modifier.optimizedHazeEffect(
                            state = hazeState,
                            style = HazeMaterials.regular(container),
                        )
                    },
                ),
        ) {
            Image(
                painter = painterResource("drawable/ic_logo.xml"),
                contentDescription = "BitChord",
                colorFilter = ColorFilter.tint(
                    MaterialTheme.colorScheme.onSurface,
                ),
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .padding(start = 16.dp)
                    .height(18.dp),
            )

            Row(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                content = actions,
            )
        }

        HorizontalDivider(
            thickness = 0.5.dp,
            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.6f),
        )
    }
}