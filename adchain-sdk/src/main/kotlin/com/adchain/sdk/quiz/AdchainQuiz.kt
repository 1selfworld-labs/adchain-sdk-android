package com.adchain.sdk.quiz

import android.content.Intent
import android.util.Log
import com.adchain.sdk.BuildConfig
import com.adchain.sdk.core.AdchainSdk
import com.adchain.sdk.common.AdchainAdError
import com.adchain.sdk.network.ApiClient
import com.adchain.sdk.network.ApiService
import com.adchain.sdk.network.NetworkManager
import com.adchain.sdk.offerwall.AdchainOfferwallActivity
import com.adchain.sdk.offerwall.OfferwallCallback
import com.adchain.sdk.quiz.models.QuizEvent
import com.adchain.sdk.utils.DeviceUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.lang.ref.WeakReference

class AdchainQuiz(private val unitId: String) {
    
    companion object {
        private const val TAG = "AdchainQuiz"
        internal var currentQuizInstance: WeakReference<AdchainQuiz>? = null
        internal var currentQuizEvent: QuizEvent? = null
    }
    
    private var quizEvents: List<QuizEvent> = emptyList()
    private var lastOnSuccess: ((List<QuizEvent>) -> Unit)? = null
    private var lastOnFailure: ((AdchainAdError) -> Unit)? = null
    private val coroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val apiService: ApiService by lazy {
        ApiClient.createService(ApiService::class.java)
    }
    
    // Event listener (iOS와 동일한 방식)
    private var eventsListener: AdchainQuizEventsListener? = null
    
    fun setQuizEventsListener(listener: AdchainQuizEventsListener) {
        this.eventsListener = listener
    }
    
