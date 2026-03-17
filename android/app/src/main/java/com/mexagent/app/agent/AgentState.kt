package com.mexagent.app.agent

sealed class AgentState {
    object Idle     : AgentState()
    object Starting : AgentState()
    data class Running(val sessionId: String) : AgentState()
    object Stopping : AgentState()
    data class Error(val message: String) : AgentState()
}
