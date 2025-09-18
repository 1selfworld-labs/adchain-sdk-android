package com.adchain.sdk.offerwall

// TEST MODIFICATION: React Native Local SDK Build Test - 2025-01-16 18:30
import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import com.adchain.sdk.utils.AdchainLogger
import android.view.ViewGroup
import android.webkit.*
import android.webkit.WebViewClient.*
import android.widget.FrameLayout
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import com.adchain.sdk.BuildConfig
import com.adchain.sdk.core.AdchainSdk
import com.adchain.sdk.mission.AdchainMission
import com.adchain.sdk.network.NetworkManager
import com.adchain.sdk.quiz.AdchainQuiz
import com.adchain.sdk.utils.DeviceUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.lang.ref.WeakReference
import java.util.Stack

/**
 * Internal activity for displaying the offerwall
 * This activity is started by AdchainSdk.openOfferwall() method
 */
internal class AdchainOfferwallActivity : AppCompatActivity() {
    
    companion object {
        private const val TAG = "AdchainOfferwall"
        const val EXTRA_BASE_URL = "base_url"
        const val EXTRA_USER_ID = "user_id"
        const val EXTRA_APP_ID = "app_id"  // 하위 호환성을 위해 이름은 유지
        const val EXTRA_IS_SUB_WEBVIEW = "is_sub_webview"
        const val EXTRA_CONTEXT_TYPE = "context_type"
        const val EXTRA_QUIZ_ID = "quiz_id"
        const val EXTRA_QUIZ_TITLE = "quiz_title"
        
        private var callback: OfferwallCallback? = null
        private val webViewStack = Stack<WeakReference<AdchainOfferwallActivity>>()
        
        fun setCallback(cb: OfferwallCallback?) {
            callback = cb
        }
        
        internal fun openSubWebView(context: Context, url: String) {
            val intent = Intent(context, AdchainOfferwallActivity::class.java).apply {
                putExtra(EXTRA_BASE_URL, url)
                putExtra(EXTRA_IS_SUB_WEBVIEW, true)
                putExtra(EXTRA_USER_ID, AdchainSdk.getCurrentUser()?.userId)
                putExtra(EXTRA_APP_ID, AdchainSdk.getConfig()?.appKey)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        }
        
        internal fun closeAllWebViews() {
            // Close all stacked WebViews
            while (webViewStack.isNotEmpty()) {
                val activityRef = webViewStack.pop()
                activityRef.get()?.finish()
            }
        }
    }
    
    private lateinit var webView: WebView
    private val coroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var isSubWebView = false
    private var contextType: String = "offerwall"
    
    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Enable Edge-to-Edge for Android 30+ to properly handle window insets
        // This is required for proper inset handling
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            AdchainLogger.d(TAG, "Setting Edge-to-Edge: setDecorFitsSystemWindows(false)")
            WindowCompat.setDecorFitsSystemWindows(window, false)
        } else {
            AdchainLogger.d(TAG, "SDK < 30, not setting Edge-to-Edge")
        }

        // Check if this is a sub WebView
        isSubWebView = intent.getBooleanExtra(EXTRA_IS_SUB_WEBVIEW, false)

        // Get context type (offerwall or quiz)
        contextType = intent.getStringExtra(EXTRA_CONTEXT_TYPE) ?: "offerwall"

        // Setup action bar title based on context
        if (contextType == "quiz") {
            supportActionBar?.apply {
                title = intent.getStringExtra(EXTRA_QUIZ_TITLE) ?: "Quiz"
                setDisplayHomeAsUpEnabled(true)
            }
        }

        // Add to stack if this is a sub WebView
        if (isSubWebView) {
            webViewStack.push(WeakReference(this))
        }

