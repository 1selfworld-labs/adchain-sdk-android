package com.adchain.sdk.banner

import com.adchain.sdk.core.AdchainSdk
import com.adchain.sdk.utils.AdchainLogger
import com.adchain.sdk.common.AdchainAdError
import com.adchain.sdk.network.ApiClient
import com.adchain.sdk.network.ApiService
import com.adchain.sdk.network.models.response.BannerInfoResponse
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

object AdchainBanner {
    private const val TAG = "AdchainBanner"
    private val coroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val apiService: ApiService by lazy {
        ApiClient.createService(ApiService::class.java)
    }
    
    /**
     * Get banner data from server
     * @param placementId Unique identifier for the banner placement
     * @param onSuccess Callback with banner response
     * @param onFailure Callback with error
     */
    fun getBanner(
        placementId: String,
        onSuccess: (BannerInfoResponse) -> Unit,
        onFailure: (AdchainAdError) -> Unit
    ) {
        // Check if SDK is initialized and user is logged in
        if (!AdchainSdk.isLoggedIn) {
            AdchainLogger.e(TAG, "SDK not initialized or user not logged in")
            onFailure(AdchainAdError.NOT_INITIALIZED)
            return
        }
        
        val currentUser = AdchainSdk.getCurrentUser()
        if (currentUser == null) {
            AdchainLogger.e(TAG, "Current user is null")
            onFailure(AdchainAdError.NOT_INITIALIZED)
            return
        }
        
        coroutineScope.launch {
            try {
                val response = withContext(Dispatchers.IO) {
                    apiService.getBannerInfo(
                        userId = currentUser.userId,
                        placementId = placementId,
                        platform = "android"
                    )
                }

                if (response.isSuccessful) {
                    response.body()?.let { bannerResponse ->
                        if (bannerResponse.success == true) {
                            AdchainLogger.i(TAG, "Banner loaded successfully: ${bannerResponse.titleText}")
                            onSuccess(bannerResponse)
                        } else {
                            AdchainLogger.e(TAG, "Banner response error")
                            onFailure(AdchainAdError.NETWORK_ERROR)
                        }
                    } ?: run {
                        AdchainLogger.e(TAG, "Empty response body")
                        onFailure(AdchainAdError.EMPTY_RESPONSE)
                    }
                } else {
                    AdchainLogger.e(TAG, "Failed to load banner: ${response.code()}")
                    onFailure(AdchainAdError.NETWORK_ERROR)
                }
            } catch (e: Exception) {
                AdchainLogger.e(TAG, "Exception while loading banner", e)
                onFailure(AdchainAdError.UNKNOWN)
            }
        }
    }
}