package com.sancharsaathi.app.data.remote

import com.sancharsaathi.app.data.remote.dto.*
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path

interface AnalysisApiService {

    @POST("api/v1/analyze")
    suspend fun analyze(@Body body: AnalyzeRequestDto): Response<AnalyzeResponseDto>

    @POST("api/v1/analyze/url")
    suspend fun analyzeUrl(@Body body: UrlAnalyzeRequestDto): Response<UrlAnalyzeResponseDto>

    @POST("api/v1/reports")
    suspend fun submitReport(@Body body: ReportRequestDto): Response<ReportResponseDto>

    @GET("api/v1/reports/{reportId}")
    suspend fun getReport(@Path("reportId") reportId: String): Response<ReportResponseDto>

    @GET("api/v1/health")
    suspend fun health(): Response<HealthResponseDto>
}
