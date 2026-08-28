package com.sancharsaathi.app.di

import android.content.Context
import com.sancharsaathi.app.BuildConfig
import com.sancharsaathi.app.data.local.HistoryStore
import com.sancharsaathi.app.data.local.NetworkConfigStore
import com.sancharsaathi.app.data.local.SmsInboxReader
import com.sancharsaathi.app.data.remote.AnalysisApiService
import com.sancharsaathi.app.data.repository.AnalysisRepository
import com.sancharsaathi.app.data.repository.AnalysisRepositoryImpl
import com.sancharsaathi.app.data.repository.ReportRepository
import com.sancharsaathi.app.data.repository.ReportRepositoryImpl
import com.sancharsaathi.app.domain.capture.DemoContentSource
import com.sancharsaathi.app.domain.usecase.AnalyzeContentUseCase
import com.sancharsaathi.app.domain.usecase.SubmitReportUseCase
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Response
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

class DynamicHostInterceptor(private val networkConfigStore: NetworkConfigStore) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        var request = chain.request()
        val currentBaseUrl = networkConfigStore.getBaseUrl()
        val newHttpUrl = currentBaseUrl.toHttpUrlOrNull()
        if (newHttpUrl != null) {
            val newUrl = request.url.newBuilder()
                .scheme(newHttpUrl.scheme)
                .host(newHttpUrl.host)
                .port(newHttpUrl.port)
                .build()
            request = request.newBuilder().url(newUrl).build()
        }
        android.util.Log.d("NetworkConfig", "BACKEND_REQUEST\nmode=${networkConfigStore.connectionMode.name}\ntarget=${networkConfigStore.getBaseUrl()}\nendpoint=${request.url.encodedPath}")
        return chain.proceed(request)
    }
}

object AppModule {
    lateinit var appContext: Context
        private set

    val historyStore by lazy { HistoryStore(if (::appContext.isInitialized) appContext else null) }
    val smsInboxReader by lazy { SmsInboxReader(appContext, historyStore) }
    val networkConfigStore by lazy { NetworkConfigStore(appContext) }
    val demoContentSource by lazy { DemoContentSource() }

    private val okHttpClient by lazy {
        val builder = OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .addInterceptor(DynamicHostInterceptor(networkConfigStore))

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
            .baseUrl("http://127.0.0.1:8000/") // Default fallback, overridden by DynamicHostInterceptor
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
        historyStore.setContext(appContext)
    }
}
