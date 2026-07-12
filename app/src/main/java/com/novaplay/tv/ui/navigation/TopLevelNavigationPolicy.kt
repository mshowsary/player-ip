package com.novaplay.tv.ui.navigation

import com.novaplay.tv.data.repo.ManagedAccessPolicy
import com.novaplay.tv.data.repo.ManagedFeature

/**
 * Pure policy for the adaptive phone/tablet shell. Keeping destination filtering
 * outside Compose makes the order and managed-access behavior deterministic and
 * unit-testable across touch, keyboard and accessibility navigation.
 */
internal object TopLevelNavigationPolicy {
    private val orderedRoutes = listOf(
        Routes.HOME,
        Routes.LIVE,
        Routes.GUIDE,
        Routes.MOVIES,
        Routes.SERIES,
        Routes.SETTINGS,
    )

    fun visibleRoutes(policy: ManagedAccessPolicy): List<String> = orderedRoutes.filter { route ->
        when (route) {
            // The guide is a Live surface: it shows and tunes live channels only.
            Routes.LIVE, Routes.GUIDE -> policy.allows(ManagedFeature.LIVE)
            Routes.MOVIES -> policy.allows(ManagedFeature.MOVIES)
            Routes.SERIES -> policy.allows(ManagedFeature.SERIES)
            else -> true
        }
    }

    fun isSelected(currentRoute: String?, route: String): Boolean = currentRoute == route
}
