package com.example.data.network

import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.util.concurrent.TimeUnit

object ApiClient {

    @Volatile
    private var baseUrl: String = "http://10.0.2.2:8080/"

    @Volatile
    private var localToken: String = ""

    private val moshi: Moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()

    private val authAndHostInterceptor = Interceptor { chain ->
        var request = chain.request()
        val originalHttpUrl = request.url

        val currentBase = baseUrl.toHttpUrlOrNull()
        if (currentBase != null) {
            val newHttpUrl = originalHttpUrl.newBuilder()
                .scheme(currentBase.scheme)
                .host(currentBase.host)
                .port(currentBase.port)
                .build()
            request = request.newBuilder().url(newHttpUrl).build()
        }

        val requestBuilder = request.newBuilder()
        if (localToken.isNotBlank()) {
            requestBuilder.header("X-ServerUI-Local-Token", localToken)
        }
        chain.proceed(requestBuilder.build())
    }

    val okHttpClient: OkHttpClient = OkHttpClient.Builder()
        .addInterceptor(authAndHostInterceptor)
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    private var retrofit: Retrofit = Retrofit.Builder()
        .baseUrl(baseUrl)
        .client(okHttpClient)
        .addConverterFactory(MoshiConverterFactory.create(moshi))
        .build()

    @Volatile
    var api: ServerUIApi = retrofit.create(ServerUIApi::class.java)
        private set

    fun updateConfig(url: String, token: String) {
        var cleanUrl = url.trim()
        if (!cleanUrl.startsWith("http://") && !cleanUrl.startsWith("https://")) {
            cleanUrl = "http://$cleanUrl"
        }
        if (!cleanUrl.endsWith("/")) {
            cleanUrl += "/"
        }
        baseUrl = cleanUrl
        localToken = token.trim()

        retrofit = Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
        api = retrofit.create(ServerUIApi::class.java)
    }

    fun getBaseUrl(): String = baseUrl

    fun getLocalToken(): String = localToken

    fun getWebSocketUrl(serverId: String): String {
        val httpUrl = baseUrl.toHttpUrlOrNull() ?: "http://10.0.2.2:8080/".toHttpUrlOrNull()!!
        val wsScheme = if (httpUrl.scheme == "https") "wss" else "ws"
        return "$wsScheme://${httpUrl.host}:${httpUrl.port}/ws/terminal?serverId=$serverId"
    }
}
