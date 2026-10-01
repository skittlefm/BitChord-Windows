package com.music.bitchord.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.materials.ExperimentalHazeMaterialsApi
import dev.chrisbanes.haze.materials.HazeMaterials
import kotlin.math.abs
import kotlin.math.roundToInt

data class BottomTab(
    val label: String,
    val icon: ImageVector,
)

private val GlassSpring = spring<Float>(
    dampingRatio = 0.72f,
    stiffness = 320f,
)

@OptIn(ExperimentalHazeMaterialsApi::class)
@Composable
fun FloatingBottomBar(
    tabs: List<BottomTab>,
    selectedIndex: Int,
    onTabSelected: (Int) -> Unit,
    hazeState: HazeState,
    modifier: Modifier = Modifier,
    reduceAnimation: Boolean = false,
    reduceDynamicBlur: Boolean = false,
) {
    if (tabs.isEmpty()) return

    val pillShape = RoundedCornerShape(percent = 50)
    val container = MaterialTheme.colorScheme.surface
    val density = LocalDensity.current

    val glassSpec: AnimationSpec<Float> =
        if (reduceAnimation) snap() else GlassSpring

    var dragOffset by remember { mutableFloatStateOf(0f) }
    var rowSize by remember { mutableStateOf(IntSize.Zero) }

    val currentSelectedIndex by rememberUpdatedState(selectedIndex)
    val currentOnTabSelected by rememberUpdatedState(onTabSelected)

    val gapPx = with(density) { 6.dp.toPx() }

    val tabWidthPx = if (rowSize.width > 0) {
        ((rowSize.width - gapPx * (tabs.size - 1)) / tabs.size)
            .coerceAtLeast(0f)
    } else {
        0f
    }

    val tabStepPx = if (rowSize.width > 0) {
        (rowSize.width + gapPx) / tabs.size
    } else {
        0f
    }

    val pillTargetPx = selectedIndex * tabStepPx + dragOffset

    val animatedPillOffset by animateFloatAsState(
        targetValue = pillTargetPx,
        animationSpec = glassSpec,
        label = "pillOffset",
    )

    val lag = if (tabStepPx > 0f) {
        (abs(pillTargetPx - animatedPillOffset) / tabStepPx)
            .coerceIn(0f, 1f)
    } else {
        0f
    }

    LaunchedEffect(selectedIndex) {
        dragOffset = 0f
    }

    Box(
        modifier = modifier
            .padding(horizontal = 10.dp)
            .padding(bottom = 2.dp)
            .fillMaxWidth()
            .clip(pillShape)
            .then(
                if (reduceDynamicBlur) {
                    Modifier.background(container)
                } else {
                    Modifier.optimizedHazeEffect(
                        state = hazeState,
                        style = HazeMaterials.regular(container),
                    )
                },
            )
            .border(
                width = 0.5.dp,
                color = Color.White.copy(alpha = 0.10f),
                shape = pillShape,
            )
            .padding(6.dp),
    ) {
        if (tabWidthPx > 0f) {
            Box(
                modifier = Modifier
                    .width(with(density) { tabWidthPx.toDp() })
                    .height(with(density) { rowSize.height.toDp() })
                    .graphicsLayer {
                        translationX = animatedPillOffset
                        scaleX = 1f + lag * 0.16f
                        scaleY = 1f - lag * 0.16f * 0.5f
                    }
                    .clip(pillShape)
                    .background(
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.14f),
                    ),
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .onSizeChanged { rowSize = it }
                .pointerInput(tabStepPx, tabs.size) {
                    if (tabStepPx <= 0f) return@pointerInput

                    var totalDrag = 0f

                    detectHorizontalDragGestures(
                        onDragStart = {
                            totalDrag = 0f
                        },
                        onDragCancel = {
                            dragOffset = 0f
                        },
                        onDragEnd = {
                            val ratio = totalDrag / tabStepPx

                            val shift = when {
                                ratio > 0.35f ->
                                    maxOf(1, ratio.roundToInt())

                                ratio < -0.35f ->
                                    minOf(-1, ratio.roundToInt())

                                else -> 0
                            }

                            val newIndex = (currentSelectedIndex + shift)
                                .coerceIn(0, tabs.lastIndex)

                            if (newIndex != currentSelectedIndex) {
                                currentOnTabSelected(newIndex)
                            }

                            dragOffset = 0f
                        },
                        onHorizontalDrag = { _, delta ->
                            totalDrag += delta

                            dragOffset = when {
                                totalDrag > 0f &&
                                        currentSelectedIndex == tabs.lastIndex ->
                                    totalDrag * 0.25f

                                totalDrag < 0f &&
                                        currentSelectedIndex == 0 ->
                                    totalDrag * 0.25f

                                else -> totalDrag
                            }
                        },
                    )
                },
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            tabs.forEachIndexed { index, tab ->
                BottomBarItem(
                    tab = tab,
                    selected = index == selectedIndex,
                    glassSpec = glassSpec,
                    reduceAnimation = reduceAnimation,
                    onClick = { onTabSelected(index) },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun BottomBarItem(
    tab: BottomTab,
    selected: Boolean,
    glassSpec: AnimationSpec<Float>,
    reduceAnimation: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scale by animateFloatAsState(
        targetValue = if (selected) 1.08f else 1f,
        animationSpec = glassSpec,
        label = "tabScale",
    )

    val tint by animateColorAsState(
        targetValue = if (selected) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
        animationSpec = if (reduceAnimation) snap() else tween(200),
        label = "tabTint",
    )

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .clip(RoundedCornerShape(percent = 50))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            )
            .padding(vertical = 9.dp),
    ) {
        Icon(
            imageVector = tab.icon,
            contentDescription = tab.label,
            tint = tint,
            modifier = Modifier
                .size(25.dp)
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                },
        )

        Spacer(Modifier.height(2.dp))

        Text(
            text = tab.label,
            style = MaterialTheme.typography.labelSmall,
            color = tint,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}