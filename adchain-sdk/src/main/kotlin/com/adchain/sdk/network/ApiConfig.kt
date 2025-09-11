package com.adchain.sdk.network

import com.adchain.sdk.BuildConfig

object ApiConfig {
    const val SDK_VERSION = "1.0.4"
    const val PLATFORM = "android"
    
    enum class Environment(val baseUrl: String) {
        PRODUCTION("https://adchain-api.1self.world/"),
        STAGING("https://staging-api.adchain.com/"),
        DEVELOPMENT("http://10.0.2.2:3000/") // Android emulator localhost
    }
    
    var currentEnvironment: Environment = if (BuildConfig.DEBUG) {
        Environment.DEVELOPMENT
    } else {
        Environment.PRODUCTION
    }
    
    val baseUrl: String
        get() = currentEnvironment.baseUrl
    
    // API Endpoints - iOS와 통일
    object Endpoints {
        const val VALIDATE_APP = "v1/api/sdk/validate"
        const val TRACK_EVENT = "v1/api/sdk/event"  // 변경: v1/api/analytics/event -> v1/api/sdk/event
        const val GET_QUIZ_EVENTS = "v1/api/quiz"
        const val GET_MISSIONS = "v1/api/mission"
        const val GET_BANNER = "v1/api/sdk/banner"  // 변경: v1/api/banner -> v1/api/sdk/banner
    }
}