package com.adchain.sdk.network.models.response

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class ValidateAppResponse(
    @Json(name = "success")
    val success: Boolean,
    @Json(name = "app")
    val app: AppData? = null,
    @Json(name = "message")
    val message: String? = null
)

@JsonClass(generateAdapter = true)
data class AppData(
    @Json(name = "appKey")
    val appKey: String,
    @Json(name = "appName")
    val appName: String,
    @Json(name = "isActive")
    val isActive: Boolean? = null,
    @Json(name = "adchainHubUrl")
    val adchainHubUrl: String,
    @Json(name = "config")
    val config: Map<String, Any>? = null,
    
    // 하위 호환성을 위한 deprecated 필드들
    @Deprecated("Use appKey instead", ReplaceWith("appKey"))
    val id: String? = null,
    @Deprecated("Use appName instead", ReplaceWith("appName"))
    val name: String? = null,
    @Deprecated("Use adchainHubUrl instead", ReplaceWith("adchainHubUrl"))
    val webOfferwallUrl: String? = null
)