    /**
     * Get quiz list from server
     * Automatically tracks impression when quiz list is fetched
     */
    @JvmOverloads
    fun getQuizList(
        onSuccess: (List<QuizEvent>) -> Unit,
        onFailure: (AdchainAdError) -> Unit,
        shouldStoreCallbacks: Boolean = true
    ) {
        // Store callbacks for refresh (only if requested)
        if (shouldStoreCallbacks) {
            lastOnSuccess = onSuccess
            lastOnFailure = onFailure
        }

        if (!AdchainSdk.isLoggedIn) {
            Log.e(TAG, "SDK not initialized or user not logged in")
            onFailure(AdchainAdError.NOT_INITIALIZED)
            return
        }

        coroutineScope.launch {
            try {
                val currentUser = AdchainSdk.getCurrentUser()
                val context = AdchainSdk.getApplication()
                
                // Get advertising ID
                val advertisingId = withContext(Dispatchers.IO) {
                    context?.let {
                        DeviceUtils.getAdvertisingIdSync(it) ?: DeviceUtils.getAdvertisingId(it)
                    }
                }
                
                val response = withContext(Dispatchers.IO) {
                    apiService.getQuizEvents(
                        userId = currentUser?.userId,
                        platform = "Android",
                        ifa = advertisingId
                    )
                }
                
                if (response.isSuccessful) {
                    response.body()?.let { quizResponse ->
                        quizEvents = quizResponse.events
                        Log.d(TAG, "Loaded ${quizEvents.size} quiz events")
                        
                        // Track impression for all quizzes
                        quizEvents.forEach { quiz ->
                            trackImpression(quiz)
                        }
                        
                        onSuccess(quizEvents)
                    } ?: run {
                        Log.e(TAG, "Empty response body")
                        onFailure(AdchainAdError.EMPTY_RESPONSE)
                    }
                } else {
                    Log.e(TAG, "Failed to load quiz events: ${response.code()}")
                    onFailure(AdchainAdError.NETWORK_ERROR)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error loading quiz events", e)
                onFailure(AdchainAdError.UNKNOWN)
            }
        }
    }
    
    private fun trackImpression(quizEvent: QuizEvent) {
        Log.d(TAG, "Tracking impression for quiz: ${quizEvent.id}")
        
        coroutineScope.launch {
            val userId = AdchainSdk.getCurrentUser()?.userId ?: ""
            NetworkManager.trackEvent(
                userId = userId,
                eventName = "quiz_impressed",
                sdkVersion = BuildConfig.VERSION_NAME,
                category = "quiz",
                properties = mapOf(
                    "quizId" to quizEvent.id,
                    "quizTitle" to quizEvent.title
                )
            )
        }
    }
    
    private fun trackClick(quizEvent: QuizEvent) {
        Log.d(TAG, "Tracking click for quiz: ${quizEvent.id}")
        
        coroutineScope.launch {
            val userId = AdchainSdk.getCurrentUser()?.userId ?: ""
            NetworkManager.trackEvent(
                userId = userId,
                eventName = "quiz_clicked",
                sdkVersion = BuildConfig.VERSION_NAME,
                category = "quiz",
                properties = mapOf(
                    "quizId" to quizEvent.id,
                    "quizTitle" to quizEvent.title,
                    "landingUrl" to quizEvent.landingUrl
                )
            )
        }
    }
    
    /**
     * Click a quiz by ID
     * Tracks click event and opens WebView
     */
    fun clickQuiz(quizId: String) {
        val quizEvent = quizEvents.find { it.id == quizId }
        if (quizEvent == null) {
            Log.e(TAG, "Quiz not found: $quizId")
            return
        }
        
        // Track click
        trackClick(quizEvent)
        
        // Open WebView
        openQuizWebView(quizEvent)
    }
    
    /**
     * Called when a quiz is completed - notifies listener
     * iOS와 동일한 방식
     */
    internal fun notifyQuizCompleted(quizEvent: QuizEvent) {
        Log.d(TAG, "Quiz completed: ${quizEvent.id}")
        
        // Notify listener
        eventsListener?.onQuizCompleted(quizEvent, 0)
        
        // Track completion
        coroutineScope.launch {
            NetworkManager.trackEvent(
                userId = AdchainSdk.getCurrentUser()?.userId ?: "",
                eventName = "quiz_completed",
                sdkVersion = BuildConfig.VERSION_NAME,
                category = "quiz",
                properties = mapOf(
                    "quizId" to quizEvent.id,
                    "quizTitle" to quizEvent.title
                )
            )
        }
        
        // Refresh list
        refreshAfterCompletion()
    }
    
    /**
     * Refresh quiz list after completion (internal use)
     * Called when a quiz is completed to refresh the list
     */
    internal fun refreshAfterCompletion() {
        // Store current callbacks if they exist
        val savedOnSuccess = lastOnSuccess
        val savedOnFailure = lastOnFailure
        
        // Re-fetch quiz list if callbacks are available
        if (savedOnSuccess != null && savedOnFailure != null) {
            getQuizList(savedOnSuccess, savedOnFailure, shouldStoreCallbacks = false)
        }
    }
    
    /**
     * Opens quiz WebView using AdchainOfferwallActivity
     */
    private fun openQuizWebView(quizEvent: QuizEvent) {
        val context = AdchainSdk.getApplication()
        if (context == null) {
            Log.e(TAG, "Application context not available")
            return
        }
        
        // Store reference for callback
        currentQuizInstance = WeakReference(this)
        currentQuizEvent = quizEvent
        
        // Setup quiz callback
        val quizCallback = object : OfferwallCallback {
            override fun onOpened() {
                Log.d(TAG, "Quiz WebView opened")
                // Quiz opened tracking is already done below
            }
            
            override fun onClosed() {
                Log.d(TAG, "Quiz WebView closed")
                // Clear references
                currentQuizInstance = null
                currentQuizEvent = null
            }
            
            override fun onError(message: String) {
                Log.e(TAG, "Quiz WebView error: $message")
                // Clear references
                currentQuizInstance = null
                currentQuizEvent = null
            }
            
            override fun onRewardEarned(amount: Int) {
                Log.d(TAG, "Quiz reward earned: $amount")
                // Can be used if quiz has rewards
            }
        }
        
        // Set callback for AdchainOfferwallActivity
        AdchainOfferwallActivity.setCallback(quizCallback)
        
        // Create intent with quiz parameters
        val intent = Intent(context, AdchainOfferwallActivity::class.java).apply {
            putExtra(AdchainOfferwallActivity.EXTRA_BASE_URL, quizEvent.landingUrl)
            putExtra(AdchainOfferwallActivity.EXTRA_USER_ID, AdchainSdk.getCurrentUser()?.userId)
            putExtra(AdchainOfferwallActivity.EXTRA_APP_ID, AdchainSdk.getConfig()?.appKey)
            putExtra(AdchainOfferwallActivity.EXTRA_CONTEXT_TYPE, "quiz")
            putExtra(AdchainOfferwallActivity.EXTRA_QUIZ_ID, quizEvent.id)
            putExtra(AdchainOfferwallActivity.EXTRA_QUIZ_TITLE, quizEvent.title)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        
        context.startActivity(intent)
    }
}