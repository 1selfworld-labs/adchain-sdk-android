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
    private const val KEY_ADVERTISING_ID = "advertising_id"
    private const val KEY_ADVERTISING_ID_TIMESTAMP = "advertising_id_timestamp"
    private const val ADVERTISING_ID_CACHE_DURATION = 3600000L // 1 hour in milliseconds
    private const val ZERO_ID = "00000000-0000-0000-0000-000000000000"
    
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
     * Get Google Advertising ID (GAID)
     * This method should be called from a coroutine
     * Returns ZERO_ID if advertising ID is not available or user has opted out
     */
    suspend fun getAdvertisingId(context: Context): String? = withContext(Dispatchers.IO) {
        AdchainLogger.v(TAG, "[GAID] Starting getAdvertisingId()")
        try {
            val currentTime = System.currentTimeMillis()

            // 1. Check cache first (skip if cached value is ZERO_ID)
            if (cachedAdvertisingId != null &&
                cachedAdvertisingId != ZERO_ID &&
                (currentTime - advertisingIdTimestamp) < ADVERTISING_ID_CACHE_DURATION) {
                AdchainLogger.v(TAG, "[GAID] Returning cached value: ${cachedAdvertisingId?.take(8)}...")
                return@withContext cachedAdvertisingId
            }
            AdchainLogger.v(TAG, "[GAID] No valid cached value, proceeding to fetch")

            // 2. Try to get from SharedPreferences
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val storedAdId = prefs.getString(KEY_ADVERTISING_ID, null)
            val storedTimestamp = prefs.getLong(KEY_ADVERTISING_ID_TIMESTAMP, 0)
            AdchainLogger.v(TAG, "[GAID] SharedPrefs check - storedAdId: ${storedAdId?.take(8) ?: "null"}, age: ${currentTime - storedTimestamp}ms")

            if (storedAdId != null &&
                storedAdId != ZERO_ID &&
                (currentTime - storedTimestamp) < ADVERTISING_ID_CACHE_DURATION) {
                cachedAdvertisingId = storedAdId
                advertisingIdTimestamp = storedTimestamp
                AdchainLogger.v(TAG, "[GAID] Returning SharedPrefs value: ${storedAdId.take(8)}...")
                return@withContext storedAdId
            }

            // 3. Check Google Play Services availability
            AdchainLogger.v(TAG, "[GAID] Checking Google Play Services availability...")
            val availability = GoogleApiAvailability.getInstance()
            val playServicesStatus = availability.isGooglePlayServicesAvailable(context)
            AdchainLogger.v(TAG, "[GAID] Google Play Services status: $playServicesStatus (SUCCESS=${ConnectionResult.SUCCESS})")

            if (playServicesStatus != ConnectionResult.SUCCESS) {
                val errorString = availability.getErrorString(playServicesStatus)
                AdchainLogger.e(TAG, "[GAID] Google Play Services not available: $errorString (code=$playServicesStatus)")
                return@withContext ZERO_ID
            }
            AdchainLogger.v(TAG, "[GAID] Google Play Services is available")

            // 4. Check AD_ID permission for Android 13+
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                AdchainLogger.v(TAG, "[GAID] Android 13+ detected, checking AD_ID permission...")
                val hasPermission = context.checkSelfPermission("com.google.android.gms.permission.AD_ID") ==
                    PackageManager.PERMISSION_GRANTED
                AdchainLogger.v(TAG, "[GAID] AD_ID permission status: ${if (hasPermission) "GRANTED" else "DENIED"}")
                if (!hasPermission) {
                    AdchainLogger.e(TAG, "[GAID] AD_ID permission not granted - returning ZERO_ID")
                    return@withContext ZERO_ID
                }
            } else {
                AdchainLogger.v(TAG, "[GAID] Android ${Build.VERSION.SDK_INT} - AD_ID permission check not required")
            }

            // 5. Get advertising ID directly with timeout
            AdchainLogger.v(TAG, "[GAID] Calling AdvertisingIdClient.getAdvertisingIdInfo() with 2s timeout...")
            val startTime = System.currentTimeMillis()
            val adIdInfo = withTimeout(2000L) {
                AdvertisingIdClient.getAdvertisingIdInfo(context.applicationContext)
            }
            val elapsed = System.currentTimeMillis() - startTime
            AdchainLogger.v(TAG, "[GAID] AdvertisingIdClient returned in ${elapsed}ms")

            // 6. Check if user has opted out of ad tracking
            AdchainLogger.v(TAG, "[GAID] Checking isLimitAdTrackingEnabled...")
            if (adIdInfo.isLimitAdTrackingEnabled) {
                AdchainLogger.w(TAG, "[GAID] User opted out of ad tracking (LAT=true) - returning ZERO_ID")
                return@withContext ZERO_ID  // This is expected behavior
            }
            AdchainLogger.v(TAG, "[GAID] User has NOT opted out (LAT=false)")

            val advertisingId = adIdInfo.id
            AdchainLogger.v(TAG, "[GAID] Raw advertising ID from API: ${if (advertisingId.isNullOrEmpty()) "NULL/EMPTY" else "'$advertisingId'"}")

            // 7. Cache valid advertising ID only
            if (!advertisingId.isNullOrEmpty() && advertisingId != ZERO_ID) {
                cachedAdvertisingId = advertisingId
                advertisingIdTimestamp = currentTime

                // Store in SharedPreferences
                prefs.edit()
                    .putString(KEY_ADVERTISING_ID, advertisingId)
                    .putLong(KEY_ADVERTISING_ID_TIMESTAMP, currentTime)
                    .apply()

                AdchainLogger.i(TAG, "[GAID] SUCCESS - Retrieved valid advertising ID: ${advertisingId.take(8)}...")
                return@withContext advertisingId
            }

            // Invalid or empty ID received
            AdchainLogger.e(TAG, "[GAID] Invalid advertising ID received: isNull=${advertisingId == null}, isEmpty=${advertisingId?.isEmpty()}, isZero=${advertisingId == ZERO_ID}")
            ZERO_ID

        } catch (e: TimeoutCancellationException) {
            AdchainLogger.e(TAG, "[GAID] TIMEOUT - Failed to retrieve GAID within 2 seconds", e)
            ZERO_ID
        } catch (e: SecurityException) {
            AdchainLogger.e(TAG, "[GAID] SECURITY ERROR - Missing permission or security restriction", e)
            ZERO_ID
        } catch (e: NoClassDefFoundError) {
            AdchainLogger.e(TAG, "[GAID] CLASS NOT FOUND - Google Play Services classes missing", e)
            ZERO_ID
        } catch (e: Exception) {
            AdchainLogger.e(TAG, "[GAID] UNEXPECTED ERROR - ${e.javaClass.simpleName}: ${e.message}", e)
            ZERO_ID
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

        val currentTime = System.currentTimeMillis()

        // Return cached value if available (skip ZERO_ID)
        if (cachedAdvertisingId != null &&
            cachedAdvertisingId != ZERO_ID &&
            (currentTime - advertisingIdTimestamp) < ADVERTISING_ID_CACHE_DURATION) {
            AdchainLogger.v(TAG, "[GAID-SYNC] Returning cached value: ${cachedAdvertisingId?.take(8)}...")
            return cachedAdvertisingId
        }
        AdchainLogger.v(TAG, "[GAID-SYNC] No valid cache, checking SharedPrefs...")

        // Try to get from SharedPreferences
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val storedAdId = prefs.getString(KEY_ADVERTISING_ID, null)
        val storedTimestamp = prefs.getLong(KEY_ADVERTISING_ID_TIMESTAMP, 0)

        if (storedAdId != null &&
            storedAdId != ZERO_ID &&
            (currentTime - storedTimestamp) < ADVERTISING_ID_CACHE_DURATION) {
            cachedAdvertisingId = storedAdId
            advertisingIdTimestamp = storedTimestamp
            AdchainLogger.v(TAG, "[GAID-SYNC] Returning SharedPrefs value: ${storedAdId.take(8)}...")
            return storedAdId
        }

        // If no cached value available, return zero ID
        AdchainLogger.w(TAG, "[GAID-SYNC] No cached value available, returning ZERO_ID")
        return ZERO_ID
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
        advertisingIdTimestamp = 0
    }
}