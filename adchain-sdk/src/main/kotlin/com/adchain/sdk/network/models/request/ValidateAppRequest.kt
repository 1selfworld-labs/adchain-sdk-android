package com.adchain.sdk.network.models.request

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class ValidateAppRequest(
    @Json(name = "device_info")
    val deviceInfo: DeviceInfo? = null
)

@JsonClass(generateAdapter = true)
data class DeviceInfo(
    @Json(name = "device_id")
    val deviceId: String,
    @Json(name = "device_model")
    val deviceModel: String,
    @Json(name = "device_model_name")
    val deviceModelName: String? = null,
    @Json(name = "os_version")
    val osVersion: String,
    @Json(name = "app_version")
    val appVersion: String? = null,
    @Json(name = "advertising_id")
    val advertisingId: String? = null,
    @Json(name = "is_limit_ad_tracking_enabled")
    val isLimitAdTrackingEnabled: Boolean = false
)