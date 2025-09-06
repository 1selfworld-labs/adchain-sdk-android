package com.adchain.sdk.mission

data class MissionProgress(
    val current: Int,
    val total: Int
) {
    val percentage: Float
        get() = if (total > 0) (current.toFloat() / total.toFloat()) * 100f else 0f
}