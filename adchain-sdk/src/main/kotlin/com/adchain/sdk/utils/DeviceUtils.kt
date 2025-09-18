package com.adchain.sdk.utils

import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import com.adchain.sdk.utils.AdchainLogger
import com.google.android.gms.ads.identifier.AdvertisingIdClient
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.security.MessageDigest
import java.util.UUID

/**
 * Utility class for device-related operations
 */
object DeviceUtils {
    private const val TAG = "AdchainDeviceUtils"
    private const val PREFS_NAME = "adchain_sdk_prefs"
    private const val KEY_DEVICE_ID = "device_id"
    // KEY_ADVERTISING_ID와 timestamp 제거 - 더 이상 영구 저장 안함
    private const val ZERO_ID = "00000000-0000-0000-0000-000000000000"

    private var cachedDeviceId: String? = null
    private var cachedAdvertisingId: String? = null  // 앱 생명주기 동안만 유지
    private var isAdvertisingIdInitialized = false  // 초기화 여부 추적
    
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
                    AdchainLogger.v(TAG, "Using Android ID as device ID")
                    
                    // Hash the Android ID for additional privacy
                    // This makes it consistent but not directly reversible
                    hashString(androidId)
                } else {
                    // Fallback to generated UUID if Android ID is not available
                    AdchainLogger.w(TAG, "Android ID not available, using generated UUID")
                    UUID.randomUUID().toString()
                }
            } catch (e: Exception) {
                AdchainLogger.e(TAG, "Error getting Android ID, using generated UUID", e)
                UUID.randomUUID().toString()
            }
            
            prefs.edit().putString(KEY_DEVICE_ID, deviceId).apply()
            AdchainLogger.v(TAG, "Device ID stored: ${deviceId?.take(8)}...")
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
            AdchainLogger.e(TAG, "Error hashing string", e)
            input // Return original if hashing fails
        }
    }
    
    /**
     * Initialize advertising ID on app launch
     * Should be called once when SDK initializes
     */
    suspend fun initializeAdvertisingId(context: Context) = withContext(Dispatchers.IO) {
        AdchainLogger.i(TAG, "[GAID-INIT] Initializing advertising ID on app launch...")

        try {
            // Check Google Play Services availability
            val availability = GoogleApiAvailability.getInstance()
            val playServicesStatus = availability.isGooglePlayServicesAvailable(context)

            if (playServicesStatus != ConnectionResult.SUCCESS) {
                AdchainLogger.e(TAG, "[GAID-INIT] Google Play Services not available")
                cachedAdvertisingId = ZERO_ID
                isAdvertisingIdInitialized = true
                return@withContext
            }

            // Check AD_ID permission for Android 13+
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                val hasPermission = context.checkSelfPermission("com.google.android.gms.permission.AD_ID") ==
                    PackageManager.PERMISSION_GRANTED
                if (!hasPermission) {
                    AdchainLogger.e(TAG, "[GAID-INIT] AD_ID permission not granted")
                    cachedAdvertisingId = ZERO_ID
                    isAdvertisingIdInitialized = true
                    return@withContext
                }
            }

            // Get advertising ID
            val adIdInfo = withTimeout(2000L) {
                AdvertisingIdClient.getAdvertisingIdInfo(context.applicationContext)
            }

            // Check if user has opted out
            if (adIdInfo.isLimitAdTrackingEnabled) {
                AdchainLogger.w(TAG, "[GAID-INIT] User opted out of ad tracking")
                cachedAdvertisingId = ZERO_ID
            } else {
                val advertisingId = adIdInfo.id
                if (!advertisingId.isNullOrEmpty() && advertisingId != ZERO_ID) {
                    cachedAdvertisingId = advertisingId
                    AdchainLogger.i(TAG, "[GAID-INIT] SUCCESS - Advertising ID: ${advertisingId.take(8)}...")
                } else {
                    cachedAdvertisingId = ZERO_ID
                    AdchainLogger.w(TAG, "[GAID-INIT] Invalid advertising ID received")
                }
            }

        } catch (e: TimeoutCancellationException) {
            AdchainLogger.e(TAG, "[GAID-INIT] Timeout getting advertising ID", e)
            cachedAdvertisingId = ZERO_ID
        } catch (e: Exception) {
            AdchainLogger.e(TAG, "[GAID-INIT] Error getting advertising ID", e)
            cachedAdvertisingId = ZERO_ID
        }

        isAdvertisingIdInitialized = true
    }

    /**
     * Get Google Advertising ID (GAID)
     * This method should be called from a coroutine
     * Returns ZERO_ID if advertising ID is not available or user has opted out
     */
    suspend fun getAdvertisingId(context: Context): String? = withContext(Dispatchers.IO) {
        // 초기화되지 않았으면 먼저 초기화
        if (!isAdvertisingIdInitialized) {
            initializeAdvertisingId(context)
        }

        // 메모리 캐시에서 바로 반환 (앱 생명주기 동안 유효)
        AdchainLogger.v(TAG, "[GAID] Returning cached value: ${cachedAdvertisingId?.take(8) ?: "null"}")
        return@withContext cachedAdvertisingId ?: ZERO_ID

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
            // Check Google Play Services availability
            val availability = GoogleApiAvailability.getInstance()
            if (availability.isGooglePlayServicesAvailable(context) != ConnectionResult.SUCCESS) {
                AdchainLogger.v(TAG, "Google Play Services not available for checking ad tracking status")
                return@withContext false
            }

            // Get advertising ID info directly
            val adInfo = withTimeout(2000L) {
                AdvertisingIdClient.getAdvertisingIdInfo(context.applicationContext)
            }

            adInfo.isLimitAdTrackingEnabled
        } catch (e: TimeoutCancellationException) {
            AdchainLogger.e(TAG, "Timeout checking limit ad tracking status")
            false
        } catch (e: Exception) {
            AdchainLogger.e(TAG, "Error checking limit ad tracking status", e)
            false
        }
    }
    
    /**
     * Get advertising ID synchronously (for compatibility)
     * This only returns cached value, doesn't fetch new one
     */
    fun getAdvertisingIdSync(context: Context?): String? {
        AdchainLogger.v(TAG, "[GAID-SYNC] getAdvertisingIdSync called")
        if (context == null) {
            AdchainLogger.w(TAG, "[GAID-SYNC] Context is null, returning ZERO_ID")
            return ZERO_ID
        }

        // 초기화되지 않았으면 ZERO_ID 반환
        if (!isAdvertisingIdInitialized) {
            AdchainLogger.w(TAG, "[GAID-SYNC] Not initialized yet, returning ZERO_ID")
            return ZERO_ID
        }

        // 메모리 캐시에서 바로 반환
        AdchainLogger.v(TAG, "[GAID-SYNC] Returning cached value: ${cachedAdvertisingId?.take(8) ?: "null"}")
        return cachedAdvertisingId ?: ZERO_ID
    }
    
    /**
     * Get country code from system locale
     */
    fun getCountryCode(): String? {
        return try {
            java.util.Locale.getDefault().country.takeIf { it.isNotEmpty() }
        } catch (e: Exception) {
            AdchainLogger.e(TAG, "Error getting country code", e)
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
            AdchainLogger.e(TAG, "Error getting language code", e)
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
                    AdchainLogger.v(TAG, "No installer found, likely installed via ADB or direct APK")
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
            AdchainLogger.e(TAG, "Error getting installer package: ${e.message}", e)
            "unknown"
        }
    }
    
    /**
     * Clear cached values (useful for testing)
     */
    fun clearCache() {
        cachedDeviceId = null
        cachedAdvertisingId = null
        isAdvertisingIdInitialized = false
    }
}