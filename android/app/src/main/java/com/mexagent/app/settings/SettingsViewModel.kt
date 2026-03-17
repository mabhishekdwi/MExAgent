package com.mexagent.app.settings

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.mexagent.app.utils.Constants
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class SettingsViewModel(app: Application) : AndroidViewModel(app) {

    private val dataStore = SettingsDataStore(app)

    val backendUrl: StateFlow<String> = dataStore.backendUrl
        .stateIn(viewModelScope, SharingStarted.Eagerly, Constants.DEFAULT_BACKEND_URL)

    val depth: StateFlow<Int> = dataStore.depth
        .stateIn(viewModelScope, SharingStarted.Eagerly, Constants.DEFAULT_DEPTH)

    val aiMode: StateFlow<Boolean> = dataStore.aiMode
        .stateIn(viewModelScope, SharingStarted.Eagerly, Constants.DEFAULT_AI_MODE)

    fun setBackendUrl(url: String) = viewModelScope.launch {
        dataStore.saveBackendUrl(url.trim())
    }

    fun setDepth(depth: Int) = viewModelScope.launch {
        dataStore.saveDepth(depth.coerceIn(1, 10))
    }

    fun setAiMode(enabled: Boolean) = viewModelScope.launch {
        dataStore.saveAiMode(enabled)
    }
}
