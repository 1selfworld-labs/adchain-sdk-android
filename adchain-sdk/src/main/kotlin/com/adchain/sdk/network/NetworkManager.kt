package com.adchain.sdk.network

import android.os.Build
import android.util.Log
import com.adchain.sdk.core.AdchainSdk
import com.adchain.sdk.network.models.request.DeviceInfo
import com.adchain.sdk.network.models.request.TrackEventRequest
import com.adchain.sdk.network.models.request.ValidateAppRequest
import com.adchain.sdk.network.models.response.ValidateAppResponse
import com.adchain.sdk.utils.DeviceUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.UUID

object NetworkManager {
    private const val TAG = "AdchainNetworkManager"
    private var apiService: ApiService? = null
    private var isInitialized = false
    private var sessionId: String = UUID.randomUUID().toString()
    
    fun getSessionId(): String = sessionId
    
    fun initialize() {
        if (!isInitialized) {
            try {
                apiService = ApiClient.createService(ApiService::class.java)
                isInitialized = true
            } catch (e: Exception) {
                Log.e(TAG, "Failed to initialize network manager", e)
            }
        }
    }
    
    suspend fun validateApp(): Result<ValidateAppResponse> = withContext(Dispatchers.IO) {
        try {
            if (apiService == null) {
                return@withContext Result.failure(Exception("Network manager not initialized"))
            }
            
            val deviceInfo = DeviceInfo(
                deviceId = getDeviceId(),
                deviceModel = Build.MODEL,
                deviceModelName = DeviceUtils.getDeviceModelName(),
                osVersion = Build.VERSION.RELEASE,
                appVersion = getAppVersion(),
                advertisingId = DeviceUtils.getAdvertisingIdSync(AdchainSdk.getApplication()),
                isLimitAdTrackingEnabled = false  // For now, set to false as isLimitAdTrackingEnabled is also suspend
            )
            
            val request = ValidateAppRequest(deviceInfo = deviceInfo)
            val response = apiService!!.validateApp(request)
            
            if (response.isSuccessful) {
                response.body()?.let {
                    Log.d(TAG, "App validated successfully: ${it.app?.appName}")
                    Result.success(it)
                } ?: Result.failure(Exception("Empty response body"))
            } else {
                Log.e(TAG, "App validation failed: ${response.code()}")
                Result.failure(Exception("Validation failed: ${response.code()}"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Network error during app validation", e)
            Result.failure(e)
        }
    }
    
    suspend fun trackEvent(
        userId: String,
        eventName: String,
        category: String? = null,
        properties: Map<String, Any>? = null
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            if (apiService == null) {
                return@withContext Result.success(Unit) // Silent fail for tracking
            }
            
            val context = AdchainSdk.getApplication()
            if (context == null) {
                Log.e(TAG, "Application context is null")
                return@withContext Result.failure(Exception("Application context not available"))
            }
            
            // Get advertising ID (use sync version from cache first)
            val advertisingId = try {
                // First try to get from cache synchronously
                DeviceUtils.getAdvertisingIdSync(context) ?: 
                // If not in cache, fetch asynchronously
                DeviceUtils.getAdvertisingId(context)
            } catch (e: Exception) {
                Log.w(TAG, "Failed to get advertising ID", e)
                null
            }
            
            // Prepare parameters with category if provided
            val finalParameters = if (category != null) {
                val params = properties?.toMutableMap() ?: mutableMapOf()
                params["category"] = category
                params
            } else {
                properties
            }
            
            val request = TrackEventRequest(
                name = eventName,
                timestamp = System.currentTimeMillis(),
                sessionId = sessionId,
                userId = userId,
                deviceId = DeviceUtils.getDeviceId(context),
                advertisingId = advertisingId,
                os = "Android",
                osVersion = DeviceUtils.getOsVersion(),
                parameters = finalParameters
            )
            
            val response = apiService!!.trackEvent(request)
            
            if (response.isSuccessful) {
                Log.d(TAG, "Event tracked: $eventName")
                Result.success(Unit)
            } else {
                Log.e(TAG, "Event tracking failed: ${response.code()}")
                Result.failure(Exception("Event tracking failed: ${response.code()}"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Network error during event tracking", e)
            Result.failure(e)
        }
    }
    
    // Native Ad tracking methods - removed as iOS SDK doesn't have these
    // If needed in the future, uncomment and add corresponding endpoints in ApiService
    /*
    suspend fun trackAdImpression(adId: String, unitId: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            if (apiService == null) {
                return@withContext Result.success(Unit) // Silent fail for tracking
            }
            
            val request = mapOf(
                "ad_id" to adId,
                "unit_id" to unitId,
                "timestamp" to System.currentTimeMillis()
            )
            
            // TODO: Add trackImpression endpoint to ApiService if needed
            // val response = apiService!!.trackImpression(request)
            
            Log.d(TAG, "Ad impression tracking not implemented yet: $adId")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Network error during impression tracking", e)
            // Return success even on failure to not break user experience
            Result.success(Unit)
        }
    }
    
    suspend fun trackAdClick(adId: String, unitId: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            if (apiService == null) {
                return@withContext Result.success(Unit) // Silent fail for tracking
            }
            
            val request = mapOf(
                "ad_id" to adId,
                "unit_id" to unitId,
                "timestamp" to System.currentTimeMillis()
            )
            
            // TODO: Add trackClick endpoint to ApiService if needed
            // val response = apiService!!.trackClick(request)
            
            Log.d(TAG, "Ad click tracking not implemented yet: $adId")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Network error during click tracking", e)
            // Return success even on failure to not break user experience
            Result.success(Unit)
        }
    }
    */
    
    private fun getDeviceId(): String {
        val context = AdchainSdk.getApplication()
        return context?.let { DeviceUtils.getDeviceId(it) } ?: UUID.randomUUID().toString()
    }
    
    private fun getAppVersion(): String? {
        return try {
            val context = AdchainSdk.getApplication()
            context?.packageManager?.getPackageInfo(context.packageName, 0)?.versionName
        } catch (e: Exception) {
            null
        }
    }
    
    fun resetForTesting() {
        isInitialized = false
        sessionId = UUID.randomUUID().toString()
    }
}