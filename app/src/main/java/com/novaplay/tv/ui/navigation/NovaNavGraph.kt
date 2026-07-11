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
import com.novaplay.tv.data.repo.ManagedAccessPolicy
import com.novaplay.tv.data.repo.ManagedFeature
import com.novaplay.tv.ui.access.ManagedAccessBlockedScreen
import com.novaplay.tv.ui.access.ManagedAccessViewModel
import com.novaplay.tv.ui.activation.ActivationScreen
import com.novaplay.tv.ui.components.NovaBackdrop
import com.novaplay.tv.ui.gate.GateState
import com.novaplay.tv.ui.gate.GateViewModel
import com.novaplay.tv.ui.home.HomeScreen
import com.novaplay.tv.ui.live.LivePlayerScreen
import com.novaplay.tv.ui.live.ResponsiveLiveScreen
import com.novaplay.tv.ui.movies.MovieDetailsScreen
import com.novaplay.tv.ui.movies.MoviesScreen
import com.novaplay.tv.ui.player.VodPlayerScreen
import com.novaplay.tv.ui.playlists.AdaptivePlaylistsScreen
import com.novaplay.tv.ui.series.SeriesDetailsScreen
import com.novaplay.tv.ui.series.SeriesScreen
import com.novaplay.tv.ui.settings.EnhancedSettingsScreen
import com.novaplay.tv.ui.theme.isTvDevice

/**
 * Root navigation graph inside the shared backdrop and adaptive shell. Starts at the launch
 * gate (no playlist -> Activation, otherwise Home); live/movies/series destinations are
 * wrapped in [ManagedDestination] so the server-resolved policy gates them fail-closed.
 * Reports immersive mode (always on TV, player routes on touch) so the activity can hide
 * system bars.
 */
@Composable
fun NovaNavGraph(
    onImmersiveChanged: (Boolean) -> Unit = {},
) {
    val navController = rememberNavController()
    val accessViewModel: ManagedAccessViewModel = hiltViewModel()
    val policy by accessViewModel.policy.collectAsStateWithLifecycle()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val immersive = isTvDevice() || currentRoute == Routes.LIVE_PLAYER || currentRoute == Routes.VOD_PLAYER

    LaunchedEffect(immersive) { onImmersiveChanged(immersive) }

    NovaBackdrop {
        AdaptiveAppShell(
            currentRoute = currentRoute,
            policy = policy,
            onNavigate = { route -> navController.navigateTopLevel(route) },
        ) { shellModifier ->
            NavHost(
                navController = navController,
                startDestination = Routes.GATE,
                modifier = shellModifier,
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
                    ManagedDestination(
                        feature = ManagedFeature.LIVE,
                        policy = policy,
                        navController = navController,
                    ) {
                        ResponsiveLiveScreen(
                            onPlayChannel = { channelId, categoryId ->
                                navController.navigate(Routes.livePlayer(channelId, categoryId))
                            },
                        )
                    }
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
                    ManagedDestination(
                        feature = ManagedFeature.LIVE,
                        policy = policy,
                        navController = navController,
                    ) { LivePlayerScreen() }
                }

                composable(Routes.MOVIES) {
                    ManagedDestination(
                        feature = ManagedFeature.MOVIES,
                        policy = policy,
                        navController = navController,
                    ) {
                        MoviesScreen(
                            onOpenMovie = { movieId -> navController.navigate(Routes.movieDetails(movieId)) },
                        )
                    }
                }

                composable(
                    route = Routes.MOVIE_DETAILS,
                    arguments = listOf(navArgument("movieId") { type = NavType.LongType }),
                ) {
                    ManagedDestination(
                        feature = ManagedFeature.MOVIES,
                        policy = policy,
                        navController = navController,
                    ) {
                        MovieDetailsScreen(
                            onPlay = { movieId, resume ->
                                navController.navigate(
                                    Routes.vodPlayer(Routes.MEDIA_TYPE_MOVIE, movieId, resume),
                                )
                            },
                        )
                    }
                }

                composable(Routes.SERIES) {
                    ManagedDestination(
                        feature = ManagedFeature.SERIES,
                        policy = policy,
                        navController = navController,
                    ) {
                        SeriesScreen(
                            onOpenSeries = { seriesId -> navController.navigate(Routes.seriesDetails(seriesId)) },
                        )
                    }
                }

                composable(
                    route = Routes.SERIES_DETAILS,
                    arguments = listOf(navArgument("seriesId") { type = NavType.LongType }),
                ) {
                    ManagedDestination(
                        feature = ManagedFeature.SERIES,
                        policy = policy,
                        navController = navController,
                    ) {
                        SeriesDetailsScreen(
                            onPlayEpisode = { episodeId, resume ->
                                navController.navigate(
                                    Routes.vodPlayer(Routes.MEDIA_TYPE_EPISODE, episodeId, resume),
                                )
                            },
                        )
                    }
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
                ) { entry ->
                    val feature = if (entry.arguments?.getString("mediaType") == Routes.MEDIA_TYPE_EPISODE) {
                        ManagedFeature.SERIES
                    } else {
                        ManagedFeature.MOVIES
                    }
                    ManagedDestination(
                        feature = feature,
                        policy = policy,
                        navController = navController,
                    ) { VodPlayerScreen() }
                }

                composable(Routes.PLAYLISTS) {
                    AdaptivePlaylistsScreen(
                        onAllPlaylistsRemoved = {
                            navController.navigate(Routes.ACTIVATION) {
                                popUpTo(0) { inclusive = true }
                            }
                        },
                    )
                }

                composable(Routes.PLAYLISTS_SETUP) {
                    AdaptivePlaylistsScreen(
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

                composable(Routes.SETTINGS) { EnhancedSettingsScreen() }
            }
        }
    }
}

/**
 * Gates a destination behind the managed-access policy: renders [content] when the feature
 * is allowed, otherwise the blocked screen in place of it — no redirect, so the check
 * re-evaluates on every policy change and blocked routes never flash real content.
 */
@Composable
private fun ManagedDestination(
    feature: ManagedFeature,
    policy: ManagedAccessPolicy,
    navController: NavHostController,
    content: @Composable () -> Unit,
) {
    if (policy.allows(feature)) {
        content()
    } else {
        ManagedAccessBlockedScreen(
            feature = feature,
            policy = policy,
            onOpenPlaylists = { navController.navigate(Routes.PLAYLISTS) },
            onOpenSettings = { navController.navigateTopLevel(Routes.SETTINGS) },
        )
    }
}

/**
 * Tab-style navigation between top-level destinations: single-top with state save/restore
 * so each tab keeps its scroll/focus, popping up to HOME so tabs never stack on the back
 * stack. No-op when already on the route.
 */
private fun NavHostController.navigateTopLevel(route: String) {
    if (currentDestination?.route == route) return
    navigate(route) {
        launchSingleTop = true
        restoreState = true
        popUpTo(Routes.HOME) { saveState = true }
    }
}

/**
 * Launch gate: renders only an empty box over the backdrop while [GateViewModel] resolves,
 * then routes to Activation (no playlist) or Home, removing GATE from the back stack so
 * Back exits the app instead of returning here.
 */
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
