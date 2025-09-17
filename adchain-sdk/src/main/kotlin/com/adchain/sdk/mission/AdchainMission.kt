package com.adchain.sdk.mission

import android.content.Intent
import com.adchain.sdk.BuildConfig
import com.adchain.sdk.utils.AdchainLogger
import com.adchain.sdk.core.AdchainSdk
import com.adchain.sdk.common.AdchainAdError
import com.adchain.sdk.network.ApiClient
import com.adchain.sdk.network.ApiService
import com.adchain.sdk.network.NetworkManager
import com.adchain.sdk.offerwall.AdchainOfferwallActivity
import com.adchain.sdk.offerwall.OfferwallCallback
import com.adchain.sdk.utils.DeviceUtils
import kotlinx.coroutines.*
class AdchainMission(private val unitId: String) {
    
    companion object {
        private const val TAG = "AdchainMission"
        // 강한 참조 유지 - 메모리 사용량이 크지 않으므로 계속 유지
        internal var currentMissionInstance: AdchainMission? = null
        internal var currentMission: Mission? = null
    }
    
    private var missions: List<Mission> = emptyList()
    private var missionResponse: MissionResponse? = null
    private var rewardUrl: String? = null
    private var missionProgress: MissionProgress? = null
    private val coroutineScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private val apiService: ApiService by lazy {
        ApiClient.createService(ApiService::class.java)
    }
    
    // Store callbacks for refresh
    private var lastOnSuccess: ((List<Mission>) -> Unit)? = null
    private var lastOnFailure: ((AdchainAdError) -> Unit)? = null
    
    // Event listener (iOS와 동일한 방식)
    private var eventsListener: AdchainMissionEventsListener? = null
    
    fun setEventsListener(listener: AdchainMissionEventsListener) {
        this.eventsListener = listener
    }
    
