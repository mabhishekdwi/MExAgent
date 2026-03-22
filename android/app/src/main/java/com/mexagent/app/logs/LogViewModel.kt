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

    private val _scrollToBottom = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val scrollToBottom: SharedFlow<Unit> = _scrollToBottom.asSharedFlow()

    private var pollJob: Job? = null
    private var lastSeenId: Long = 0
    private var currentSessionId: String? = null

    fun startPolling() {
        if (pollJob?.isActive == true) return
        _logs.value = emptyList()
        lastSeenId = 0
        pollJob = viewModelScope.launch {
            val url = dataStore.backendUrl.first()
            val service = ApiClient.getService(url)

            // Get current session ID from status
            try {
                val statusResp = service.getStatus()
                if (statusResp.isSuccessful) {
                    currentSessionId = statusResp.body()?.sessionId
                }
            } catch (_: Exception) {}

            // Load existing logs for the current session
            try {
                val resp = service.getLogs(sinceId = null)
                if (resp.isSuccessful) {
                    val all = resp.body()?.logs ?: emptyList()
                    val sessionLogs = if (currentSessionId != null)
                        all.filter { it.sessionId == currentSessionId }
                    else all.takeLast(50)
                    if (sessionLogs.isNotEmpty()) {
                        _logs.value = sessionLogs.map { LogEntry.fromNetwork(it) }
                        lastSeenId = sessionLogs.maxOf { it.id }
                        _scrollToBottom.tryEmit(Unit)
                    }
                }
            } catch (_: Exception) {}

            // Poll for new logs
            while (isActive) {
                try {
                    val resp = service.getLogs(sinceId = lastSeenId.takeIf { it > 0 })
                    if (resp.isSuccessful) {
                        val newEntries = resp.body()?.logs
                            ?.filter { currentSessionId == null || it.sessionId == currentSessionId }
                            ?.map { LogEntry.fromNetwork(it) }
                            ?: emptyList()
                        val existingIds = _logs.value.map { it.id }.toSet()
                        val dedupedEntries = newEntries.filter { it.id !in existingIds }
                        if (dedupedEntries.isNotEmpty()) {
                            lastSeenId = dedupedEntries.maxOf { it.id }
                            _logs.value = _logs.value + dedupedEntries
                            _scrollToBottom.tryEmit(Unit)
                        }
                    }
                } catch (_: Exception) {}
                delay(Constants.LOG_POLL_INTERVAL_MS)
            }
        }
    }

    fun stopPolling() {
        pollJob?.cancel()
    }

    fun clearLogs() {
        _logs.value = emptyList()
    }

    override fun onCleared() {
        super.onCleared()
        stopPolling()
    }
}
