package com.adchain.sdk.core

import android.app.Application
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import com.adchain.sdk.network.ApiConfig
import com.adchain.sdk.network.NetworkManager
import com.adchain.sdk.network.models.response.AppData
import com.adchain.sdk.network.models.response.BannerInfoResponse
import com.adchain.sdk.offerwall.AdchainOfferwallActivity
import com.adchain.sdk.offerwall.OfferwallCallback
import com.adchain.sdk.utils.AdchainLogger
import com.adchain.sdk.utils.DeviceUtils
import com.adchain.sdk.utils.LogLevel
import com.adchain.sdk.BuildConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean

object AdchainSdk {
    private const val TAG = "AdchainSdk"
    private val isInitialized = AtomicBoolean(false)
    private var application: Application? = null
    private var config: AdchainSdkConfig? = null
    private var currentUser: AdchainSdkUser? = null
    private var validatedAppData: AppData? = null
    private val handler = Handler(Looper.getMainLooper())
    private val coroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private const val PREFS_NAME = "adchain_prefs"
    private const val KEY_CURRENT_USER_ID = "currentUserId"

    private fun saveCurrentUserId(userId: String) {
        val app = application ?: return
        val prefs = app.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_CURRENT_USER_ID, userId).apply()
    }

    private fun getSavedUserId(): String {
        val app = application ?: return ""
        val prefs = app.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_CURRENT_USER_ID, "") ?: ""
    }

    private fun clearSavedUserId() {
        val app = application ?: return
        val prefs = app.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().remove(KEY_CURRENT_USER_ID).apply()
    }
    
    @JvmStatic
    fun initialize(
        application: Application,
        sdkConfig: AdchainSdkConfig
    ) {
        // Check if already initialized and return early with log message
        if (isInitialized.get()) {
            AdchainLogger.w(TAG, "AdchainSdk is already initialized. Skipping re-initialization.")
            return
        }

        require(sdkConfig.appKey.isNotEmpty()) { "App Key cannot be empty" }
        require(sdkConfig.appSecret.isNotEmpty()) { "App Secret cannot be empty" }

        this.application = application
        this.config = sdkConfig

        // Initialize network manager
        NetworkManager.initialize()

        // Pre-fetch advertising ID in background to populate cache early
        coroutineScope.launch(Dispatchers.IO) {
            try {
                AdchainLogger.d(TAG, "Starting GAID pre-fetch during SDK initialization")
                val gaid = DeviceUtils.getAdvertisingId(application)
                AdchainLogger.d(TAG, "GAID pre-fetch completed during init: ${gaid?.take(8) ?: "null or empty"}")
            } catch (e: Exception) {
                AdchainLogger.w(TAG, "GAID pre-fetch failed during init, will retry on login: ${e.message}")
            }
        }

        // Validate app credentials with server asynchronously
        coroutineScope.launch {
            val result = NetworkManager.validateApp()
            if (result.isSuccess) {
                // Store validated app data
                validatedAppData = result.getOrNull()?.app

                // Mark as initialized immediately to allow SDK usage
                isInitialized.set(true)

                AdchainLogger.i(TAG, "SDK validated successfully with server")
                AdchainLogger.d(TAG, "Offerwall URL: ${validatedAppData?.adchainHubUrl}")
            } else {
                AdchainLogger.e(TAG, "SDK validation failed", result.exceptionOrNull())
            }
        }
    }
    
    @JvmStatic
    fun login(
        adchainSdkUser: AdchainSdkUser,
        listener: AdchainSdkLoginListener? = null
    ) {
        if (!isInitialized.get()) {
            handler.post {
                listener?.onFailure(AdchainSdkLoginListener.ErrorType.NOT_INITIALIZED)
            }
            return
        }
        
        if (adchainSdkUser.userId.isEmpty()) {
            handler.post {
                listener?.onFailure(AdchainSdkLoginListener.ErrorType.INVALID_USER_ID)
            }
            return
        }
        
        // Check for duplicate login and handle it
        if (currentUser?.userId != adchainSdkUser.userId) {
            // Different user trying to login, logout the previous user first
            logout()
        }
        
        coroutineScope.launch {
            try {
                // Set current user first
                // 아래 통신이 실패해도, 유저 바인딩은 진행
                currentUser = adchainSdkUser

                // 로컬 스토리지에 유저 아이디 저장 (Android SharedPreferences)
                saveCurrentUserId(adchainSdkUser.userId)
                
                // Login to server (gender와 birthYear 포함)
                try {
                    val loginResponse = NetworkManager.login(
                        userId = adchainSdkUser.userId,
                        eventName = "user_login",
                        sdkVersion = BuildConfig.VERSION_NAME,
                        gender = adchainSdkUser.gender?.value,  // Gender enum의 value (M/F)
                        birthYear = adchainSdkUser.birthYear,  // birthYear 전달
                        category = "authentication",
                        properties = mapOf("user_id" to adchainSdkUser.userId)
                    )
                    
                    if (loginResponse.isSuccess) {
                        AdchainLogger.i(TAG, "Login successful: ${loginResponse.getOrNull()?.success}")
                    } else {
                        AdchainLogger.e(TAG, "Login failed but continuing: ${loginResponse.exceptionOrNull()}")
                    }
                } catch (e: Exception) {
                    AdchainLogger.e(TAG, "Login failed but continuing", e)
                }

                // Track session start event for DAU
                NetworkManager.trackEvent(
                    userId = adchainSdkUser.userId,
                    eventName = "session_start",
                    sdkVersion = BuildConfig.VERSION_NAME,
                    category = "session",
                    properties = mapOf(
                        "user_id" to adchainSdkUser.userId,
                        "session_id" to java.util.UUID.randomUUID().toString()
                    )
                )

                // Login successful
                handler.post { listener?.onSuccess() }
            } catch (e: Exception) {
                handler.post { 
                    listener?.onFailure(AdchainSdkLoginListener.ErrorType.AUTHENTICATION_FAILED)
                }
            }
        }
    }
    
    @JvmStatic
    fun logout() {
        val userToLogout = currentUser
        if (userToLogout != null) {
            // Track logout event before clearing user
            coroutineScope.launch {
                try {
                    NetworkManager.trackEvent(
                        userId = userToLogout.userId,
                        eventName = "user_logout",
                        sdkVersion = BuildConfig.VERSION_NAME,
                        category = "authentication",
                        properties = mapOf("userId" to userToLogout.userId)
                    )
                } catch (e: Exception) {
                    AdchainLogger.e("AdchainSdk", "Failed to track logout event", e)
                }
            }
        }
        currentUser = null
        clearSavedUserId()
    }
    
    @JvmStatic
    fun isInitialized(): Boolean = isInitialized.get()

    @JvmStatic
    val isLoggedIn: Boolean
        get() = currentUser != null

    @JvmStatic
    fun getCurrentUser(): AdchainSdkUser? = currentUser

    @JvmStatic
    fun getConfig(): AdchainSdkConfig? = config

    @JvmStatic
    internal fun getApplication(): Application? = application

    /**
     * Set the log level for SDK logs
     * @param level The desired log level (NONE, ERROR, WARNING, INFO, DEBUG, VERBOSE)
     * Default is WARNING for production safety
     */
    @JvmStatic
    fun setLogLevel(level: LogLevel) {
        AdchainLogger.logLevel = level
    }
    
    internal fun requireInitialized() {
        require(isInitialized.get()) { "AdchainSdk must be initialized before use" }
    }
    
    /**
     * Opens the offerwall in a new activity
     * @param context The context to start the activity from
     * @param callback Optional callback for offerwall events
     */
    @JvmStatic
    fun openOfferwall(context: Context, callback: OfferwallCallback? = null) {
        // Check if SDK is initialized
        if (!isInitialized.get()) {
            AdchainLogger.e(TAG, "SDK not initialized")
            callback?.onError("SDK not initialized. Please initialize the SDK first.")
            return
        }
        
        // Check if user is logged in
        if (currentUser == null) {
            AdchainLogger.e(TAG, "User not logged in")
            callback?.onError("User not logged in. Please login first.")
            return
        }
        
        // Check if offerwall URL is available
        val offerwallUrl = validatedAppData?.adchainHubUrl
        if (offerwallUrl.isNullOrEmpty()) {
            AdchainLogger.e(TAG, "Offerwall URL not available")
            callback?.onError("Offerwall URL not available. Please check your app configuration.")
            return
        }
        
        // Store callback in companion object to be accessed by activity
        AdchainOfferwallActivity.setCallback(callback)
        
        // Start offerwall activity
        val intent = Intent(context, AdchainOfferwallActivity::class.java).apply {
            putExtra(AdchainOfferwallActivity.EXTRA_BASE_URL, offerwallUrl)
            putExtra(AdchainOfferwallActivity.EXTRA_USER_ID, currentUser?.userId)
            putExtra(AdchainOfferwallActivity.EXTRA_APP_ID, config?.appKey)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        
        context.startActivity(intent)
        
        // Notify callback that offerwall is opened
        callback?.onOpened()
        
        // Track event
        coroutineScope.launch {
            NetworkManager.trackEvent(
                userId = currentUser?.userId ?: "",
                eventName = "offerwall_opened",
                sdkVersion = BuildConfig.VERSION_NAME,
                category = "offerwall",
                properties = mapOf(
                    "source" to "sdk_api"
                )
            )
        }
    }

    /**
     * Opens the offerwall with a custom URL
     * @param url The custom URL to open in the offerwall WebView
     * @param context The context to start the activity from
     * @param callback Optional callback for offerwall events
     */
    @JvmStatic
    @JvmOverloads
    fun openOfferwallWithUrl(
        url: String,
        context: Context,
        callback: OfferwallCallback? = null
    ) {
        // Check if SDK is initialized
        if (!isInitialized.get()) {
            AdchainLogger.e(TAG, "SDK not initialized")
            callback?.onError("SDK not initialized. Please initialize the SDK first.")
            return
        }

        // Check if user is logged in
        if (currentUser == null) {
            AdchainLogger.e(TAG, "User not logged in")
            callback?.onError("User not logged in. Please login first.")
            return
        }

        // Validate URL
        if (url.isEmpty()) {
            AdchainLogger.e(TAG, "URL is empty")
            callback?.onError("URL cannot be empty")
            return
        }

        // Store callback in companion object to be accessed by activity
        AdchainOfferwallActivity.setCallback(callback)

        // Start offerwall activity with custom URL
        val intent = Intent(context, AdchainOfferwallActivity::class.java).apply {
            putExtra(AdchainOfferwallActivity.EXTRA_BASE_URL, url)
            putExtra(AdchainOfferwallActivity.EXTRA_USER_ID, currentUser?.userId)
            putExtra(AdchainOfferwallActivity.EXTRA_APP_ID, config?.appKey)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        context.startActivity(intent)

        // Notify callback that offerwall is opened
        callback?.onOpened()

        // Track event
        coroutineScope.launch {
            NetworkManager.trackEvent(
                userId = currentUser?.userId ?: "",
                eventName = "custom_offerwall_opened",
                sdkVersion = BuildConfig.VERSION_NAME,
                category = "offerwall",
                properties = mapOf(
                    "source" to "sdk_api",
                    "url" to url
                )
            )
        }
    }

    /**
     * Opens a URL in the system's default external browser
     * @param url The URL to open in the external browser
     * @param context The context to start the browser from
     * @return true if browser was opened successfully, false otherwise
     */
    @JvmStatic
    fun openExternalBrowser(url: String, context: Context): Boolean {
        // Validate URL
        if (url.isEmpty()) {
            AdchainLogger.e(TAG, "URL is empty")
            return false
        }

        return try {
            val browserIntent = Intent(Intent.ACTION_VIEW, android.net.Uri.parse(url)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(browserIntent)

            // Track event if user is logged in
            currentUser?.let { user ->
                coroutineScope.launch {
                    NetworkManager.trackEvent(
                        userId = user.userId,
                        eventName = "external_browser_opened",
                        sdkVersion = BuildConfig.VERSION_NAME,
                        category = "browser",
                        properties = mapOf(
                            "source" to "sdk_api",
                            "url" to url
                        )
                    )
                }
            }

            true
        } catch (e: android.content.ActivityNotFoundException) {
            AdchainLogger.e(TAG, "No browser app found to open URL: $url", e)
            false
        } catch (e: Exception) {
            AdchainLogger.e(TAG, "Failed to open external browser", e)
            false
        }
    }

    /**
     * Get banner information for a specific placement
     * @param placementId The placement identifier for the banner
     * @param callback Callback to receive the banner information
     */
    @JvmStatic
    fun getBannerInfo(
        placementId: String,
        callback: (Result<BannerInfoResponse>) -> Unit
    ) {
        // Check if SDK is initialized
        if (!isInitialized.get()) {
            AdchainLogger.e(TAG, "SDK not initialized")
            callback(Result.failure(Exception("SDK not initialized. Please initialize the SDK first.")))
            return
        }

        // Check if user is logged in
        val user = currentUser
        if (user == null) {
            AdchainLogger.e(TAG, "User not logged in")
            callback(Result.failure(Exception("User not logged in. Please login first.")))
            return
        }

        // Check if placementId is valid
        if (placementId.isEmpty()) {
            AdchainLogger.e(TAG, "PlacementId is empty")
            callback(Result.failure(Exception("PlacementId cannot be empty")))
            return
        }

        // Make network request
        coroutineScope.launch {
            try {
                val result = NetworkManager.getBannerInfo(
                    userId = user.userId,
                    placementId = placementId
                )
                handler.post {
                    callback(result)
                }
            } catch (e: Exception) {
                AdchainLogger.e(TAG, "Failed to get banner info", e)
                handler.post {
                    callback(Result.failure(e))
                }
            }
        }
    }

    @JvmStatic
    internal fun resetForTesting() {
        isInitialized.set(false)
        application = null
        config = null
        currentUser = null
        validatedAppData = null
    }
}