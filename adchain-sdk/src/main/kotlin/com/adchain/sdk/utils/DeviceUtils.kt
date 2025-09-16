package com.adchain.sdk.utils

import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import android.provider.Settings
import android.util.Log
import com.google.android.gms.ads.identifier.AdvertisingIdClient
import com.google.android.gms.common.GooglePlayServicesNotAvailableException
import com.google.android.gms.common.GooglePlayServicesRepairableException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.security.MessageDigest
import java.util.UUID

/**
 * Utility class for device-related operations
 */
object DeviceUtils {
    private const val TAG = "AdchainDeviceUtils"
    private const val PREFS_NAME = "adchain_sdk_prefs"
    private const val KEY_DEVICE_ID = "device_id"
    private const val KEY_ADVERTISING_ID = "advertising_id"
    private const val KEY_ADVERTISING_ID_TIMESTAMP = "advertising_id_timestamp"
    private const val ADVERTISING_ID_CACHE_DURATION = 3600000L // 1 hour in milliseconds
    
    private var cachedDeviceId: String? = null
    private var cachedAdvertisingId: String? = null
    private var advertisingIdTimestamp: Long = 0
    
    /**
     * Get a persistent device ID using Android ID
     * Android ID is a unique 64-bit hex string that is generated when the device first boots
     * It persists across app reinstalls but resets on factory reset
     * On Android 8.0+, it's unique per app signing key
     */
    @SuppressLint("HardwareIds")
    fun getDeviceId(context: Context): String {
        cachedDeviceId?.let { return it }
        
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        var deviceId = prefs.getString(KEY_DEVICE_ID, null)
        
        if (deviceId == null) {
            // Try to get Android ID first (real device ID)
            deviceId = try {
                val androidId = Settings.Secure.getString(
                    context.contentResolver,
                    Settings.Secure.ANDROID_ID
                )
                
                if (!androidId.isNullOrEmpty() && androidId != "9774d56d682e549c") {
                    // Valid Android ID found
                    // "9774d56d682e549c" is a known bad value on some devices
                    Log.d(TAG, "Using Android ID as device ID")
                    
                    // Hash the Android ID for additional privacy
                    // This makes it consistent but not directly reversible
                    hashString(androidId)
                } else {
                    // Fallback to generated UUID if Android ID is not available
                    Log.w(TAG, "Android ID not available, using generated UUID")
                    UUID.randomUUID().toString()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error getting Android ID, using generated UUID", e)
                UUID.randomUUID().toString()
            }
            
            prefs.edit().putString(KEY_DEVICE_ID, deviceId).apply()
            Log.d(TAG, "Device ID stored: ${deviceId?.take(8)}...")
        }
        
        cachedDeviceId = deviceId
        return deviceId ?: ""
    }
    
    /**
     * Hash a string using SHA-256
     */
    private fun hashString(input: String): String {
        return try {
            val bytes = MessageDigest.getInstance("SHA-256").digest(input.toByteArray())
            bytes.joinToString("") { "%02x".format(it) }
        } catch (e: Exception) {
            Log.e(TAG, "Error hashing string", e)
            input // Return original if hashing fails
        }
    }
    
    /**
     * Get Google Advertising ID (GAID)
     * This method should be called from a coroutine
     * Returns null if advertising ID is not available or user has opted out
     */
    suspend fun getAdvertisingId(context: Context): String? = withContext(Dispatchers.IO) {
        try {
            // Check cache first
            val currentTime = System.currentTimeMillis()
            if (cachedAdvertisingId != null && 
                (currentTime - advertisingIdTimestamp) < ADVERTISING_ID_CACHE_DURATION) {
                return@withContext cachedAdvertisingId
            }
            
            // Try to get from SharedPreferences
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val storedAdId = prefs.getString(KEY_ADVERTISING_ID, null)
            val storedTimestamp = prefs.getLong(KEY_ADVERTISING_ID_TIMESTAMP, 0)
            
            if (storedAdId != null && (currentTime - storedTimestamp) < ADVERTISING_ID_CACHE_DURATION) {
                cachedAdvertisingId = storedAdId
                advertisingIdTimestamp = storedTimestamp
                return@withContext storedAdId
            }
            
            // Check if Google Play Services is available before attempting to use it
            if (!isGooglePlayServicesAvailable()) {
                Log.d(TAG, "Google Play Services not available, returning zero ID")
                return@withContext "00000000-0000-0000-0000-000000000000"
            }
            
            // Fetch new advertising ID using reflection to avoid NoClassDefFoundError
            val adInfo = getAdvertisingIdInfoSafely(context) ?: return@withContext "00000000-0000-0000-0000-000000000000"
            
            // Check if user has opted out of ad tracking using reflection
            val isLimitedMethod = adInfo.javaClass.getMethod("isLimitAdTrackingEnabled")
            val isLimited = isLimitedMethod.invoke(adInfo) as? Boolean ?: false
            
            if (isLimited) {
                Log.d(TAG, "User has opted out of ad tracking, returning zero ID")
                return@withContext "00000000-0000-0000-0000-000000000000"
            }
            
            // Get advertising ID using reflection
            val getIdMethod = adInfo.javaClass.getMethod("getId")
            val advertisingId = getIdMethod.invoke(adInfo) as? String
            
            // Cache the advertising ID
            if (advertisingId != null) {
                cachedAdvertisingId = advertisingId
                advertisingIdTimestamp = currentTime
                
                // Store in SharedPreferences
                prefs.edit()
                    .putString(KEY_ADVERTISING_ID, advertisingId)
                    .putLong(KEY_ADVERTISING_ID_TIMESTAMP, currentTime)
                    .apply()
                
                Log.d(TAG, "Retrieved advertising ID: ${advertisingId.take(8)}...")
            }
            
            advertisingId
        } catch (e: ClassNotFoundException) {
            Log.e(TAG, "Google Play Services classes not found, returning zero ID", e)
            "00000000-0000-0000-0000-000000000000"
        } catch (e: NoClassDefFoundError) {
            Log.e(TAG, "Google Play Services not available in classpath, returning zero ID", e)
            "00000000-0000-0000-0000-000000000000"
        } catch (e: Exception) {
            Log.e(TAG, "Error retrieving advertising ID: ${e.message}", e)
            "00000000-0000-0000-0000-000000000000"
        }
    }
    
    /**
     * Check if Google Play Services is available using reflection
     */
    private fun isGooglePlayServicesAvailable(): Boolean {
        return try {
            Class.forName("com.google.android.gms.ads.identifier.AdvertisingIdClient")
            Class.forName("com.google.android.gms.common.GooglePlayServicesUtil")
            true
        } catch (e: ClassNotFoundException) {
            Log.d(TAG, "Google Play Services classes not found")
            false
        } catch (e: NoClassDefFoundError) {
            Log.d(TAG, "Google Play Services not available")
            false
        }
    }
    
    /**
     * Get advertising ID info safely using reflection to avoid NoClassDefFoundError
     */
    private fun getAdvertisingIdInfoSafely(context: Context): Any? {
        return try {
            val clazz = Class.forName("com.google.android.gms.ads.identifier.AdvertisingIdClient")
            val method = clazz.getMethod("getAdvertisingIdInfo", Context::class.java)
            method.invoke(null, context)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get advertising ID info via reflection: ${e.message}")
            null
        }
    }
    
    /**
     * Get OS version string
     */
    fun getOsVersion(): String {
        return Build.VERSION.RELEASE
    }
    
    /**
     * Get device model
     */
    fun getDeviceModel(): String {
        return Build.MODEL
    }
    
    /**
     * Get device manufacturer
     */
    fun getDeviceManufacturer(): String {
        return Build.MANUFACTURER
    }
    
    /**
     * Get device model name (brand + model)
     */
    fun getDeviceModelName(): String {
        return "${Build.MANUFACTURER} ${Build.MODEL}"
    }
    
    /**
     * Check if user has limited ad tracking enabled
     */
    suspend fun isLimitAdTrackingEnabled(context: Context?): Boolean = withContext(Dispatchers.IO) {
        if (context == null) return@withContext false
        
        try {
            // Check if Google Play Services is available
            if (!isGooglePlayServicesAvailable()) {
                Log.d(TAG, "Google Play Services not available for checking ad tracking status")
                return@withContext false
            }
            
            // Get advertising ID info safely using reflection
            val adInfo = getAdvertisingIdInfoSafely(context) ?: return@withContext false
            
            // Get limit ad tracking status using reflection
            val isLimitedMethod = adInfo.javaClass.getMethod("isLimitAdTrackingEnabled")
            isLimitedMethod.invoke(adInfo) as? Boolean ?: false
        } catch (e: ClassNotFoundException) {
            Log.e(TAG, "Google Play Services classes not found", e)
            false
        } catch (e: NoClassDefFoundError) {
            Log.e(TAG, "Google Play Services not available", e)
            false
        } catch (e: Exception) {
            Log.e(TAG, "Error checking limit ad tracking status", e)
            false
        }
    }
    
    /**
     * Get advertising ID synchronously (for compatibility)
     * This only returns cached value, doesn't fetch new one
     */
    fun getAdvertisingIdSync(context: Context?): String? {
        if (context == null) return "00000000-0000-0000-0000-000000000000"
        
        // Return cached value if available
        val currentTime = System.currentTimeMillis()
        if (cachedAdvertisingId != null && 
            (currentTime - advertisingIdTimestamp) < ADVERTISING_ID_CACHE_DURATION) {
            return cachedAdvertisingId
        }
        
        // Try to get from SharedPreferences
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val storedAdId = prefs.getString(KEY_ADVERTISING_ID, null)
        val storedTimestamp = prefs.getLong(KEY_ADVERTISING_ID_TIMESTAMP, 0)
        
        if (storedAdId != null && (currentTime - storedTimestamp) < ADVERTISING_ID_CACHE_DURATION) {
            cachedAdvertisingId = storedAdId
            advertisingIdTimestamp = storedTimestamp
            return storedAdId
        }
        
        // If no cached value available, return zero ID
        return "00000000-0000-0000-0000-000000000000"
    }
    
    /**
     * Get country code from system locale
     */
    fun getCountryCode(): String? {
        return try {
            java.util.Locale.getDefault().country.takeIf { it.isNotEmpty() }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting country code", e)
            null
        }
    }
    
    /**
     * Get language code from system locale
     */
    fun getLanguageCode(): String? {
        return try {
            java.util.Locale.getDefault().language.takeIf { it.isNotEmpty() }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting language code", e)
            null
        }
    }
    
    /**
     * Get installer package name (e.g., "com.android.vending" for Google Play)
     * Returns "unknown" if installer cannot be determined
     */
    fun getInstaller(context: Context): String? {
        return try {
            val installer = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                context.packageManager.getInstallSourceInfo(context.packageName).installingPackageName
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getInstallerPackageName(context.packageName)
            }
            
            when (installer) {
                null -> {
                    // 앱이 ADB나 APK 직접 설치 등으로 설치된 경우
                    Log.d(TAG, "No installer found, likely installed via ADB or direct APK")
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        // Android R (API 30) 이상에서는 InstallSourceInfo를 통해 더 자세한 정보 확인 가능
                        try {
                            val sourceInfo = context.packageManager.getInstallSourceInfo(context.packageName)
                            when {
                                sourceInfo.initiatingPackageName != null -> sourceInfo.initiatingPackageName
                                sourceInfo.originatingPackageName != null -> sourceInfo.originatingPackageName
                                else -> "sideload"  // APK 직접 설치
                            }
                        } catch (e: Exception) {
                            "sideload"
                        }
                    } else {
                        "sideload"  // 구버전에서는 sideload로 표시
                    }
                }
                "com.android.vending" -> "google_play"  // Google Play Store
                "com.amazon.venezia" -> "amazon_appstore"  // Amazon Appstore
                "com.sec.android.app.samsungapps" -> "galaxy_store"  // Samsung Galaxy Store
                "com.xiaomi.market" -> "mi_store"  // Xiaomi Mi Store
                "com.huawei.appmarket" -> "huawei_appgallery"  // Huawei AppGallery
                "com.oppo.market" -> "oppo_store"  // OPPO App Market
                "com.vivo.appstore" -> "vivo_store"  // Vivo App Store
                "com.android.packageinstaller" -> "package_installer"  // Default package installer
                "com.google.android.packageinstaller" -> "package_installer"  // Google package installer
                else -> installer  // 기타 installer는 패키지명 그대로 반환
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting installer package: ${e.message}", e)
            "unknown"
        }
    }
    
    /**
     * Clear cached values (useful for testing)
     */
    fun clearCache() {
        cachedDeviceId = null
        cachedAdvertisingId = null
        advertisingIdTimestamp = 0
    }
}