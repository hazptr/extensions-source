package eu.kanade.tachiyomi.extension.es.heavenmanga

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
class PayloadChaptersDto(
    val data: List<ChapterDto>,
)

@Serializable
class ChapterDto(
    val id: Int,
    val slug: String,
    @SerialName("number") val numberHtml: String,
    @SerialName("created_at") val createdAt: String? = null,
)

@Serializable
class PageDto(
    val imgURL: String,
)

@Serializable
class GenreDto(
    val name: String,
    val slug: String,
)
