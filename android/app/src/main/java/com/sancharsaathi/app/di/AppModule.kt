package com.sancharsaathi.app.di

import android.content.Context
import com.sancharsaathi.app.BuildConfig
import com.sancharsaathi.app.data.local.HistoryStore
import com.sancharsaathi.app.data.remote.AnalysisApiService
import com.sancharsaathi.app.data.repository.AnalysisRepository
import com.sancharsaathi.app.data.repository.AnalysisRepositoryImpl
import com.sancharsaathi.app.data.repository.ReportRepository
import com.sancharsaathi.app.data.repository.ReportRepositoryImpl
import com.sancharsaathi.app.domain.capture.DemoContentSource
import com.sancharsaathi.app.domain.usecase.AnalyzeContentUseCase
import com.sancharsaathi.app.domain.usecase.SubmitReportUseCase
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object AppModule {
    lateinit var appContext: Context
        private set

    val historyStore by lazy { HistoryStore() }
    val demoContentSource by lazy { DemoContentSource() }

    private val okHttpClient by lazy {
        val builder = OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)

        if (BuildConfig.DEBUG) {
            val logging = HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.BODY
            }
            builder.addInterceptor(logging)
        }
        builder.build()
    }

    private val retrofit by lazy {
        Retrofit.Builder()
            .baseUrl(BuildConfig.BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }

    val apiService: AnalysisApiService by lazy {
        retrofit.create(AnalysisApiService::class.java)
    }

    val analysisRepository: AnalysisRepository by lazy {
        AnalysisRepositoryImpl(apiService, historyStore)
    }

    val reportRepository: ReportRepository by lazy {
        ReportRepositoryImpl(apiService)
    }

    val analyzeContentUseCase by lazy {
        AnalyzeContentUseCase(analysisRepository)
    }

    val submitReportUseCase by lazy {
        SubmitReportUseCase(reportRepository)
    }

    fun initialize(context: Context) {
        appContext = context.applicationContext
    }
}
