package com.music.bitchord.ui.components

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

val PAGE_GUTTER = 10.dp
val FLOATING_BAR_MAX_WIDTH = 440.dp
val SHELF_CARD_WIDTH = 150.dp

val TopBarContentHeight = 52.dp
val TopBarContentGap = 12.dp

const val HERO_CARD_RATIO = 0.92f

fun heroCardWidth(available: Dp): Dp =
    minOf(available * 0.70f, 320.dp)

fun trackColumnWidth(available: Dp): Dp =
    minOf(available * 0.88f, 400.dp)