package com.gecko.app.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.gecko.app.data.DownloadEntry
import com.gecko.app.model.AppSettings
import com.gecko.app.navigation.Routes
import com.gecko.app.sources.SourcePlugin
import com.gecko.app.ui.components.GeckoBackdrop
import com.gecko.app.ui.components.GeckoBottomBar
import com.gecko.app.ui.screens.DownloadsScreen
import com.gecko.app.ui.screens.HomeScreen
import com.gecko.app.ui.screens.IpodScreen
import com.gecko.app.ui.screens.KaraokeScreen
import com.gecko.app.ui.screens.LibraryScreen
import com.gecko.app.ui.screens.PlayerScreen
import com.gecko.app.ui.screens.SettingsScreen
import com.gecko.app.ui.theme.GeckoTheme

@Composable
fun GeckoRoot(viewModel: GeckoViewModel) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val library by viewModel.library.collectAsStateWithLifecycle()
    val player by viewModel.player.collectAsStateWithLifecycle()
    val downloads by viewModel.downloads.collectAsStateWithLifecycle()
    val plugins by viewModel.plugins.collectAsStateWithLifecycle()
    val search by viewModel.search.collectAsStateWithLifecycle()
    val bridgeStatus by viewModel.bridgeStatus.collectAsStateWithLifecycle()

    val navController = rememberNavController()
    val snackbarHostState = remember { SnackbarHostState() }
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route ?: Routes.HOME
    val isTab = currentRoute in Routes.tabs
    val context = LocalContext.current

    val notificationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { }

    LaunchedEffect(Unit) {
        viewModel.messages.collect { message ->
            snackbarHostState.showSnackbar(message)
        }
    }

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS,
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    GeckoTheme(settings = settings, palette = player.palette) {
        GeckoBackdrop {
            Scaffold(
                modifier = Modifier.fillMaxSize(),
                containerColor = Color.Transparent,
                contentWindowInsets = WindowInsets(0, 0, 0, 0),
                snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
                bottomBar = {
                    if (isTab) {
                        GeckoBottomBar(
                            selectedRoute = currentRoute,
                            player = player,
                            onSelect = { route -> navController.navigateToTab(route) },
                            onOpenPlayer = {
                                navController.navigate(Routes.PLAYER) { launchSingleTop = true }
                            },
                            onTogglePlay = viewModel::togglePlayPause,
                        )
                    }
                },
            ) { innerPadding ->
                GeckoNavHost(
                    navController = navController,
                    viewModel = viewModel,
                    library = library,
                    player = player,
                    settings = settings,
                    downloads = downloads,
                    plugins = plugins,
                    search = search,
                    bridgeStatus = bridgeStatus,
                    innerPadding = innerPadding,
                )
            }
        }
    }
}

