package eu.kanade.tachiyomi.extension.ja.rawfree

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
class DecodeImagesDto(
    @SerialName("mes") val html: String,
    val going: Int,
    @SerialName("img_index") val imgIndex: Int,
)
