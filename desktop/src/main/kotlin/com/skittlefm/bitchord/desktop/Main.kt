package com.skittlefm.bitchord.desktop

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.music.bitchord.ui.theme.BitChordTheme

fun main() = application {
    Window(
        onCloseRequest = ::exitApplication,
        title = "BitChord Windows",
        state = rememberWindowState(
            width = 1100.dp,
            height = 740.dp,
        ),
    ) {
        BitChordTheme(darkTheme = true) {
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = MaterialTheme.colorScheme.background,
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "BitChord Windows",
                        style = MaterialTheme.typography.displayLarge,
                    )
                }
            }
        }
    }
}