    /**
     * Get mission list from server
     * Automatically tracks impression when mission list is fetched
     */
    @JvmOverloads
    fun getMissionList(
        onSuccess: (List<Mission>) -> Unit,
        onFailure: (AdchainAdError) -> Unit,
        shouldStoreCallbacks: Boolean = true
    ) {
        AdchainLogger.d(TAG, "Loading missions for unit: $unitId")
        
        // Store callbacks for refresh (only if requested)
        if (shouldStoreCallbacks) {
            lastOnSuccess = onSuccess
            lastOnFailure = onFailure
        }
        
        if (!AdchainSdk.isLoggedIn) {
            AdchainLogger.e(TAG, "SDK not initialized or user not logged in")
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
                    apiService.getMissions(
                        userId = currentUser?.userId,
                        platform = "Android",
                        ifa = advertisingId
                    )
                }
                
                if (response.isSuccessful) {
                    response.body()?.let { missionResp ->
                        missionResponse = missionResp
                        var missionsToShow = missionResp.events.toMutableList()
                        rewardUrl = missionResp.rewardUrl
                        
                        missionProgress = MissionProgress(
                            current = missionResp.current,
                            total = missionResp.total
                        )
                        
                        missions = missionsToShow
                        
                        AdchainLogger.i(TAG, "Loaded ${missions.size} missions, progress: ${missionResp.current}/${missionResp.total}, reward_url: ${rewardUrl}")
                        
                        // Track impression for all missions
                        missions.forEach { mission ->
                            onMissionImpressed(mission)
                        }
                        
                        onSuccess(missions)
                    } ?: run {
                        AdchainLogger.e(TAG, "Empty response body")
                        onFailure(AdchainAdError.EMPTY_RESPONSE)
                    }
                } else {
                    AdchainLogger.e(TAG, "Failed to load missions: ${response.code()}")
                    onFailure(AdchainAdError.NETWORK_ERROR)
                }
            } catch (e: Exception) {
                AdchainLogger.e(TAG, "Error loading missions", e)
                onFailure(AdchainAdError.UNKNOWN)
            }
        }
    }
    
    /**
     * Get mission completion status
     */
    fun getMissionStatus(
        onSuccess: (MissionStatus) -> Unit,
        onFailure: (AdchainAdError) -> Unit
    ) {
        if (missionProgress == null) {
            // If no cached data, fetch from server
            getMissionList(
                onSuccess = { 
                    missionProgress?.let { progress ->
                        onSuccess(MissionStatus.from(progress))
                    } ?: onFailure(AdchainAdError.EMPTY_RESPONSE)
                },
                onFailure = onFailure
            )
        } else {
            // Return cached status
            onSuccess(MissionStatus.from(missionProgress!!))
        }
    }
    
    /**
     * Click a mission by ID
     * Tracks click event and opens WebView
     */
    fun clickMission(missionId: String) {
        val mission = missions.find { it.id == missionId }
        if (mission == null) {
            AdchainLogger.e(TAG, "Mission not found: $missionId")
            return
        }
        
        // Track click
        onMissionClicked(mission)
        
        // Open WebView
        openMissionWebView(mission)
    }
    
    /**
     * Click get reward button
     * Opens reward WebView
     */
    fun clickGetReward() {
        AdchainLogger.d(TAG, "Get reward clicked")
        openRewardWebView()
    }
    
    private fun onMissionClicked(mission: Mission) {
        AdchainLogger.d(TAG, "Mission clicked: ${mission.id}")
        
        coroutineScope.launch {
            val userId = AdchainSdk.getCurrentUser()?.userId ?: ""
            NetworkManager.trackEvent(
                userId = userId,
                eventName = "mission_clicked",
                sdkVersion = BuildConfig.VERSION_NAME,
                category = "mission",
                properties = mapOf(
                    "missionId" to mission.id,
                    "missionTitle" to mission.title
                )
            )
        }
    }
    
    private fun onMissionImpressed(mission: Mission) {
        AdchainLogger.d(TAG, "Mission impressed: ${mission.id}")
        
        coroutineScope.launch {
            val userId = AdchainSdk.getCurrentUser()?.userId ?: ""
            NetworkManager.trackEvent(
                userId = userId,
                eventName = "mission_impressed",
                sdkVersion = BuildConfig.VERSION_NAME,
                category = "mission",
                properties = mapOf(
                    "missionId" to mission.id,
                    "missionTitle" to mission.title
                )
            )
        }
    }
    
    /**
     * Opens reward WebView for claiming mission completion rewards
     */
    private fun openRewardWebView() {
        val context = AdchainSdk.getApplication()
        if (context == null) {
            AdchainLogger.e(TAG, "Application context not available")
            return
        }
        
        if (rewardUrl.isNullOrEmpty()) {
            AdchainLogger.e(TAG, "No reward URL available")
            return
        }
        
        // Setup reward callback
        val rewardCallback = object : OfferwallCallback {
            override fun onOpened() {
                AdchainLogger.d(TAG, "Reward WebView opened")
            }
            
            override fun onClosed() {
                AdchainLogger.d(TAG, "Reward WebView closed")
            }
            
            override fun onError(message: String) {
                AdchainLogger.e(TAG, "Reward WebView error: $message")
            }
            
            override fun onRewardEarned(amount: Int) {
                AdchainLogger.d(TAG, "Reward earned: $amount")
            }
        }
        
        // Set callback for AdchainOfferwallActivity
        AdchainOfferwallActivity.setCallback(rewardCallback)
        
        // Create intent with reward URL
        val intent = Intent(context, AdchainOfferwallActivity::class.java).apply {
            putExtra(AdchainOfferwallActivity.EXTRA_BASE_URL, rewardUrl)
            putExtra(AdchainOfferwallActivity.EXTRA_USER_ID, AdchainSdk.getCurrentUser()?.userId)
            putExtra(AdchainOfferwallActivity.EXTRA_APP_ID, AdchainSdk.getConfig()?.appKey)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        
        // Start activity
        context.startActivity(intent)
        
        AdchainLogger.d(TAG, "Opening reward WebView with URL: $rewardUrl")
    }
    
    /**
     * Opens mission WebView using AdchainOfferwallActivity
     */
    private fun openMissionWebView(mission: Mission) {
        val context = AdchainSdk.getApplication()
        if (context == null) {
            AdchainLogger.e(TAG, "Application context not available")
            return
        }
        
        // Store reference for callback
        currentMissionInstance = this
        currentMission = mission
        
        // Setup mission callback
        val missionCallback = object : OfferwallCallback {
            override fun onOpened() {
                AdchainLogger.d(TAG, "Mission WebView opened")
            }
            
            override fun onClosed() {
                AdchainLogger.d(TAG, "Mission WebView closed")
                // instance는 null로 만들지 않음 - 메모리 사용량이 크지 않음
            }
            
            override fun onError(message: String) {
                AdchainLogger.e(TAG, "Mission WebView error: $message")
                // 에러 발생 시에도 유지 (필요시 재사용 가능)
            }
            
            override fun onRewardEarned(amount: Int) {
                AdchainLogger.d(TAG, "Mission reward earned: $amount")
            }
        }
        
        // Set callback for AdchainOfferwallActivity
        AdchainOfferwallActivity.setCallback(missionCallback)
        
        // Create intent with mission parameters
        val intent = Intent(context, AdchainOfferwallActivity::class.java).apply {
            putExtra(AdchainOfferwallActivity.EXTRA_BASE_URL, mission.landingUrl)
            putExtra(AdchainOfferwallActivity.EXTRA_USER_ID, AdchainSdk.getCurrentUser()?.userId)
            putExtra(AdchainOfferwallActivity.EXTRA_APP_ID, AdchainSdk.getConfig()?.appKey)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        
        // Start activity
        context.startActivity(intent)
        
        // Track mission open
        AdchainLogger.d(TAG, "Opening mission WebView for mission: ${mission.id}")
    }
    
    /**
     * Called when a mission is completed - notifies listener
     * iOS와 동일한 방식
     */
    internal fun onMissionCompleted(mission: Mission) {
        AdchainLogger.i(TAG, "Mission completed: ${mission.id}")

        // Notify listener
        eventsListener?.onCompleted(mission)

        // Track completion
        coroutineScope.launch {
            NetworkManager.trackEvent(
                userId = AdchainSdk.getCurrentUser()?.userId ?: "",
                eventName = "mission_completed",
                sdkVersion = BuildConfig.VERSION_NAME,
                category = "mission",
                properties = mapOf(
                    "missionId" to mission.id,
                    "missionTitle" to mission.title
                )
            )
        }

        // Refresh list
        refreshAfterCompletion()
    }

    /**
     * Called when a mission progress is updated - notifies listener
     * iOS와 동일한 방식
     */
    internal fun onMissionProgressed(mission: Mission) {
        AdchainLogger.d(TAG, "Mission progressed: ${mission.id}")

        // Notify listener
        eventsListener?.onProgressed(mission)

        // Track progress (optional, could be too frequent)
        coroutineScope.launch {
            NetworkManager.trackEvent(
                userId = AdchainSdk.getCurrentUser()?.userId ?: "",
                eventName = "mission_progressed",
                sdkVersion = BuildConfig.VERSION_NAME,
                category = "mission",
                properties = mapOf(
                    "missionId" to mission.id,
                    "missionTitle" to mission.title
                )
            )
        }

        // Note: We don't call refreshAfterCompletion here as progress doesn't require full refresh
    }
    
    /**
     * Refresh mission list after completion
     * Called from OfferwallActivity when mission is completed
     */
    internal fun refreshAfterCompletion() {
        AdchainLogger.d(TAG, "Refreshing mission list after completion")
        
        // Store current callbacks if they exist
        val savedOnSuccess = lastOnSuccess
        val savedOnFailure = lastOnFailure
        
        // Re-fetch mission list if callbacks are available
        if (savedOnSuccess != null && savedOnFailure != null) {
            getMissionList(savedOnSuccess, savedOnFailure, shouldStoreCallbacks = false)
        } else {
            AdchainLogger.d(TAG, "No callbacks stored for refresh, skipping UI update")
        }
        
        // instance는 유지 - 다음 사용을 위해
    }
    
    fun destroy() {
        coroutineScope.cancel()
        missions = emptyList()
        missionResponse = null
        missionProgress = null
    }
}