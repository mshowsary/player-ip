package com.novaplay.tv.ui.navigation

import com.novaplay.tv.data.repo.ManagedAccessPolicy
import com.novaplay.tv.data.repo.ManagedAccessState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TopLevelNavigationPolicyTest {

    @Test
    fun personalAccessShowsAllDestinationsInStableOrder() {
        assertEquals(
            listOf(Routes.HOME, Routes.LIVE, Routes.GUIDE, Routes.MOVIES, Routes.SERIES, Routes.SETTINGS),
            TopLevelNavigationPolicy.visibleRoutes(ManagedAccessPolicy()),
        )
    }

    @Test
    fun managedFeatureRestrictionsKeepHomeAndSettingsReachable() {
        val routes = TopLevelNavigationPolicy.visibleRoutes(
            ManagedAccessPolicy(
                state = ManagedAccessState.ACTIVE,
                allowLive = true,
                allowMovies = false,
                allowSeries = false,
            ),
        )

        assertEquals(listOf(Routes.HOME, Routes.LIVE, Routes.GUIDE, Routes.SETTINGS), routes)
    }

    @Test
    fun guideFollowsTheLiveEntitlement() {
        val routes = TopLevelNavigationPolicy.visibleRoutes(
            ManagedAccessPolicy(
                state = ManagedAccessState.ACTIVE,
                allowLive = false,
                allowMovies = true,
                allowSeries = true,
            ),
        )

        assertEquals(listOf(Routes.HOME, Routes.MOVIES, Routes.SERIES, Routes.SETTINGS), routes)
    }

    @Test
    fun blockedManagedDeviceKeepsOnlySafeDestinations() {
        val routes = TopLevelNavigationPolicy.visibleRoutes(
            ManagedAccessPolicy(
                state = ManagedAccessState.REVOKED,
                allowLive = true,
                allowMovies = true,
                allowSeries = true,
            ),
        )

        assertEquals(listOf(Routes.HOME, Routes.SETTINGS), routes)
    }

    @Test
    fun selectedStateMatchesOnlyTheCurrentRoute() {
        assertTrue(TopLevelNavigationPolicy.isSelected(Routes.MOVIES, Routes.MOVIES))
        assertFalse(TopLevelNavigationPolicy.isSelected(Routes.MOVIES, Routes.SERIES))
        assertFalse(TopLevelNavigationPolicy.isSelected(null, Routes.HOME))
    }
}
