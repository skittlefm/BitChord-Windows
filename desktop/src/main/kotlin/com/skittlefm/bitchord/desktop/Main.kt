package com.skittlefm.bitchord.desktop

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.*
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.music.bitchord.data.DebugLog
import com.music.bitchord.data.HomeRepository
import com.music.bitchord.data.innertube.Innertube
import com.music.bitchord.data.model.HomeFeed
import com.music.bitchord.data.model.ShelfItem
import com.music.bitchord.data.model.UiState
import com.music.bitchord.ui.components.*
import com.music.bitchord.ui.icons.BitChordIcons
import com.music.bitchord.ui.screens.DetailScreen
import com.music.bitchord.ui.screens.HomeScreen
import com.music.bitchord.ui.theme.BitChordTheme
import com.music.bitchord.ui.player.NowPlayingScreen
import com.skittlefm.bitchord.desktop.playback.DesktopPlayer
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.awt.Desktop
import java.net.URI
import java.net.URLEncoder

fun main() = application {
    val player = remember { DesktopPlayer() }
    val scope = rememberCoroutineScope()
    var closing by remember { mutableStateOf(false) }

    Window(
        onCloseRequest = {
            if (!closing) {
                closing = true

                scope.launch {
                    try {
                        player.close()
                    } finally {
                        try {
                            Innertube.close()
                        } finally {
                            exitApplication()
                        }
                    }
                }
            }
        },
        visible = !closing,
        onPreviewKeyEvent = { event ->
            if (
                event.type == KeyEventType.KeyDown &&
                event.key == Key.O && event.isCtrlPressed &&
                !event.isAltPressed && !event.isShiftPressed
            ) {
                AudioFiles.open(
                    canOpen = { !closing },
                    onSelected = player::playFiles,
                )
                true
            } else {
                false
            }
        },
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
                DesktopApp(player)
            }
        }
    }
}

@Composable
private fun DesktopApp(player: DesktopPlayer) {
    val playback by player.state.collectAsState()
    val messages = remember { SnackbarHostState() }
    var showPlayer by remember { mutableStateOf(false) }

    LaunchedEffect(playback.song == null) {
        if (playback.song == null) showPlayer = false
    }

    var bottomHeight by remember { mutableIntStateOf(0) }

    val bottomPadding = maxOf(
        140.dp,
        with(LocalDensity.current) { bottomHeight.toDp() } + 16.dp,
    )

    LaunchedEffect(playback.error) {
        playback.error?.let { message ->
            val verification = playback.requiresVerification
            val videoId = playback.song?.videoId

            val result = messages.showSnackbar(
                message = message,
                actionLabel = if (verification) "Abrir no YouTube" else "Tentar novamente",
                withDismissAction = true,
            )

            if (result == SnackbarResult.ActionPerformed) {
                if (verification) {
                    if (videoId != null) {
                        val opened = withContext(Dispatchers.IO) {
                            runCatching {
                                val id = URLEncoder.encode(videoId, "UTF-8")
                                Desktop.getDesktop().browse(
                                    URI("https://www.youtube.com/watch?v=$id"),
                                )
                            }.isSuccess
                        }
                        if (!opened) {
                            messages.showSnackbar("Não foi possível abrir o navegador.")
                        }
                    }
                } else {
                    player.retry()
                }
            }
        }
    }

    var selectedTab by remember { mutableIntStateOf(0) }
    var openedItem by remember { mutableStateOf<ShelfItem?>(null) }

    val savedScreens = rememberSaveableStateHolder()
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
            val detail = openedItem

            when {
                detail != null -> {
                    key(detail.browseId) {
                        DetailScreen(
                            item = detail,
                            onPlay = player::play,
                            bottomPadding = bottomPadding,
                        )
                    }
                }

                selectedTab == 0 -> {
                    savedScreens.SaveableStateProvider("home") {
                        HomeScreen(
                            state = homeState,
                            listState = homeListState,
                            onRetry = { homeRequest++ },
                            onItemClick = { openedItem = it },
                            bottomPadding = bottomPadding,
                        )
                    }
                }

                else -> {
                    Box(
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
        }

        FrostedTopBar(
            hazeState = hazeState,
            modifier = Modifier.align(Alignment.TopCenter),
            onBack = if (openedItem != null) {
                { openedItem = null }
            } else {
                null
            },
        )

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .widthIn(max = FLOATING_BAR_MAX_WIDTH)
                .fillMaxWidth()
                .onSizeChanged { bottomHeight = it.height },
        ) {
            if (playback.song != null) {
                MiniPlayer(
                    state = playback,
                    hazeState = hazeState,
                    onPlayPause = player::togglePlayPause,
                    onNext = player::next,
                    onExpand = { showPlayer = true },
                    modifier = Modifier.fillMaxWidth(),
                )

                Spacer(Modifier.height(8.dp))
            }

            FloatingBottomBar(
                tabs = tabs,
                selectedIndex = selectedTab,
                onTabSelected = {
                    selectedTab = it
                    openedItem = null
                },
                hazeState = hazeState,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        if (showPlayer && playback.song != null) {
            NowPlayingScreen(
                state = playback,
                onClose = { showPlayer = false },
                onPlayPause = player::togglePlayPause,
                onPrevious = player::previous,
                onNext = player::next,
                onSeek = player::seekTo,
                onVolume = player::setVolume,
                onToggleShuffle = player::toggleShuffle,
                onCycleRepeat = player::cycleRepeat,
                onQueueSelect = player::jumpTo,
                onQueueRemove = player::removeFromQueue,
                onQueueMove = player::moveInQueue,
            )
        }

        SnackbarHost(
            hostState = messages,
            modifier = Modifier.align(Alignment.BottomCenter)
                .widthIn(max = 560.dp).fillMaxWidth()
                .padding(bottom = if (showPlayer) 16.dp else bottomPadding),
        )
    }
}
