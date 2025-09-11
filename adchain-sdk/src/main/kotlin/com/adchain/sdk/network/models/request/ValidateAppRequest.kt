package com.adchain.sdk.network.models.request

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
class ValidateAppRequest

@JsonClass(generateAdapter = true)
data class DeviceInfo(
    @Json(name = "deviceId")
    val deviceId: String,
    @Json(name = "deviceModel")
    val deviceModel: String,
    @Json(name = "deviceModelName")
    val deviceModelName: String? = null,
    @Json(name = "manufacturer")
    val manufacturer: String,
    @Json(name = "platform")
    val platform: String,
    @Json(name = "osVersion")
    val osVersion: String,
    @Json(name = "country")
    val country: String? = null,
    @Json(name = "language")
    val language: String? = null,
    @Json(name = "installer")
    val installer: String? = null,
    @Json(name = "ifa")
    val ifa: String? = null
)