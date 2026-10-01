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
import androidx.compose.foundation.lazy.rememberLazyListState
import com.music.bitchord.ui.components.FLOATING_BAR_MAX_WIDTH
import com.music.bitchord.ui.components.FrostedTopBar
import com.music.bitchord.ui.screens.HomeScreen
import com.music.bitchord.data.DebugLog
import com.music.bitchord.data.HomeRepository
import com.music.bitchord.data.innertube.Innertube
import com.music.bitchord.data.model.HomeFeed
import com.music.bitchord.data.model.UiState
import kotlinx.coroutines.CancellationException

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
    val homeListState = rememberLazyListState()
    var homeState by remember {
        mutableStateOf<UiState<HomeFeed>>(UiState.Loading)
    }

    var homeRequest by remember { mutableIntStateOf(0) }

    LaunchedEffect(homeRequest) {
        homeState = UiState.Loading

        try {
            homeState = UiState.Success(HomeRepository.home())
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            DebugLog.e("Home", "Falha ao carregar o feed", error)

            homeState = UiState.Error(
                "Não foi possível carregar o início. Tente novamente.",
            )
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            Innertube.close()
        }
    }

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
                .hazeSource(hazeState),
        ) {
            when (selectedTab) {
                0 -> HomeScreen(
                    state = homeState,
                    listState = homeListState,
                    onRetry = { homeRequest++ },
                )

                else -> Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(top = 64.dp, bottom = 96.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = tabs[selectedTab].label,
                        style = MaterialTheme.typography.displayLarge,
                    )
                }
            }
        }

        FrostedTopBar(
            hazeState = hazeState,
            modifier = Modifier.align(Alignment.TopCenter),
        )

        FloatingBottomBar(
            tabs = tabs,
            selectedIndex = selectedTab,
            onTabSelected = { selectedTab = it },
            hazeState = hazeState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .widthIn(max = FLOATING_BAR_MAX_WIDTH)
                .fillMaxWidth(),
        )
    }
}