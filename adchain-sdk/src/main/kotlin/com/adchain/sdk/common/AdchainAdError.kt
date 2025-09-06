package com.adchain.sdk.common

/**
 * Common error class for Adchain SDK
 */
class AdchainAdError(
    val code: Int,
    override val message: String
) : Exception(message) {
    
    companion object {
        // Error codes
        const val CODE_NOT_INITIALIZED = 1001
        const val CODE_NETWORK_ERROR = 1002
        const val CODE_EMPTY_RESPONSE = 1003
        const val CODE_UNKNOWN = 9999
        
        // Predefined errors
        val NOT_INITIALIZED = AdchainAdError(CODE_NOT_INITIALIZED, "SDK not initialized or user not logged in")
        val NETWORK_ERROR = AdchainAdError(CODE_NETWORK_ERROR, "Network error occurred")
        val EMPTY_RESPONSE = AdchainAdError(CODE_EMPTY_RESPONSE, "Empty response from server")
        val UNKNOWN = AdchainAdError(CODE_UNKNOWN, "Unknown error occurred")
    }
}