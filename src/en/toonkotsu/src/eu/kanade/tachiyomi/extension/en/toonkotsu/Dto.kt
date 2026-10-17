package eu.kanade.tachiyomi.extension.en.toonkotsu

import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient

@Serializable
class BrowseDto(
    val items: List<SeriesDto>,
    val hasNextPage: Boolean,
)

@Serializable
class SeriesDto(
    private val hid: String,
    private val title: String,
    private val cover: String? = null,
    private val type: String? = null,
    private val status: String? = null,
    private val tags: List<String> = emptyList(),
    private val authors: List<String> = emptyList(),
    private val description: String? = null,
    private val alt: List<String> = emptyList(),
) {
    fun toSManga(baseUrl: String) = SManga.create().apply {
        url = "/title/$hid"
        title = this@SeriesDto.title
        thumbnail_url = cover?.let { baseUrl + it }
        author = authors.joinToString().ifEmpty { null }
        genre = (listOfNotNull(type) + tags).joinToString().ifEmpty { null }
        status = when (this@SeriesDto.status) {
            "Ongoing" -> SManga.ONGOING
            "Complete" -> SManga.COMPLETED
            "Hiatus" -> SManga.ON_HIATUS
            "Canceled" -> SManga.CANCELLED
            else -> SManga.UNKNOWN
        }
        description = buildString {
            this@SeriesDto.description?.let { append(it) }
            if (alt.isNotEmpty()) {
                if (isNotEmpty()) append("\n\n")
                append("Alternative titles:\n")
                alt.joinTo(this, "\n") { "- $it" }
            }
        }.ifEmpty { null }
    }
}

@Serializable
class SeriesResponse(
    val series: SeriesDto,
    val chapters: List<ChapterDto>,
    val recs: List<SeriesDto> = emptyList(),
)

@Serializable
class ChapterDto(
    val id: Long,
    val n: Double?,
    private val vol: String? = null,
    private val label: String,
    private val at: Long? = null,
    private val group: GroupDto? = null,
    val partial: Boolean = false,
    private val src: String? = null,
    @Transient val parentId: Long? = null,
    private val alts: List<AltChapterDto> = emptyList(),
) {
    val isOfficial get() = group?.slug == "official"

    val numericVolume get() = vol?.trim()?.toDoubleOrNull()

    val chapterKey get() = n ?: parentId ?: id

    fun row(byVolume: Boolean, byGroup: Boolean = true) = listOf(
        chapterKey,
        (numericVolume ?: vol?.trim()?.lowercase().orEmpty()).takeIf { byVolume },
        (group?.slug ?: group?.name).takeIf { byGroup },
    )

    fun withAlts() = listOf(this) + alts.sortedBy { it.id }.map {
        ChapterDto(it.id, n, vol, it.label ?: label, it.at ?: at, it.group, it.partial, it.src, id)
    }

    fun isFromSource(key: String) = key.equals(src, ignoreCase = true)

    fun isFromGroup(name: String) = name.equals(group?.name, ignoreCase = true)

    fun toSChapter(mangaUrl: String, withSourceCode: Boolean) = SChapter.create().apply {
        url = "$mangaUrl/$id"
        name = label
        // The site's own chapter number; names can count per season or per arc, or have none
        n?.let { chapter_number = it.toFloat() }
        date_upload = at ?: 0L
        // The site has no names for its sources, only codes
        scanlator = group?.name?.let { if (withSourceCode && src != null) "$it ($src)" else it }
    }
}

@Serializable
class AltChapterDto(
    val id: Long,
    val label: String? = null,
    val at: Long? = null,
    val group: GroupDto? = null,
    val partial: Boolean = false,
    val src: String? = null,
)

@Serializable
class GroupDto(val name: String, val slug: String? = null)

@Serializable
class ChapterResponse(val chapter: ChapterPagesDto)

@Serializable
class ChapterPagesDto(val pages: List<PageDto>)

@Serializable
class PageDto(
    val src: String,
    val k: String,
    val n: String,
)

@Serializable
class LimitDto(
    val error: String? = null,
    val code: String? = null,
    val reason: String? = null,
)

@Serializable
class UnlockBody(private val token: String)

@Serializable
class FacetsDto(
    val genres: List<FacetDto>,
    val types: List<FacetDto>,
)

@Serializable
class FacetDto(val name: String)
