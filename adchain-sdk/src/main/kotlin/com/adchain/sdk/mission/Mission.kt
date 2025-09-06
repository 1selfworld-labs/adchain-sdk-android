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
    @Json(name = "point")
    val point: String,
    @Json(name = "image_url")
    val imageUrl: String,
    @Json(name = "landing_url")
    val landingUrl: String,
    @Json(name = "metadata")
    val metadata: Map<String, Any>? = null,
    @Json(name = "type")
    val type: MissionType? = MissionType.NORMAL
)