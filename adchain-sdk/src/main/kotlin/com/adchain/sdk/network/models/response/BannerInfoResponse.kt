package com.adchain.sdk.network.models.response

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class BannerInfoResponse(
    @Json(name = "success")
    val success: Boolean,

    @Json(name = "imageUrl")
    val imageUrl: String? = null,

    @Json(name = "imageWidth")
    val imageWidth: Int? = null,

    @Json(name = "imageHeight")
    val imageHeight: Int? = null,

    @Json(name = "titleText")
    val titleText: String? = null,

    @Json(name = "linkType")
    val linkType: String? = null, // "external" or "internal"

    @Json(name = "internalLinkUrl")
    val internalLinkUrl: String? = null,

    @Json(name = "externalLinkUrl")
    val externalLinkUrl: String? = null
)