package com.adchain.sdk.core

import android.app.Application
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.adchain.sdk.network.ApiConfig
import com.adchain.sdk.network.NetworkManager
import com.adchain.sdk.network.models.response.AppData
import com.adchain.sdk.offerwall.AdchainOfferwallActivity
import com.adchain.sdk.offerwall.OfferwallCallback
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
            Log.w(TAG, "AdchainSdk is already initialized. Skipping re-initialization.")
            return
        }

        require(sdkConfig.appKey.isNotEmpty()) { "App Key cannot be empty" }
        require(sdkConfig.appSecret.isNotEmpty()) { "App Secret cannot be empty" }

        this.application = application
        this.config = sdkConfig

        // Initialize network manager
        NetworkManager.initialize()

        // Validate app credentials with server asynchronously
        coroutineScope.launch {
            val result = NetworkManager.validateApp()
            if (result.isSuccess) {
                // Store validated app data
                validatedAppData = result.getOrNull()?.app

                // Mark as initialized immediately to allow SDK usage
                isInitialized.set(true)

                Log.d(TAG, "SDK validated successfully with server")
                Log.d(TAG, "Offerwall URL: ${validatedAppData?.adchainHubUrl}")
            } else {
                Log.e(TAG, "SDK validation failed", result.exceptionOrNull())
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
                        Log.d(TAG, "Login successful: ${loginResponse.getOrNull()?.success}")
                    } else {
                        Log.e(TAG, "Login failed but continuing: ${loginResponse.exceptionOrNull()}")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Login failed but continuing", e)
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
                    android.util.Log.e("AdchainSdk", "Failed to track logout event", e)
                }
            }
        }
        currentUser = null
        clearSavedUserId()
    }
    
    @JvmStatic
    val isLoggedIn: Boolean
        get() = currentUser != null
    
    @JvmStatic
    fun getCurrentUser(): AdchainSdkUser? = currentUser
    
    @JvmStatic
    fun getConfig(): AdchainSdkConfig? = config
    
    @JvmStatic
    internal fun getApplication(): Application? = application
    
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
            Log.e(TAG, "SDK not initialized")
            callback?.onError("SDK not initialized. Please initialize the SDK first.")
            return
        }
        
        // Check if user is logged in
        if (currentUser == null) {
            Log.e(TAG, "User not logged in")
            callback?.onError("User not logged in. Please login first.")
            return
        }
        
        // Check if offerwall URL is available
        val offerwallUrl = validatedAppData?.adchainHubUrl
        if (offerwallUrl.isNullOrEmpty()) {
            Log.e(TAG, "Offerwall URL not available")
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
    
    @JvmStatic
    internal fun resetForTesting() {
        isInitialized.set(false)
        application = null
        config = null
        currentUser = null
        validatedAppData = null
    }
}