package com.skittlefm.bitchord.desktop

import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.music.bitchord.ui.components.BottomTab
import com.music.bitchord.ui.components.FloatingBottomBar
import com.music.bitchord.ui.icons.BitChordIcons
import com.music.bitchord.ui.theme.BitChordTheme
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource

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
                DesktopApp()
            }
        }
    }
}

@Composable
private fun DesktopApp() {
    var selectedTab by remember { mutableIntStateOf(0) }
    val hazeState = remember { HazeState() }

    val tabs = remember {
        listOf(
            BottomTab("Início", BitChordIcons.Home),
            BottomTab("Explorar", BitChordIcons.Explore),
            BottomTab("Biblioteca", BitChordIcons.Library),
            BottomTab("Buscar", BitChordIcons.Search),
        )
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .hazeSource(hazeState)
                .padding(bottom = 96.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = tabs[selectedTab].label,
                style = MaterialTheme.typography.displayLarge,
            )
        }

        FloatingBottomBar(
            tabs = tabs,
            selectedIndex = selectedTab,
            onTabSelected = { selectedTab = it },
            hazeState = hazeState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .widthIn(max = 440.dp)
                .fillMaxWidth(),
        )
    }
}