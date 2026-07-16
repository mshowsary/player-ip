package com.novaplay.tv.ui.playlists

/**
 * One-shot hand-off from the first-run setup screen: "Add from your phone"
 * navigates to Playlists and this flag makes the phone-entry panel open
 * immediately, so a self-service user never digs through menus for the code.
 * Process-scoped by design (same pattern as the gesture-demo session flag).
 */
object PhoneEntryLaunch {
    var requested: Boolean = false

    /** Returns true exactly once per request. */
    fun consume(): Boolean {
        val was = requested
        requested = false
        return was
    }
}
