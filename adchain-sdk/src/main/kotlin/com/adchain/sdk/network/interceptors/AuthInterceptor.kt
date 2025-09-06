package com.adchain.sdk.network.interceptors

import com.adchain.sdk.core.AdchainSdk
import com.adchain.sdk.network.ApiConfig
import okhttp3.Interceptor
import okhttp3.Response

class AuthInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val originalRequest = chain.request()
        val config = AdchainSdk.getConfig()
        
        if (config == null) {
            return chain.proceed(originalRequest)
        }
        
        val request = originalRequest.newBuilder()
            .header("x-adchain-app-key", config.appKey)
            .header("x-adchain-app-secret", config.appSecret)
            .header("x-adchain-sdk-version", ApiConfig.SDK_VERSION)
            .header("x-adchain-platform", ApiConfig.PLATFORM)
            .header("Content-Type", "application/json")
            .build()
        
        return chain.proceed(request)
    }
}