package com.adchain.sdk.network

import android.os.Build
import android.util.Log
import com.adchain.sdk.core.AdchainSdk
import com.adchain.sdk.network.models.request.DeviceInfo
import com.adchain.sdk.network.models.request.LoginInfo
import com.adchain.sdk.network.models.request.LoginRequest
import com.adchain.sdk.network.models.request.TrackEventRequest
import com.adchain.sdk.network.models.request.ValidateAppRequest
import com.adchain.sdk.network.models.response.LoginResponse
import com.adchain.sdk.network.models.response.ValidateAppResponse
import com.adchain.sdk.utils.DeviceUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale
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
            
            val response = apiService!!.validateApp()
            
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
    
    // 서버 LoginDto 구조에 맞춰 업데이트
    suspend fun login(
        userId: String,
        eventName: String,
        sdkVersion: String,
        gender: String? = null,  // 추가
        birthYear: Int? = null,  // 추가
        category: String? = null,
        properties: Map<String, String>? = null
    ): Result<LoginResponse> = withContext(Dispatchers.IO) {
        try {
            if (apiService == null) {
                return@withContext Result.failure(Exception("Network manager not initialized"))
            }
            
            val context = AdchainSdk.getApplication()
            if (context == null) {
                Log.e(TAG, "Application context is null")
                return@withContext Result.failure(Exception("Application context not available"))
            }
            
            // Get advertising ID
            val advertisingId = try {
                DeviceUtils.getAdvertisingIdSync(context) ?: 
                DeviceUtils.getAdvertisingId(context)
            } catch (e: Exception) {
                Log.w(TAG, "Failed to get advertising ID", e)
                null
            }
            
            // Prepare parameters with category (iOS와 동일)
            val finalParameters = mutableMapOf<String, String>()
            properties?.let { finalParameters.putAll(it) }
            category?.let { finalParameters["category"] = it }
            
            // Create LoginInfo (iOS와 동일한 구조)
            val loginInfo = LoginInfo(
                name = eventName,
                sdkVersion = sdkVersion,
                timestamp = System.currentTimeMillis().toString(),
                sessionId = sessionId,
                userId = if (userId.isEmpty()) null else userId,
                deviceId = DeviceUtils.getDeviceId(context),
                ifa = advertisingId,
                platform = "Android",
                osVersion = DeviceUtils.getOsVersion(),
                parameters = if (finalParameters.isEmpty()) null else finalParameters
            )
            
            // Create DeviceInfo (iOS와 동일한 구조)
            val deviceInfo = DeviceInfo(
                deviceId = DeviceUtils.getDeviceId(context),
                deviceModel = DeviceUtils.getDeviceModel(),
                deviceModelName = DeviceUtils.getDeviceModelName(),
                manufacturer = DeviceUtils.getDeviceManufacturer(),
                platform = "Android",
                osVersion = DeviceUtils.getOsVersion(),
                country = DeviceUtils.getCountryCode(),
                language = DeviceUtils.getLanguageCode(),
                installer = DeviceUtils.getInstaller(context),
                ifa = advertisingId
            )
            
            val request = LoginRequest(
                userId = userId,
                gender = gender,
                birthYear = birthYear?.toString(),  // Int를 String으로 변환
                loginInfo = loginInfo,
                deviceInfo = deviceInfo
            )
            
            Log.d(TAG, "=== Sending Login Request ===")
            Log.d(TAG, "Device Info: $deviceInfo")
            
            val response = apiService!!.login(request)
            
            if (response.isSuccessful) {
                response.body()?.let {
                    Log.d(TAG, "=== Login Success ===")
                    it.user?.let { user ->
                        Log.d(TAG, "User ID: ${user.userId}")
                        Log.d(TAG, "User Status: ${user.status ?: "unknown"}")
                    }
                    Result.success(it)
                } ?: Result.failure(Exception("Empty response body"))
            } else {
                Log.e(TAG, "Login failed: ${response.code()}")
                Result.failure(Exception("Login failed: ${response.code()}"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Network error during login", e)
            Result.failure(e)
        }
    }
    
    suspend fun trackEvent(
        userId: String,
        eventName: String,
        sdkVersion: String,
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
            
            // Prepare parameters with category if provided - convert to Map<String, String>
            val finalParameters: Map<String, String>? = if (category != null || properties != null) {
                val params = mutableMapOf<String, String>()
                properties?.forEach { (key, value) ->
                    params[key] = value.toString()
                }
                if (category != null) {
                    params["category"] = category
                }
                params
            } else {
                null
            }
            
            val request = TrackEventRequest(
                name = eventName,
                sdkVersion = sdkVersion,
                timestamp = System.currentTimeMillis().toString(),
                sessionId = sessionId,
                userId = if (userId.isEmpty()) null else userId,
                deviceId = DeviceUtils.getDeviceId(context),
                ifa = advertisingId,
                platform = "Android",
                osVersion = DeviceUtils.getOsVersion(),
                parameters = finalParameters
            )
            
            // Debug logging to verify platform field
            Log.d(TAG, "TrackEvent Request - Platform: ${request.platform}")
            Log.d(TAG, "TrackEvent Request - Full: $request")
            
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
                "adId" to adId,
                "unitId" to unitId,
                "timestamp" to (System.currentTimeMillis() / 1000).toInt()
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
                "adId" to adId,
                "unitId" to unitId,
                "timestamp" to (System.currentTimeMillis() / 1000).toInt()
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