package com.novaplay.tv.data.repo

import com.novaplay.tv.core.ParentalPinPolicy
import com.novaplay.tv.data.prefs.AppPreferences
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Parental-control state: the PIN and the locked-category set, plus the
 * in-memory session unlock. A correct PIN unlocks locked content until the
 * app process ends or [relock] is called — matching how a parent hands the
 * remote back after choosing something to watch.
 *
 * Content filtering keys on [hiddenKeys]: the locked set while the session
 * is locked, empty once unlocked. UIs that show lock badges use [lockedKeys],
 * which is unaffected by the session state.
 */
@Singleton
class ParentalControlsRepository @Inject constructor(
    private val prefs: AppPreferences,
) {
    private val _sessionUnlocked = MutableStateFlow(false)

    /** True after a successful PIN entry, until process death or [relock]. */
    val sessionUnlocked: StateFlow<Boolean> = _sessionUnlocked

    /** Whether a parental PIN has been configured on this device. */
    val pinConfigured: Flow<Boolean> = prefs.parentalPin
        .map { ParentalPinPolicy.isConfigured(it) }
        .distinctUntilChanged()

    /** Lock keys of every locked category, regardless of the session state. */
    val lockedKeys: Flow<Set<String>> = prefs.parentalLockedKeys

    /** Lock keys whose content must be hidden right now (empty once unlocked). */
    val hiddenKeys: Flow<Set<String>> =
        combine(lockedKeys, _sessionUnlocked) { locked, unlocked ->
            if (unlocked) emptySet() else locked
        }.distinctUntilChanged()

    /**
     * Sets or replaces the PIN. Returns false (and stores nothing) for input
     * that is not exactly four digits. Setting a PIN also unlocks the session:
     * the person who just chose the PIN is the parent.
     */
    suspend fun setPin(pin: String): Boolean {
        if (!ParentalPinPolicy.isValidPin(pin)) return false
        prefs.setParentalPin(ParentalPinPolicy.encode(pin))
        _sessionUnlocked.value = true
        return true
    }

    /** Checks [pin] without changing session state (used by the change-PIN flow). */
    suspend fun checkPin(pin: String): Boolean =
        ParentalPinPolicy.verify(pin, prefs.parentalPin.first())

    /** Verifies [pin] and unlocks the session on success. Fails closed on any mismatch. */
    suspend fun unlock(pin: String): Boolean {
        val ok = checkPin(pin)
        if (ok) _sessionUnlocked.value = true
        return ok
    }

    /** Ends the unlocked session; locked categories disappear again immediately. */
    fun relock() {
        _sessionUnlocked.value = false
    }

    /** Adds or removes one category lock key. */
    suspend fun toggleLock(key: String) {
        val current = prefs.parentalLockedKeys.first()
        prefs.setParentalLockedKeys(
            if (key in current) current - key else current + key,
        )
    }
}
