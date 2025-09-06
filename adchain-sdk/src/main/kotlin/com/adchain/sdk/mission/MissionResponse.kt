package com.adchain.sdk.mission

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class MissionResponse(
    @Json(name = "success")
    val success: Boolean? = null,
    @Json(name = "events")
    val events: List<Mission> = emptyList(),
    @Json(name = "current")
    val current: Int = 0,
    @Json(name = "total")
    val total: Int = 0,
    @Json(name = "reward_url")
    val rewardUrl: String? = null,
    @Json(name = "message")
    val message: String? = null
)