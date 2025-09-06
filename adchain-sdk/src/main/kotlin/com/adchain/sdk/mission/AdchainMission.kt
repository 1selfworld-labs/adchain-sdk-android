package com.adchain.sdk.mission

import android.content.Context
import android.content.Intent
import android.util.Log
import com.adchain.sdk.core.AdchainSdk
import com.adchain.sdk.common.AdchainAdError
import com.adchain.sdk.network.ApiClient
import com.adchain.sdk.network.ApiService
import com.adchain.sdk.offerwall.AdchainOfferwallActivity
import com.adchain.sdk.offerwall.OfferwallCallback
import kotlinx.coroutines.*
import java.lang.ref.WeakReference

class AdchainMission(private val unitId: String) {
    
    companion object {
        private const val TAG = "AdchainMission"
        internal var currentMissionInstance: WeakReference<AdchainMission>? = null
        private var currentMission: Mission? = null
    }
    
    private var missions: List<Mission> = emptyList()
    private var missionResponse: MissionResponse? = null
    private var rewardUrl: String? = null
    private var eventsListener: AdchainMissionEventsListener? = null
    private val participatingMissions = mutableSetOf<String>()
    private val coroutineScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private val apiService: ApiService by lazy {
        ApiClient.createService(ApiService::class.java)
    }
    
    private var loadSuccessCallback: ((List<Mission>, MissionProgress) -> Unit)? = null
    private var loadFailureCallback: ((AdchainAdError) -> Unit)? = null
    
