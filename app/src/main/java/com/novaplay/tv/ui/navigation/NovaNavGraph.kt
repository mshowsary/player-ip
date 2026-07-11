package com.novaplay.tv.ui.navigation

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.novaplay.tv.ui.activation.ActivationScreen
import com.novaplay.tv.ui.components.NovaBackdrop
import com.novaplay.tv.ui.gate.GateState
import com.novaplay.tv.ui.gate.GateViewModel
import com.novaplay.tv.ui.home.HomeScreen
import com.novaplay.tv.ui.live.LivePlayerScreen
import com.novaplay.tv.ui.live.LiveScreen
import com.novaplay.tv.ui.movies.MovieDetailsScreen
import com.novaplay.tv.ui.movies.MoviesScreen
import com.novaplay.tv.ui.player.VodPlayerScreen
import com.novaplay.tv.ui.playlists.PlaylistsScreen
import com.novaplay.tv.ui.series.SeriesDetailsScreen
import com.novaplay.tv.ui.series.SeriesScreen
import com.novaplay.tv.ui.settings.SettingsScreen
import com.novaplay.tv.ui.theme.isTvDevice

@Composable
fun NovaNavGraph(
    onImmersiveChanged: (Boolean) -> Unit = {},
) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val immersive = isTvDevice() || currentRoute == Routes.LIVE_PLAYER || currentRoute == Routes.VOD_PLAYER

    LaunchedEffect(immersive) { onImmersiveChanged(immersive) }

    NovaBackdrop {
        AdaptiveAppShell(
            currentRoute = currentRoute,
            onNavigate = { route -> navController.navigateTopLevel(route) },
        ) { shellModifier ->
            NavHost(
                navController = navController,
                startDestination = Routes.GATE,
                modifier = shellModifier,
                // Quick fades — snappy on low-end boxes, no sliding chrome.
                enterTransition = { fadeIn(tween(220)) },
                exitTransition = { fadeOut(tween(150)) },
                popEnterTransition = { fadeIn(tween(220)) },
                popExitTransition = { fadeOut(tween(150)) },
            ) {
                composable(Routes.GATE) { GateScreen(navController) }

                composable(Routes.ACTIVATION) {
                    ActivationScreen(
                        onActivated = {
                            navController.navigate(Routes.HOME) {
                                popUpTo(Routes.ACTIVATION) { inclusive = true }
                            }
                        },
                        onAddPersonalPlaylist = {
                            navController.navigate(Routes.PLAYLISTS_SETUP)
                        },
                    )
                }

                composable(Routes.HOME) {
                    HomeScreen(
                        onOpenLive = { navController.navigateTopLevel(Routes.LIVE) },
                        onOpenMovies = { navController.navigateTopLevel(Routes.MOVIES) },
                        onOpenSeries = { navController.navigateTopLevel(Routes.SERIES) },
                        onOpenPlaylists = { navController.navigate(Routes.PLAYLISTS) },
                        onOpenSettings = { navController.navigateTopLevel(Routes.SETTINGS) },
                    )
                }

                composable(Routes.LIVE) {
                    LiveScreen(
                        onPlayChannel = { channelId, categoryId ->
                            navController.navigate(Routes.livePlayer(channelId, categoryId))
                        },
                    )
                }

                composable(
                    route = Routes.LIVE_PLAYER,
                    arguments = listOf(
                        navArgument("channelId") { type = NavType.LongType },
                        navArgument("categoryId") {
                            type = NavType.LongType
                            defaultValue = -1L
                        },
                    ),
                ) {
                    LivePlayerScreen()
                }

                composable(Routes.MOVIES) {
                    MoviesScreen(
                        onOpenMovie = { movieId -> navController.navigate(Routes.movieDetails(movieId)) },
                    )
                }

                composable(
                    route = Routes.MOVIE_DETAILS,
                    arguments = listOf(navArgument("movieId") { type = NavType.LongType }),
                ) {
                    MovieDetailsScreen(
                        onPlay = { movieId, resume ->
                            navController.navigate(
                                Routes.vodPlayer(Routes.MEDIA_TYPE_MOVIE, movieId, resume),
                            )
                        },
                    )
                }

                composable(Routes.SERIES) {
                    SeriesScreen(
                        onOpenSeries = { seriesId -> navController.navigate(Routes.seriesDetails(seriesId)) },
                    )
                }

                composable(
                    route = Routes.SERIES_DETAILS,
                    arguments = listOf(navArgument("seriesId") { type = NavType.LongType }),
                ) {
                    SeriesDetailsScreen(
                        onPlayEpisode = { episodeId, resume ->
                            navController.navigate(
                                Routes.vodPlayer(Routes.MEDIA_TYPE_EPISODE, episodeId, resume),
                            )
                        },
                    )
                }

                composable(
                    route = Routes.VOD_PLAYER,
                    arguments = listOf(
                        navArgument("mediaType") { type = NavType.StringType },
                        navArgument("mediaId") { type = NavType.LongType },
                        navArgument("resume") {
                            type = NavType.BoolType
                            defaultValue = false
                        },
                    ),
                ) {
                    VodPlayerScreen()
                }

                composable(Routes.PLAYLISTS) {
                    PlaylistsScreen(
                        onAllPlaylistsRemoved = {
                            navController.navigate(Routes.ACTIVATION) {
                                popUpTo(0) { inclusive = true }
                            }
                        },
                    )
                }

                composable(Routes.PLAYLISTS_SETUP) {
                    PlaylistsScreen(
                        onAllPlaylistsRemoved = {
                            navController.popBackStack()
                        },
                        onPlaylistReady = {
                            navController.navigate(Routes.HOME) {
                                popUpTo(Routes.ACTIVATION) { inclusive = true }
                            }
                        },
                    )
                }

                composable(Routes.SETTINGS) { SettingsScreen() }
            }
        }
    }
}

private fun NavHostController.navigateTopLevel(route: String) {
    if (currentDestination?.route == route) return
    navigate(route) {
        launchSingleTop = true
        restoreState = true
        popUpTo(Routes.HOME) { saveState = true }
    }
}

@Composable
private fun GateScreen(navController: NavHostController) {
    val viewModel: GateViewModel = hiltViewModel()
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(state) {
        when (state) {
            GateState.Loading -> Unit
            GateState.NeedsActivation -> navController.navigate(Routes.ACTIVATION) {
                popUpTo(Routes.GATE) { inclusive = true }
            }
            GateState.Ready -> navController.navigate(Routes.HOME) {
                popUpTo(Routes.GATE) { inclusive = true }
            }
        }
    }
    Box(modifier = Modifier.fillMaxSize())
}