        // Create container layout that will handle insets
        val container = FrameLayout(this).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }

        // Create WebView
        webView = WebView(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        }

        container.addView(webView)
        setContentView(container)

        // Apply padding to container instead of margin to WebView
        // This ensures the WebView content is properly positioned
        ViewCompat.setOnApplyWindowInsetsListener(container) { v, windowInsets ->
            val insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars())

            AdchainLogger.v(TAG, "Applying container padding - bottom: ${insets.bottom}, top: ${insets.top}")

            // Apply padding to the container using system-provided values
            v.setPadding(0, insets.top, 0, insets.bottom)

            // Don't consume the insets, let them pass through
            windowInsets
        }

        // Also set a fallback for WebView itself
        webView.post {
            // Get the root window insets directly
            ViewCompat.getRootWindowInsets(container)?.let { rootInsets ->
                val insets = rootInsets.getInsets(WindowInsetsCompat.Type.systemBars())

                if (container.paddingBottom == 0 && insets.bottom > 0) {
                    AdchainLogger.v(TAG, "Fallback: Applying container padding - bottom: ${insets.bottom}, top: ${insets.top}")
                    container.setPadding(0, insets.top, 0, insets.bottom)
                }
            }
        }

        // Additional debugging
        AdchainLogger.v(TAG, "Window flags: ${window.attributes.flags}")
        AdchainLogger.v(TAG, "Window softInputMode: ${window.attributes.softInputMode}")
        
        // Get base URL from intent
        val baseUrl = intent.getStringExtra(EXTRA_BASE_URL)
        if (baseUrl.isNullOrEmpty()) {
            AdchainLogger.e(TAG, "No base URL provided")
            if (!isSubWebView) {
                callback?.onError("Failed to load offerwall: No URL provided")
            }
            finish()
            return
        }
        
        // Setup WebView
        setupWebView()
        
        // Setup back press handler
        setupBackPressHandler()
        
        // Build and load URL
        val finalUrl = if (isSubWebView) {
            // Sub WebView - use URL as is
            baseUrl
        } else {
            // Main WebView - build with parameters
            buildOfferwallUrl(baseUrl)
        }
        AdchainLogger.d(TAG, "Loading ${if (isSubWebView) "sub " else ""}offerwall URL: $finalUrl")
        webView.loadUrl(finalUrl)
    }
    
    @SuppressLint("SetJavaScriptEnabled")
    private fun setupWebView() {
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            loadWithOverviewMode = true
            useWideViewPort = true
            builtInZoomControls = false
            displayZoomControls = false
            
            // Allow mixed content including HTTP resources on HTTPS pages
            // MIXED_CONTENT_ALWAYS_ALLOW allows all mixed content (including HTTP on HTTPS)
            mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
            
            // Cache settings
            cacheMode = WebSettings.LOAD_DEFAULT
            
            // User agent
            userAgentString = "$userAgentString AdchainSDK/${BuildConfig.VERSION_NAME}"
            
            // Allow file access for local resources if needed
            allowFileAccess = true
            allowContentAccess = true
        }
        
        // Set WebView client first (before loading URL)
        setupWebViewClient()
        
        // WebChrome client for JavaScript dialogs
        webView.webChromeClient = object : WebChromeClient() {
            override fun onJsAlert(
                view: WebView?,
                url: String?,
                message: String?,
                result: JsResult?
            ): Boolean {
                AdchainLogger.d(TAG, "JS Alert: $message")
                return super.onJsAlert(view, url, message, result)
            }
        }
        
        // Add JavaScript interface for communication
        // Register with a different name to avoid conflicts
        webView.addJavascriptInterface(NativeBridge(), "__adchainNative__")
    }
    
    private fun buildOfferwallUrl(baseUrl: String): String {
        val urlBuilder = Uri.parse(baseUrl).buildUpon()
        
        // Add required parameters
        urlBuilder.apply {
            // User and app info  
            appendQueryParameter("user_id", intent.getStringExtra(EXTRA_USER_ID) ?: "")
            appendQueryParameter("app_key", intent.getStringExtra(EXTRA_APP_ID) ?: "")  // app_key로 변경
            
            // Add quiz-specific parameters if context is quiz
            if (contextType == "quiz") {
                intent.getStringExtra(EXTRA_QUIZ_ID)?.let {
                    appendQueryParameter("quiz_id", it)
                }
                intent.getStringExtra(EXTRA_QUIZ_TITLE)?.let {
                    appendQueryParameter("quiz_title", it)
                }
                appendQueryParameter("context", "quiz")
            }
            
            // Device info
            appendQueryParameter("device_id", DeviceUtils.getDeviceId(this@AdchainOfferwallActivity))
            appendQueryParameter("platform", "Android")
            appendQueryParameter("os_version", DeviceUtils.getOsVersion())
            appendQueryParameter("device_model", DeviceUtils.getDeviceModel())
            appendQueryParameter("device_manufacturer", DeviceUtils.getDeviceManufacturer())
            
            // SDK info
            appendQueryParameter("sdk_version", BuildConfig.VERSION_NAME)
            
            // Session info
            appendQueryParameter("session_id", NetworkManager.getSessionId())
            appendQueryParameter("timestamp", System.currentTimeMillis().toString())
            
            // Add IFA (advertising ID) if available (synchronously from cache)
            val advertisingId = DeviceUtils.getAdvertisingIdSync(this@AdchainOfferwallActivity)
            if (!advertisingId.isNullOrEmpty()) {
                appendQueryParameter("ifa", advertisingId)
            }
        }
        
        // Also inject advertising ID via JavaScript for backward compatibility
        coroutineScope.launch {
            try {
                // Try to fetch fresh advertising ID asynchronously
                val freshAdvertisingId = DeviceUtils.getAdvertisingId(this@AdchainOfferwallActivity)
                if (!freshAdvertisingId.isNullOrEmpty()) {
                    webView.evaluateJavascript(
                        "if(window.AdchainConfig) { window.AdchainConfig.ifa = '$freshAdvertisingId'; }",
                        null
                    )
                }
            } catch (e: Exception) {
                AdchainLogger.d(TAG, "Failed to inject advertising ID via JavaScript: ${e.message}")
            }
        }
        
        return urlBuilder.build().toString()
    }
    
    private fun closeOfferwall() {
        AdchainLogger.i(TAG, "Closing offerwall")
        
        // If this is a sub WebView, just close this one
        if (isSubWebView) {
            finish()
            return
        }
        
        // Close all WebViews and notify callback
        closeAllWebViews()
        callback?.onClosed()
        
        // Track close event
        coroutineScope.launch {
            val userId = intent.getStringExtra(EXTRA_USER_ID) ?: ""
            NetworkManager.trackEvent(
                userId = userId,
                eventName = "offerwall_closed",
                sdkVersion = BuildConfig.VERSION_NAME,
                category = "offerwall"
            )
        }
        
        finish()
    }
    
    // Handle back press using OnBackPressedCallback for Android 13+ compatibility
    private fun setupBackPressHandler() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (webView.canGoBack()) {
                    webView.goBack()
                } else {
                    // If this is a sub WebView, just close it
                    if (isSubWebView) {
                        finish()
                    } else {
                        // Main WebView - close everything
                        closeOfferwall()
                    }
                    // Remove this callback to allow default behavior
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                }
            }
        })
    }
    
    override fun onDestroy() {
        super.onDestroy()
        
        // Remove WebView from parent before destroying to avoid "WebView.destroy() called while still attached" warning
        (webView.parent as? ViewGroup)?.removeView(webView)
        webView.destroy()
        
        // Remove from stack if it's a sub WebView
        if (isSubWebView) {
            webViewStack.removeIf { it.get() == this }
        }
        
        // Clear callback to avoid memory leak (only for main WebView)
        if (!isSubWebView) {
            callback = null
        }
    }
    
    /**
     * Simple native bridge interface
     * Actual implementation that receives messages from JavaScript wrapper
     */
    inner class NativeBridge {
        @JavascriptInterface
        fun postMessage(jsonMessage: String) {
            AdchainLogger.d(TAG, "Received webkit message: $jsonMessage")
            handlePostMessage(jsonMessage)
        }
    }
    
    /**
     * Setup WebView client with proper handlers
     */
    private fun setupWebViewClient() {
        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                AdchainLogger.d(TAG, "Page loaded: $url, injecting webkit wrapper")
                injectWebkitWrapper()
            }
            
            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                val url = request?.url?.toString() ?: return false
                
                // Check for special URLs (e.g., close command)
                if (url.contains("adchain://close")) {
                    runOnUiThread {
                        closeOfferwall()
                    }
                    return true
                }
                
                // Load URL in WebView
                return false
            }
            
            override fun onReceivedError(
                view: WebView?,
                request: WebResourceRequest?,
                error: WebResourceError?
            ) {
                super.onReceivedError(view, request, error)
                // error.description requires API 23+, so we need to check the API level
                val errorDescription = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                    error?.description?.toString() ?: "Unknown error"
                } else {
                    "WebView error occurred"
                }
                val errorCode = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                    error?.errorCode ?: 0
                } else {
                    0
                }
                AdchainLogger.e(TAG, "WebView error: $errorDescription (code: $errorCode)")

                // Check if it's a network error and show offline page
                // ERROR_INTERNET_DISCONNECTED is only available on API 33+, use value directly
                if (errorCode == ERROR_HOST_LOOKUP ||
                    errorCode == ERROR_CONNECT ||
                    errorCode == ERROR_TIMEOUT ||
                    errorCode == -2 || // ERROR_INTERNET_DISCONNECTED value
                    errorDescription.contains("ERR_INTERNET_DISCONNECTED", ignoreCase = true) ||
                    errorDescription.contains("ERR_NAME_NOT_RESOLVED", ignoreCase = true) ||
                    errorDescription.contains("ERR_CONNECTION", ignoreCase = true)) {

                    AdchainLogger.d(TAG, "Network error detected, showing offline page")
                    showOfflinePage()
                } else {
                    runOnUiThread {
                        callback?.onError("Failed to load offerwall: $errorDescription")
                    }
                }
            }
        }
    }
    
    /**
     * Inject JavaScript wrapper to create iOS-compatible webkit.messageHandlers structure
     */
    private fun injectWebkitWrapper() {
        val jsWrapper = """
            (function() {
                // Create webkit structure if it doesn't exist
                if (typeof window.webkit === 'undefined') {
                    window.webkit = {};
                }
                
                // Create messageHandlers with postMessage function
                window.webkit.messageHandlers = {
                    postMessage: function(message) {
                        // Forward to actual native bridge
                        if (window.__adchainNative__ && window.__adchainNative__.postMessage) {
                            // Convert object to JSON string if needed
                            var jsonString = typeof message === 'string' ? message : JSON.stringify(message);
                            window.__adchainNative__.postMessage(jsonString);
                        } else {
                            console.error('Native bridge not available');
                        }
                    }
                };
                
                console.log('Webkit messageHandlers wrapper injected successfully');
            })();
        """.trimIndent()
        
        webView.evaluateJavascript(jsWrapper, null)
    }
    
    
    /**
     * Common handler for postMessage calls from both interfaces
     */
    private fun handlePostMessage(jsonMessage: String) {
        try {
            val message = JSONObject(jsonMessage)
            val type = message.getString("type")
            val data = message.optJSONObject("data")
            
            AdchainLogger.d(TAG, "Processing message type: $type")
            
            when (type) {
                "openWebView" -> handleOpenWebView(data)
                "close" -> handleClose()
                "closeOpenWebView" -> handleCloseOpenWebView(data)
                "externalOpenBrowser" -> handleExternalOpenBrowser(data)
                // Quiz-specific message types
                "quizCompleted" -> if (contextType == "quiz") handleQuizCompleted(data)
                // Mission-specific message types
                "missionCompleted" -> handleMissionCompleted(data)
                "missionProgressed" -> handleMissionProgressed(data)
                "getUserInfo" -> handleGetUserInfo()
                else -> AdchainLogger.d(TAG, "Unknown message type: $type")
            }
        } catch (e: Exception) {
            AdchainLogger.e(TAG, "Failed to parse JS message", e)
        }
    }
    
    // Message handlers for JavaScript bridge
    private fun handleOpenWebView(data: JSONObject?) {
        val url = data?.optString("url")
        if (url.isNullOrEmpty()) {
            AdchainLogger.e(TAG, "openWebView: No URL provided")
            return
        }
        
        AdchainLogger.d(TAG, "Opening sub WebView: $url")
        
        // Ensure UI operations run on UI thread
        runOnUiThread {
            openSubWebView(this, url)
        }
        
        // Track event
        coroutineScope.launch {
            val userId = intent.getStringExtra(EXTRA_USER_ID) ?: ""
            NetworkManager.trackEvent(
                userId = userId,
                eventName = "sub_webview_opened",
                sdkVersion = BuildConfig.VERSION_NAME,
                category = "offerwall",
                properties = mapOf("url" to url)
            )
        }
    }
    
    private fun handleClose() {
        AdchainLogger.d(TAG, "Handling close message")
        
        runOnUiThread {
            // Close all WebViews
            closeAllWebViews()
            
            // Close main offerwall
            if (!isSubWebView) {
                callback?.onClosed()
            }
            
            finish()
        }
    }
    
    private fun handleCloseOpenWebView(data: JSONObject?) {
        val url = data?.optString("url")
        AdchainLogger.d(TAG, "Handling closeOpenWebView message - isSubWebView: $isSubWebView, url: $url")
        
        if (url.isNullOrEmpty()) {
            AdchainLogger.e(TAG, "closeOpenWebView: No URL provided")
            return
        }
        
        runOnUiThread {
            // Create intent for new WebView with proper flags
            val intent = Intent(this@AdchainOfferwallActivity, AdchainOfferwallActivity::class.java).apply {
                putExtra(EXTRA_BASE_URL, url)
                putExtra(EXTRA_IS_SUB_WEBVIEW, true)
                putExtra(EXTRA_USER_ID, AdchainSdk.getCurrentUser()?.userId)
                putExtra(EXTRA_APP_ID, AdchainSdk.getConfig()?.appKey)
                // Clear current activity and create new one
                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
            }
            
            // Start new activity
            startActivity(intent)
            
            // Close current activity with fade animation
            finish()
            
            // Use new API for Android 14+ (API 34), fallback for older versions
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                overrideActivityTransition(
                    OVERRIDE_TRANSITION_OPEN,
                    android.R.anim.fade_in,
                    android.R.anim.fade_out
                )
            } else {
                @Suppress("DEPRECATION")
                overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
            }
            
            // Track event
            coroutineScope.launch {
                val userId = intent.getStringExtra(EXTRA_USER_ID) ?: ""
                NetworkManager.trackEvent(
                    userId = userId,
                    eventName = "webview_replaced",
                    sdkVersion = BuildConfig.VERSION_NAME,
                    category = "offerwall",
                    properties = mapOf("url" to url)
                )
            }
        }
    }
    
    private fun handleExternalOpenBrowser(data: JSONObject?) {
        val url = data?.optString("url")
        if (url.isNullOrEmpty()) {
            AdchainLogger.e(TAG, "externalOpenBrowser: No URL provided")
            return
        }
        
        AdchainLogger.d(TAG, "Opening external browser: $url")
        
        runOnUiThread {
            try {
                val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                startActivity(browserIntent)
                
                // Track event
                coroutineScope.launch {
                    val userId = intent.getStringExtra(EXTRA_USER_ID) ?: ""
                    NetworkManager.trackEvent(
                        userId = userId,
                        eventName = "external_browser_opened",
                        sdkVersion = BuildConfig.VERSION_NAME,
                        category = "offerwall",
                        properties = mapOf("url" to url)
                    )
                }
            } catch (e: ActivityNotFoundException) {
                AdchainLogger.e(TAG, "No browser found to open URL: $url", e)
                Toast.makeText(this@AdchainOfferwallActivity, "No browser app found", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                AdchainLogger.e(TAG, "Failed to open external browser", e)
                Toast.makeText(this@AdchainOfferwallActivity, "Failed to open browser", Toast.LENGTH_SHORT).show()
            }
        }
    }
    
    // Quiz-specific message handlers
    private fun handleQuizCompleted(data: JSONObject?) {
        AdchainLogger.i(TAG, "Quiz completed")
        
        runOnUiThread {
            // Track quiz completion
            coroutineScope.launch {
                val userId = intent.getStringExtra(EXTRA_USER_ID) ?: ""
                val quizId = intent.getStringExtra(EXTRA_QUIZ_ID) ?: ""
                
                NetworkManager.trackEvent(
                    userId = userId,
                    eventName = "quiz_completed",
                    sdkVersion = BuildConfig.VERSION_NAME,
                    category = "quiz",
                    properties = mapOf(
                        "quiz_id" to quizId
                    )
                )
                
                // Notify quiz completion and refresh list
                val quizInstance = AdchainQuiz.currentQuizInstance?.get()
                val quizEvent = AdchainQuiz.currentQuizEvent
                
                if (quizInstance != null && quizEvent != null) {
                    // iOS와 동일한 방식: 리스너 호출
                    quizInstance.notifyQuizCompleted(quizEvent)
                } else {
                    // Fallback: 리스너가 없으면 기존 방식으로 새로고침
                    quizInstance?.refreshAfterCompletion()
                }
                
                // DO NOT call onClosed() here
                // Quiz completion should only trigger data refresh, not close the WebView
                // The WebView should remain open until user manually closes it
                // callback?.onClosed() // Removed to prevent duplicate callback invocation
            }
            // Don't finish() - let WebView stay open for user to continue or close manually
        }
    }
    
    private fun handleMissionCompleted(data: JSONObject?) {
        AdchainLogger.i(TAG, "Mission completed")

        val missionId = data?.optString("missionId") ?: ""

        runOnUiThread {
            // Track mission completion
            coroutineScope.launch {
                val userId = intent.getStringExtra(EXTRA_USER_ID) ?: ""

                NetworkManager.trackEvent(
                    userId = userId,
                    eventName = "mission_completed",
                    sdkVersion = BuildConfig.VERSION_NAME,
                    category = "mission",
                    properties = mapOf(
                        "mission_id" to missionId
                    )
                )

                // Notify mission completion and refresh list
                val missionInstance = AdchainMission.currentMissionInstance
                val mission = AdchainMission.currentMission

                if (missionInstance != null && mission != null) {
                    // iOS와 동일한 방식: 리스너 호출
                    missionInstance.onMissionCompleted(mission)
                } else {
                    // Fallback: 리스너가 없으면 기존 방식으로 새로고침
                    missionInstance?.refreshAfterCompletion()
                }

                // DO NOT call onClosed() here
                // Mission completion should only trigger data refresh, not close the WebView
                // The WebView should remain open until user manually closes it
                // callback?.onClosed() // Removed to prevent duplicate callback invocation
            }
        }
    }

    private fun handleMissionProgressed(data: JSONObject?) {
        AdchainLogger.i(TAG, "Mission progressed")

        val missionId = data?.optString("missionId") ?: ""

        runOnUiThread {
            // Track mission progress
            coroutineScope.launch {
                val userId = intent.getStringExtra(EXTRA_USER_ID) ?: ""

                NetworkManager.trackEvent(
                    userId = userId,
                    eventName = "mission_progressed",
                    sdkVersion = BuildConfig.VERSION_NAME,
                    category = "mission",
                    properties = mapOf(
                        "mission_id" to missionId
                    )
                )

                // Notify mission progress (without progress parameter)
                val missionInstance = AdchainMission.currentMissionInstance
                val mission = AdchainMission.currentMission

                if (missionInstance != null && mission != null) {
                    AdchainLogger.d(TAG, "🔄 [Android SDK - WebView] Mission 진행 알림...")
                    missionInstance.onMissionProgressed(mission)
                    AdchainLogger.d(TAG, "✅ [Android SDK - WebView] Mission 진행 알림 완료!")
                } else {
                    AdchainLogger.d(TAG, "⚠️ [Android SDK - WebView] Mission instance 또는 mission을 찾을 수 없음")
                }

                // DO NOT call onClosed() here
                // Mission progress should only trigger UI update, not close the WebView
            }
        }
    }

    /**
     * Show offline page when network error occurs
     */
    private fun showOfflinePage() {
        runOnUiThread {
            try {
                // Load offline HTML from assets
                webView.loadUrl("file:///android_asset/adchain/offline_error.html")
            } catch (e: Exception) {
                AdchainLogger.e(TAG, "Failed to load offline page", e)
                // Fallback: close the activity
                finish()
            }
        }
    }

    private fun handleGetUserInfo() {
        AdchainSdk.getCurrentUser()?.let { user ->
            val userInfo = JSONObject().apply {
                put("userId", user.userId)
                put("gender", user.gender?.value ?: "")
                put("birthYear", user.birthYear ?: 0)
            }
            
            val script = """
                if (window.onUserInfoReceived) {
                    window.onUserInfoReceived($userInfo);
                }
            """.trimIndent()
            
            webView.evaluateJavascript(script, null)
        }
    }
}