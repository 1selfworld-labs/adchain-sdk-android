package com.adchain.sdk.mission

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

enum class MissionType(val value: String) {
    NORMAL("normal"),
    OFFERWALL_PROMOTION("offerwall_promotion")
}

@JsonClass(generateAdapter = true)
data class Mission(
    @Json(name = "id")
    val id: String,
    @Json(name = "title")
    val title: String,
    @Json(name = "description")
    val description: String,
    @Json(name = "imageUrl")  // snake_case에서 camelCase로 변경
    val imageUrl: String,
    @Json(name = "landingUrl")  // snake_case에서 camelCase로 변경
    val landingUrl: String,
    @Json(name = "point")
    val point: String,
    @Json(name = "status")
    val status: String? = null,  // iOS와 동일하게 추가
    @Json(name = "progress")
    val progress: Int? = null,  // iOS와 동일하게 추가
    @Json(name = "total")
    val total: Int? = null,  // iOS와 동일하게 추가
    @Json(name = "type")
    val type: MissionType? = MissionType.NORMAL
)