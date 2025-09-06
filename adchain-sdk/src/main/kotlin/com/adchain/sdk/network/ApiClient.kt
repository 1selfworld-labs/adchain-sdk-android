package com.adchain.sdk.network

import com.adchain.sdk.BuildConfig
import com.adchain.sdk.core.AdchainSdk
import com.adchain.sdk.network.interceptors.AuthInterceptor
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.util.concurrent.TimeUnit

object ApiClient {
    private var retrofit: Retrofit? = null
    
    private val moshi = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()
    
    private fun createOkHttpClient(): OkHttpClient {
        val builder = OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .addInterceptor(AuthInterceptor())
        
        // Add logging interceptor in debug mode
        if (BuildConfig.DEBUG) {
            val loggingInterceptor = HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.BODY
            }
            builder.addInterceptor(loggingInterceptor)
        }
        
        return builder.build()
    }
    
    fun getRetrofit(): Retrofit {
        if (retrofit == null) {
            val config = AdchainSdk.getConfig()
            val environment = config?.environment ?: com.adchain.sdk.core.AdchainSdkConfig.Environment.PRODUCTION
            
            // Map SDK environment to API environment
            ApiConfig.currentEnvironment = when (environment) {
                com.adchain.sdk.core.AdchainSdkConfig.Environment.PRODUCTION -> ApiConfig.Environment.PRODUCTION
                com.adchain.sdk.core.AdchainSdkConfig.Environment.STAGING -> ApiConfig.Environment.STAGING
                com.adchain.sdk.core.AdchainSdkConfig.Environment.DEVELOPMENT -> ApiConfig.Environment.DEVELOPMENT
            }
            
            retrofit = Retrofit.Builder()
                .baseUrl(ApiConfig.baseUrl)
                .client(createOkHttpClient())
                .addConverterFactory(MoshiConverterFactory.create(moshi))
                .build()
        }
        return retrofit!!
    }
    
    fun <T> createService(serviceClass: Class<T>): T {
        return getRetrofit().create(serviceClass)
    }
    
    fun resetForTesting() {
        retrofit = null
    }
}