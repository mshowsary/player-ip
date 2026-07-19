package com.novaplay.tv.ui.activation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.novaplay.tv.core.DeviceIdentity
import com.novaplay.tv.data.repo.LicenseInfo
import com.novaplay.tv.data.repo.PlayerLicenseRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/** The pair every portal and screen shows for this installation. */
data class PlayerIdentity(
    val mac: String = "",
    val deviceKey: String = "",
)

/**
 * Backs the IBO-style identity display on the setup screen: the pseudo MAC
 * Address + Device Key (the owner's portal login), plus the live trial /
 * license state. Registration with the portal happens on first collection.
 */
@HiltViewModel
class PlayerIdentityViewModel @Inject constructor(
    deviceIdentity: DeviceIdentity,
    playerLicenseRepository: PlayerLicenseRepository,
) : ViewModel() {

    private val _identity = MutableStateFlow(PlayerIdentity())
    val identity: StateFlow<PlayerIdentity> = _identity.asStateFlow()

    val license: StateFlow<LicenseInfo?> = playerLicenseRepository.license

    init {
        viewModelScope.launch {
            val info = deviceIdentity.get()
            _identity.value = PlayerIdentity(mac = info.mac, deviceKey = info.deviceKey)
        }
        viewModelScope.launch { playerLicenseRepository.refresh() }
    }
}
