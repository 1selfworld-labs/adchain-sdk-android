package com.adchain.sdk.banner

import android.util.Log
import com.adchain.sdk.core.AdchainSdk
import com.adchain.sdk.common.AdchainAdError
import com.adchain.sdk.network.ApiClient
import com.adchain.sdk.network.ApiService
import com.adchain.sdk.banner.models.BannerResponse
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
        onSuccess: (BannerResponse) -> Unit,
        onFailure: (AdchainAdError) -> Unit
    ) {
        // Check if SDK is initialized and user is logged in
        if (!AdchainSdk.isLoggedIn) {
            Log.e(TAG, "SDK not initialized or user not logged in")
            onFailure(AdchainAdError.NOT_INITIALIZED)
            return
        }
        
        val currentUser = AdchainSdk.getCurrentUser()
        if (currentUser == null) {
            Log.e(TAG, "Current user is null")
            onFailure(AdchainAdError.NOT_INITIALIZED)
            return
        }
        
        coroutineScope.launch {
            try {
                val response = withContext(Dispatchers.IO) {
                    apiService.getBanner(
                        userId = currentUser.userId,
                        placementId = placementId,
                        platform = "Android"
                    )
                }
                
                if (response.isSuccessful) {
                    response.body()?.let { bannerResponse ->
                        if (bannerResponse.success == true) {
                            Log.d(TAG, "Banner loaded successfully: ${bannerResponse.titleText}")
                            onSuccess(bannerResponse)
                        } else {
                            Log.e(TAG, "Banner response error: ${bannerResponse.message}")
                            onFailure(AdchainAdError.NETWORK_ERROR)
                        }
                    } ?: run {
                        Log.e(TAG, "Empty response body")
                        onFailure(AdchainAdError.EMPTY_RESPONSE)
                    }
                } else {
                    Log.e(TAG, "Failed to load banner: ${response.code()}")
                    onFailure(AdchainAdError.NETWORK_ERROR)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Exception while loading banner", e)
                onFailure(AdchainAdError.UNKNOWN)
            }
        }
    }
}