package com.mexagent.app.main

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.mexagent.app.network.ApiClient
import com.mexagent.app.settings.SettingsDataStore
import com.mexagent.app.utils.Constants
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class MainUiState(
    val backendUrl: String   = Constants.DEFAULT_BACKEND_URL,
    val agentStatus: String  = "Unknown",
    val currentScreen: String? = null,
    val actionsExecuted: Int = 0,
    val isConnected: Boolean = false,
    val isLoading: Boolean   = false,
    val errorMessage: String? = null
)

class MainViewModel(app: Application) : AndroidViewModel(app) {

    private val dataStore = SettingsDataStore(app)

    private val _uiState = MutableStateFlow(MainUiState())
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            dataStore.backendUrl.collectLatest { url ->
                _uiState.update { it.copy(backendUrl = url) }
            }
        }
    }

    fun checkStatus() = viewModelScope.launch {
        _uiState.update { it.copy(isLoading = true, errorMessage = null) }
        try {
            val url     = dataStore.backendUrl.first()
            val service = ApiClient.getService(url)
            val resp    = service.getStatus()
            if (resp.isSuccessful) {
                val body = resp.body()!!
                _uiState.update {
                    it.copy(
                        isLoading       = false,
                        isConnected     = true,
                        agentStatus     = body.status,
                        currentScreen   = body.currentScreen,
                        actionsExecuted = body.actionsExecuted
                    )
                }
            } else {
                _uiState.update {
                    it.copy(isLoading = false, isConnected = false,
                        agentStatus = "HTTP ${resp.code()}")
                }
            }
        } catch (e: Exception) {
            _uiState.update {
                it.copy(isLoading = false, isConnected = false, errorMessage = e.message)
            }
        }
    }
}
