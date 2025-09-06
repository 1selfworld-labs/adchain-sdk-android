package com.adchain.sdk.core

class AdchainSdkConfig constructor(
    val appKey: String,
    val appSecret: String,
    val environment: Environment = Environment.PRODUCTION,
    val timeout: Long = 30000L
) {
    // 하위 호환성을 위한 deprecated property
    @Deprecated("Use appKey instead", ReplaceWith("appKey"))
    val appId: String
        get() = appKey
        
    enum class Environment {
        PRODUCTION,
        STAGING,
        DEVELOPMENT
    }
    
    class Builder {
        private var appKey: String? = null
        private var appSecret: String? = null
        private var environment: Environment = Environment.PRODUCTION
        private var timeout: Long = 30000L
        
        // 새로운 생성자 (appKey 사용)
        constructor(appKey: String, appSecret: String) {
            this.appKey = appKey
            this.appSecret = appSecret
        }
        
        // 하위 호환성을 위한 deprecated 메소드
        @Deprecated("Use setAppKey instead", ReplaceWith("setAppKey(appId)"))
        fun setAppId(appId: String) = apply {
            this.appKey = appId
        }
        
        fun setAppKey(appKey: String) = apply {
            this.appKey = appKey
        }
        
        fun setEnvironment(environment: Environment) = apply {
            this.environment = environment
        }
        
        fun setTimeout(timeout: Long) = apply {
            this.timeout = timeout
        }
        
        fun build(): AdchainSdkConfig {
            val finalAppKey = requireNotNull(appKey) { "App Key cannot be empty" }
            val finalAppSecret = requireNotNull(appSecret) { "App Secret cannot be empty" }
            return AdchainSdkConfig(finalAppKey, finalAppSecret, environment, timeout)
        }
    }
}