@Composable
private fun GeckoNavHost(
    navController: NavHostController,
    viewModel: GeckoViewModel,
    library: LibraryState,
    player: PlayerUiState,
    settings: AppSettings,
    downloads: List<DownloadEntry>,
    plugins: List<SourcePlugin>,
    search: SearchState,
    bridgeStatus: BridgeStatus,
    innerPadding: PaddingValues,
) {
    NavHost(
        navController = navController,
        startDestination = Routes.HOME,
        modifier = Modifier
            .fillMaxSize()
            .padding(innerPadding),
    ) {
        composable(Routes.HOME) {
            HomeScreen(
                library = library,
                player = player,
                settings = settings,
                pluginCount = plugins.size,
                onPlayTrack = { track, queue -> viewModel.playTrack(track, queue) },
                onStartMix = { viewModel.startInfiniteMix() },
                onShuffleAll = viewModel::shuffleAll,
                onOpenPlayer = {
                    navController.navigate(Routes.PLAYER) { launchSingleTop = true }
                },
                onOpenKaraoke = { navController.navigate(Routes.KARAOKE) },
                onOpenIpod = { navController.navigate(Routes.IPOD) },
                onOpenLibrary = { navController.navigateToTab(Routes.LIBRARY) },
                onOpenSettings = { navController.navigateToTab(Routes.SETTINGS) },
                onOpenDownloads = { navController.navigateToTab(Routes.DOWNLOADS) },
                onGenerateDemo = viewModel::generateDemo,
                onRefresh = viewModel::refreshLibrary,
                onDownloadTrack = viewModel::downloadTrack,
                onPlayNext = viewModel::playNext,
                onAddToQueue = viewModel::addToQueue,
            )
        }

        composable(Routes.LIBRARY) {
            LibraryScreen(
                library = library,
                player = player,
                search = search,
                remoteEnabled = settings.ytMusicUrl.isNotBlank() ||
                    settings.ytMusicStreamUrl.isNotBlank() ||
                    plugins.isNotEmpty(),
                onSearch = viewModel::search,
                onClearSearch = viewModel::clearSearch,
                onPlayTrack = { track, queue -> viewModel.playTrack(track, queue) },
                onPlayNext = viewModel::playNext,
                onAddToQueue = viewModel::addToQueue,
                onDownload = viewModel::downloadTrack,
                onPlayAlbum = { album -> viewModel.playAlbum(album) },
                onShuffleAlbum = { album -> viewModel.playAlbum(album, shuffle = true) },
                onPlayArtist = { artist -> viewModel.playArtist(artist) },
                onShuffleArtist = { artist -> viewModel.playArtist(artist, shuffle = true) },
                onOpenDownloads = { navController.navigateToTab(Routes.DOWNLOADS) },
                onGenerateDemo = viewModel::generateDemo,
                onRefresh = viewModel::refreshLibrary,
            )
        }

        composable(Routes.DOWNLOADS) {
            DownloadsScreen(
                downloads = downloads,
                player = player,
                wifiOnly = settings.wifiOnlyDownloads,
                smartDownloads = settings.smartDownloads,
                onPlayTrack = { track, queue -> viewModel.playTrack(track, queue) },
                onRetry = viewModel::retryDownload,
                onDelete = viewModel::deleteDownload,
                onDeleteAll = viewModel::deleteAllDownloads,
                onOpenLibrary = { navController.navigateToTab(Routes.LIBRARY) },
                onOpenSettings = { navController.navigateToTab(Routes.SETTINGS) },
            )
        }

        composable(Routes.SETTINGS) {
            SettingsScreen(
                settings = settings,
                plugins = plugins,
                bridgeStatus = bridgeStatus,
                hasDemo = library.hasDemo,
                onUpdate = viewModel::updateSettings,
                onImportPlugin = viewModel::importPlugin,
                onRemovePlugin = viewModel::removePlugin,
                onTestYouTubeMusic = viewModel::testYouTubeMusic,
                onTestLastFm = viewModel::testLastFmSession,
                onGenerateDemo = viewModel::generateDemo,
                onRemoveDemo = viewModel::removeDemo,
            )
        }

        composable(Routes.PLAYER) {
            PlayerScreen(
                player = player,
                settings = settings,
                onClose = { navController.popBackStack() },
                onTogglePlay = viewModel::togglePlayPause,
                onNext = viewModel::next,
                onPrevious = viewModel::previous,
                onSeekToFraction = viewModel::seekToFraction,
                onToggleShuffle = viewModel::toggleShuffle,
                onCycleRepeat = viewModel::cycleRepeat,
                onDownload = viewModel::downloadTrack,
                onOpenKaraoke = { navController.navigate(Routes.KARAOKE) },
                onImportLyrics = viewModel::importLyrics,
                onRemoveLyrics = viewModel::removeLyrics,
                onRemoveFromQueue = viewModel::removeFromQueue,
                onSeekToQueueItem = viewModel::seekToQueueItem,
            )
        }

        composable(Routes.KARAOKE) {
            KaraokeScreen(
                player = player,
                onClose = { navController.popBackStack() },
                onTogglePlay = viewModel::togglePlayPause,
                onNext = viewModel::next,
                onPrevious = viewModel::previous,
                onSeekToFraction = viewModel::seekToFraction,
                onImportLyrics = viewModel::importLyrics,
                onRemoveLyrics = viewModel::removeLyrics,
            )
        }

        composable(Routes.IPOD) {
            IpodScreen(
                library = library,
                player = player,
                onClose = { navController.popBackStack() },
                onPlayTrack = { track, queue -> viewModel.playTrack(track, queue) },
                onShuffleAll = viewModel::shuffleAll,
                onTogglePlay = viewModel::togglePlayPause,
                onNext = viewModel::next,
                onPrevious = viewModel::previous,
            )
        }
    }
}

private fun NavHostController.navigateToTab(route: String) {
    navigate(route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
