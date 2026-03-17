package com.mexagent.app.logs

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.mexagent.app.network.ApiClient
import com.mexagent.app.settings.SettingsDataStore
import com.mexagent.app.utils.Constants
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

class LogViewModel(app: Application) : AndroidViewModel(app) {

    private val dataStore = SettingsDataStore(app)

    private val _logs = MutableStateFlow<List<LogEntry>>(emptyList())
    val logs: StateFlow<List<LogEntry>> = _logs.asStateFlow()

    private val _isPolling = MutableStateFlow(false)
    val isPolling: StateFlow<Boolean> = _isPolling.asStateFlow()

    private val _error = MutableSharedFlow<String>()
    val error: SharedFlow<String> = _error.asSharedFlow()

    private var pollJob: Job? = null
    private var lastSeenId: Long = 0

    fun startPolling() {
        if (pollJob?.isActive == true) return
        _isPolling.value = true
        pollJob = viewModelScope.launch {
            val url = dataStore.backendUrl.first()
            val service = ApiClient.getService(url)
            while (isActive) {
                try {
                    val resp = service.getLogs(sinceId = lastSeenId.takeIf { it > 0 })
                    if (resp.isSuccessful) {
                        val newEntries = resp.body()?.logs?.map { LogEntry.fromNetwork(it) } ?: emptyList()
                        if (newEntries.isNotEmpty()) {
                            lastSeenId = newEntries.maxOf { it.id }
                            _logs.value = _logs.value + newEntries
                        }
                    }
                } catch (e: Exception) {
                    _error.emit("Poll error: ${e.message}")
                }
                delay(Constants.LOG_POLL_INTERVAL_MS)
            }
        }
    }

    fun stopPolling() {
        pollJob?.cancel()
        _isPolling.value = false
    }

    fun clearLogs() {
        _logs.value = emptyList()
        lastSeenId = 0
    }

    override fun onCleared() {
        super.onCleared()
        stopPolling()
    }
}
