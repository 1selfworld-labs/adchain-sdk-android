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
    @Json(name = "rewardUrl")  // snake_case에서 camelCase로 변경
    val rewardUrl: String? = null,
    @Json(name = "message")
    val message: String? = null,

    // 신규 추가 필드
    @Json(name = "titleText")
    val titleText: String? = null,
    @Json(name = "descriptionText")
    val descriptionText: String? = null,
    @Json(name = "bottomText")
    val bottomText: String? = null,
    @Json(name = "rewardIconUrl")
    val rewardIconUrl: String? = null,
    @Json(name = "bottomIconUrl")
    val bottomIconUrl: String? = null
)