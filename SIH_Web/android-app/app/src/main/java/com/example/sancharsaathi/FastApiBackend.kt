package com.example.sancharsaathi

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.POST

data class AnalyzeRequest(
    val content: String,
    val source: String = "sms",
    val sender: String? = null
)

data class AnalyzeResponse(
    val risk_score: Double,
    val risk_level: String, // "BLOCK", "WARN", "ALLOW"
    val is_spam: Boolean,
    val categories: List<String>,
    val signals: List<Any>,
    val latency_ms: Double,
    val cached: Boolean
)

interface FastApiService {
    @POST("/api/v1/analyze")
    suspend fun analyze(@Body request: AnalyzeRequest): Response<AnalyzeResponse>
}
