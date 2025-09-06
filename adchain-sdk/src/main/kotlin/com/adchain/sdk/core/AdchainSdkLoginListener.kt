package com.adchain.sdk.core

interface AdchainSdkLoginListener {
    fun onSuccess()
    fun onFailure(errorType: ErrorType)
    
    enum class ErrorType {
        NOT_INITIALIZED,
        INVALID_USER_ID,
        NETWORK_ERROR,
        AUTHENTICATION_FAILED,
        UNKNOWN
    }
}