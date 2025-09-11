package com.adchain.sdk.mission

/**
 * Mission completion status
 */
data class MissionStatus(
    val current: Int,      // Number of completed missions
    val total: Int,        // Total number of missions
    val isCompleted: Boolean = false,  // Whether all missions are completed
    val canClaimReward: Boolean = false  // Whether reward can be claimed
) {
    companion object {
        fun from(progress: MissionProgress): MissionStatus {
            return MissionStatus(
                current = progress.current,
                total = progress.total,
                isCompleted = progress.current >= progress.total && progress.total > 0,
                canClaimReward = progress.current >= progress.total && progress.total > 0
            )
        }
    }
}