package com.mexagent.app.network

import com.mexagent.app.network.models.*
import retrofit2.Response
import retrofit2.http.*

interface ApiService {

    @POST("/start")
    suspend fun startAgent(@Body request: StartRequest): Response<StartResponse>

    @POST("/stop")
    suspend fun stopAgent(@Body request: StopRequest): Response<StopResponse>

    @GET("/status")
    suspend fun getStatus(): Response<StatusResponse>

    @GET("/logs")
    suspend fun getLogs(
        @Query("session_id") sessionId: String? = null,
        @Query("since_id") sinceId: Long? = null,
        @Query("limit") limit: Int = 50
    ): Response<LogsResponse>

    @GET("/highlight")
    suspend fun getHighlight(): Response<HighlightResponse>
}
