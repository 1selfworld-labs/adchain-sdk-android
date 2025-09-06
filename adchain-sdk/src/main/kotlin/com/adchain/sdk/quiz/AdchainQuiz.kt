package com.adchain.sdk.quiz

import android.content.Context
import android.content.Intent
import android.util.Log
import com.adchain.sdk.core.AdchainSdk
import com.adchain.sdk.common.AdchainAdError
import com.adchain.sdk.network.ApiClient
import com.adchain.sdk.network.ApiService
import com.adchain.sdk.network.NetworkManager
import com.adchain.sdk.offerwall.AdchainOfferwallActivity
import com.adchain.sdk.offerwall.OfferwallCallback
import com.adchain.sdk.quiz.models.QuizEvent
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
        private var currentQuizEvent: QuizEvent? = null
    }
    
    private var quizEvents: List<QuizEvent> = emptyList()
    private var listener: AdchainQuizEventsListener? = null
    private var loadSuccessCallback: ((List<QuizEvent>) -> Unit)? = null
    private var loadFailureCallback: ((AdchainAdError) -> Unit)? = null
    private val coroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val apiService: ApiService by lazy {
        ApiClient.createService(ApiService::class.java)
    }
    
    fun setQuizEventsListener(listener: AdchainQuizEventsListener) {
        this.listener = listener
    }
    
    fun load(
        onSuccess: (List<QuizEvent>) -> Unit,
        onFailure: (AdchainAdError) -> Unit
    ) {
        // Store callbacks for refresh
        loadSuccessCallback = onSuccess
        loadFailureCallback = onFailure
        
        coroutineScope.launch {
            try {
                val userId = AdchainSdk.getCurrentUser()?.userId
                
                val response = withContext(Dispatchers.IO) {
                    //apiService.getQuizEvents(userId)
                    apiService.getQuizEvents()
                }
                
                if (response.isSuccessful) {
                    response.body()?.let { quizResponse ->
                        quizEvents = quizResponse.events
                        Log.d(TAG, "Loaded ${quizEvents.size} quiz events")
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
    
    fun trackImpression(quizEvent: QuizEvent) {
        Log.d(TAG, "Tracking impression for quiz: ${quizEvent.id}")
        listener?.onImpressed(quizEvent)
        
        coroutineScope.launch {
            val userId = AdchainSdk.getCurrentUser()?.userId ?: ""
            NetworkManager.trackEvent(
                userId = userId,
                eventName = "quiz_impressed",
                category = "quiz",
                properties = mapOf(
                    "quiz_id" to quizEvent.id,
                    "quiz_title" to quizEvent.title
                )
            )
        }
    }
    
    fun trackClick(quizEvent: QuizEvent) {
        Log.d(TAG, "Tracking click for quiz: ${quizEvent.id}")
        listener?.onClicked(quizEvent)
        
        coroutineScope.launch {
            val userId = AdchainSdk.getCurrentUser()?.userId ?: ""
            NetworkManager.trackEvent(
                userId = userId,
                eventName = "quiz_clicked",
                category = "quiz",
                properties = mapOf(
                    "quiz_id" to quizEvent.id,
                    "quiz_title" to quizEvent.title,
                    "landing_url" to quizEvent.landingUrl
                )
            )
        }
    }
    
    internal fun notifyQuizCompleted(quizEvent: QuizEvent) {
        listener?.onQuizCompleted(quizEvent, 0)  // Keep 0 for backward compatibility
        
        coroutineScope.launch {
            val userId = AdchainSdk.getCurrentUser()?.userId ?: ""
            NetworkManager.trackEvent(
                userId = userId,
                eventName = "quiz_completed",
                category = "quiz",
                properties = mapOf(
                    "quiz_id" to quizEvent.id,
                    "quiz_title" to quizEvent.title
                )
            )
        }
        
        // Automatically refresh quiz list after completion
        refreshAfterCompletion()
    }
    
    internal fun refreshAfterCompletion() {
        Log.d(TAG, "Refreshing quiz list after completion")
        // Use stored callbacks to notify the UI
        loadSuccessCallback?.let { successCallback ->
            loadFailureCallback?.let { failureCallback ->
                load(successCallback, failureCallback)
            }
        } ?: run {
            // Fallback if callbacks are not stored
            Log.w(TAG, "No callbacks stored for refresh, skipping UI update")
        }
    }
    
    /**
     * Opens quiz WebView using AdchainOfferwallActivity
     */
    internal fun openQuizWebView(context: Context, quizEvent: QuizEvent) {
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
                // Don't call onQuizCompleted here - only call it when JavaScript sends quizCompleted message
                // Clear references
                currentQuizInstance = null
                currentQuizEvent = null
            }
            
            override fun onError(message: String) {
                Log.e(TAG, "Quiz WebView error: $message")
                // Note: AdchainQuizEventsListener doesn't have onError method,
                // so we just log the error for now
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
        
        // Track quiz open
        coroutineScope.launch {
            val userId = AdchainSdk.getCurrentUser()?.userId ?: ""
            NetworkManager.trackEvent(
                userId = userId,
                eventName = "quiz_webview_opened",
                category = "quiz",
                properties = mapOf(
                    "quiz_id" to quizEvent.id,
                    "url" to quizEvent.landingUrl
                )
            )
        }
    }
}