    fun load(
        onSuccess: (List<Mission>, MissionProgress) -> Unit,
        onFailure: (AdchainAdError) -> Unit
    ) {
        Log.d(TAG, "Loading missions for unit: $unitId")
        
        // Store callbacks for potential refresh
        loadSuccessCallback = onSuccess
        loadFailureCallback = onFailure
        
        if (!AdchainSdk.isLoggedIn) {
            Log.e(TAG, "SDK not initialized or user not logged in")
            onFailure(AdchainAdError.NOT_INITIALIZED)
            return
        }
        
        coroutineScope.launch {
            try {
                val response = withContext(Dispatchers.IO) {
                    apiService.getMissions()
                }
                
                if (response.isSuccessful) {
                    response.body()?.let { missionResp ->
                        missionResponse = missionResp
                        var missionsToShow = missionResp.events.toMutableList()
                        rewardUrl = missionResp.rewardUrl
                        
                        val progress = MissionProgress(
                            current = missionResp.current,
                            total = missionResp.total
                        )
                        
                        // Add offerwall promotion if missions are not completed
                        if (missionResp.current < missionResp.total && missionResp.total > 0) {
                            val offerwallPromotion = Mission(
                                id = "offerwall_promotion",
                                title = "800만 포인트 받으러 가기",
                                description = "더 많은 포인트를 받을 수 있습니다",
                                point = "800만 포인트",
                                imageUrl = "",
                                landingUrl = "",
                                metadata = null,
                                type = MissionType.OFFERWALL_PROMOTION
                            )
                            missionsToShow.add(offerwallPromotion)
                        }
                        
                        missions = missionsToShow
                        
                        Log.d(TAG, "Loaded ${missions.size} missions, progress: ${missionResp.current}/${missionResp.total}, reward_url: ${rewardUrl}")
                        onSuccess(missions, progress)
                    } ?: run {
                        Log.e(TAG, "Empty response body")
                        onFailure(AdchainAdError.EMPTY_RESPONSE)
                    }
                } else {
                    Log.e(TAG, "Failed to load missions: ${response.code()}")
                    onFailure(AdchainAdError.NETWORK_ERROR)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error loading missions", e)
                onFailure(AdchainAdError.UNKNOWN)
            }
        }
    }
    
    fun setEventsListener(listener: AdchainMissionEventsListener) {
        this.eventsListener = listener
    }
    
    fun getMissions(): List<Mission> = missions
    
    fun getMission(missionId: String): Mission? {
        return missions.find { it.id == missionId }
    }
    
    fun onRewardButtonClicked(context: Context) {
        Log.d(TAG, "Reward button clicked")
        openRewardWebView(context)
    }
    
    fun markAsParticipating(missionId: String) {
        participatingMissions.add(missionId)
        Log.d(TAG, "Mission marked as participating: $missionId")
    }
    
    fun isParticipating(missionId: String): Boolean {
        return participatingMissions.contains(missionId)
    }
    
    fun onMissionClicked(mission: Mission) {
        Log.d(TAG, "Mission clicked: ${mission.id}")
        eventsListener?.onClicked(mission)
    }
    
    fun onMissionImpressed(mission: Mission) {
        Log.d(TAG, "Mission impressed: ${mission.id}")
        eventsListener?.onImpressed(mission)
    }
    
    fun onMissionCompleted(mission: Mission) {
        Log.d(TAG, "Mission completed: ${mission.id}")
        eventsListener?.onCompleted(mission)
        
        // Refresh the mission list after completion
        refreshAfterCompletion()
    }
    
    internal fun refreshAfterCompletion() {
        Log.d(TAG, "Refreshing mission list after completion")
        
        loadSuccessCallback?.let { successCallback ->
            loadFailureCallback?.let { failureCallback ->
                load(successCallback, failureCallback)
            }
        }
    }
    
    /**
     * Opens reward WebView for claiming mission completion rewards
     */
    internal fun openRewardWebView(context: Context) {
        if (rewardUrl.isNullOrEmpty()) {
            Log.e(TAG, "No reward URL available")
            return
        }
        
        // Setup reward callback
        val rewardCallback = object : OfferwallCallback {
            override fun onOpened() {
                Log.d(TAG, "Reward WebView opened")
            }
            
            override fun onClosed() {
                Log.d(TAG, "Reward WebView closed")
                // Refresh missions after claiming reward
                refreshAfterCompletion()
            }
            
            override fun onError(message: String) {
                Log.e(TAG, "Reward WebView error: $message")
            }
            
            override fun onRewardEarned(amount: Int) {
                Log.d(TAG, "Reward earned: $amount")
                eventsListener?.onCompleted(Mission("", "", "", "", "", "", null))
            }
        }
        
        // Set callback for AdchainOfferwallActivity
        AdchainOfferwallActivity.setCallback(rewardCallback)
        
        // Create intent with reward URL
        val intent = Intent(context, AdchainOfferwallActivity::class.java).apply {
            putExtra(AdchainOfferwallActivity.EXTRA_BASE_URL, rewardUrl)
            putExtra(AdchainOfferwallActivity.EXTRA_USER_ID, AdchainSdk.getCurrentUser()?.userId)
            putExtra(AdchainOfferwallActivity.EXTRA_APP_ID, AdchainSdk.getConfig()?.appKey)
        }
        
        // Start activity
        context.startActivity(intent)
        
        Log.d(TAG, "Opening reward WebView with URL: $rewardUrl")
    }
    
    /**
     * Opens mission WebView using AdchainOfferwallActivity
     */
    internal fun openMissionWebView(context: Context, mission: Mission) {
        // Store reference for callback
        currentMissionInstance = WeakReference(this)
        currentMission = mission
        
        // Setup mission callback
        val missionCallback = object : OfferwallCallback {
            override fun onOpened() {
                Log.d(TAG, "Mission WebView opened")
            }
            
            override fun onClosed() {
                Log.d(TAG, "Mission WebView closed")
                // Clear references
                currentMissionInstance = null
                currentMission = null
            }
            
            override fun onError(message: String) {
                Log.e(TAG, "Mission WebView error: $message")
                // Clear references
                currentMissionInstance = null
                currentMission = null
            }
            
            override fun onRewardEarned(amount: Int) {
                Log.d(TAG, "Mission reward earned: $amount")
            }
        }
        
        // Set callback for AdchainOfferwallActivity
        AdchainOfferwallActivity.setCallback(missionCallback)
        
        // Create intent with mission parameters
        val intent = Intent(context, AdchainOfferwallActivity::class.java).apply {
            putExtra(AdchainOfferwallActivity.EXTRA_BASE_URL, mission.landingUrl)
            putExtra(AdchainOfferwallActivity.EXTRA_USER_ID, AdchainSdk.getCurrentUser()?.userId)
            putExtra(AdchainOfferwallActivity.EXTRA_APP_ID, AdchainSdk.getConfig()?.appKey)
        }
        
        // Start activity
        context.startActivity(intent)
        
        // Track mission open
        Log.d(TAG, "Opening mission WebView for mission: ${mission.id}")
    }
    
    fun destroy() {
        coroutineScope.cancel()
        eventsListener = null
        missions = emptyList()
        missionResponse = null
    }
}