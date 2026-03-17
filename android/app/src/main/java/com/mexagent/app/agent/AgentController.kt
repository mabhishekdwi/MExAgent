package com.mexagent.app.agent

import android.content.Context
import com.mexagent.app.network.ApiClient
import com.mexagent.app.network.models.StartRequest
import com.mexagent.app.network.models.StopRequest
import com.mexagent.app.settings.SettingsDataStore
import kotlinx.coroutines.flow.*

class AgentController(context: Context) {

    private val dataStore = SettingsDataStore(context)

    private val _state = MutableStateFlow<AgentState>(AgentState.Idle)
    val state: StateFlow<AgentState> = _state.asStateFlow()

    suspend fun start(depth: Int, aiMode: Boolean): Result<String> {
        _state.value = AgentState.Starting
        return try {
            val url     = dataStore.backendUrl.first()
            val speedMs = dataStore.speedMs.first()
            val service = ApiClient.getService(url)
            val resp    = service.startAgent(StartRequest(depth = depth, aiMode = aiMode, actionDelayMs = speedMs))
            if (resp.isSuccessful && resp.body() != null) {
                val sessionId = resp.body()!!.sessionId
                _state.value = AgentState.Running(sessionId)
                Result.success(sessionId)
            } else {
                val msg = "Start failed: HTTP ${resp.code()}"
                _state.value = AgentState.Error(msg)
                Result.failure(Exception(msg))
            }
        } catch (e: Exception) {
            val msg = "Connection error: ${e.message}"
            _state.value = AgentState.Error(msg)
            Result.failure(e)
        }
    }

    suspend fun stop(): Result<Unit> {
        val currentSessionId = (_state.value as? AgentState.Running)?.sessionId
        _state.value = AgentState.Stopping
        return try {
            val url     = dataStore.backendUrl.first()
            val service = ApiClient.getService(url)
            service.stopAgent(StopRequest(sessionId = currentSessionId))
            _state.value = AgentState.Idle
            Result.success(Unit)
        } catch (e: Exception) {
            _state.value = AgentState.Idle
            Result.failure(e)
        }
    }
}
