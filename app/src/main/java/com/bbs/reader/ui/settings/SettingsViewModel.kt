package com.bbs.reader.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.bbs.reader.data.local.HomePrefsStore
import com.bbs.reader.data.local.ManagedCommunity
import com.bbs.reader.data.local.SettingsStore
import com.bbs.reader.data.model.AppSettings
import com.bbs.reader.data.repository.CommunityCatalog
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(
    private val settingsStore: SettingsStore,
    private val homePrefsStore: HomePrefsStore
) : ViewModel() {

    val settings: StateFlow<AppSettings> = settingsStore.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppSettings())

    val managedCommunities: StateFlow<List<ManagedCommunity>> =
        homePrefsStore.managedCommunities
            .stateIn(
                viewModelScope,
                SharingStarted.WhileSubscribed(5_000),
                CommunityCatalog.communities.map { ManagedCommunity(it, enabled = true) }
            )

    fun update(transform: (AppSettings) -> AppSettings) {
        viewModelScope.launch {
            settingsStore.update(transform)
        }
    }

    fun setCommunityEnabled(communityId: String, enabled: Boolean) {
        viewModelScope.launch {
            homePrefsStore.setEnabled(communityId, enabled)
        }
    }

    fun reset() {
        viewModelScope.launch {
            settingsStore.set(AppSettings())
        }
    }

    companion object {
        fun factory(
            settingsStore: SettingsStore,
            homePrefsStore: HomePrefsStore
        ): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return SettingsViewModel(settingsStore, homePrefsStore) as T
                }
            }
    }
}
