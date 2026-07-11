package com.novaplay.tv.ui.access

import androidx.lifecycle.ViewModel
import com.novaplay.tv.data.repo.ManagedAccessPolicy
import com.novaplay.tv.data.repo.ManagedAccessRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

/**
 * Exposes the current managed-access [policy] stream so blocked screens can
 * react live when the provider portal changes what this device may use.
 */
@HiltViewModel
class ManagedAccessViewModel @Inject constructor(
    repository: ManagedAccessRepository,
) : ViewModel() {
    val policy: StateFlow<ManagedAccessPolicy> = repository.policy
}
