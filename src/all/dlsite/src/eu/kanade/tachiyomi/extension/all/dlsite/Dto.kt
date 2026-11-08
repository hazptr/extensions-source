package eu.kanade.tachiyomi.extension.all.dlsite

import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.UpdateStrategy
import keiyoushi.utils.MarkdownDescription
import keiyoushi.utils.parseAs
import keiyoushi.utils.tryParseDateTime
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.jsoup.nodes.Element
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.time.Instant

@Serializable
class SearchDto(
    @SerialName("search_result") val searchResult: String,
    @SerialName("page_info") val pageInfo: PageInfoDto,
)

@Serializable
class PageInfoDto(
    val count: Int,
    @SerialName("last_indice") val lastIndice: Int,
)

@Serializable
class ProductDto(
    val workno: String,
    @SerialName("site_id") val siteId: String,
    @SerialName("work_type") val workType: String,
    @SerialName("work_name") private val workName: String,
    @SerialName("work_type_string") private val workTypeString: String? = null,
    @SerialName("maker_name") private val makerName: String? = null,
    @SerialName("series_name") private val seriesName: String? = null,
    @SerialName("label_name") private val labelName: String? = null,
    @SerialName("page_number") private val pageNumber: String? = null,
    @SerialName("regist_date") private val registDate: String? = null,
    @SerialName("intro_s") private val intro: String? = null,
    @SerialName("rate_count_detail") private val rateCountDetail: JsonElement? = null,
    @SerialName("image_main") private val imageMain: FileDto? = null,
    // An object of credits by role, or an empty array when the work has none
    private val creaters: JsonElement? = null,
    // Credits of the books (BJ) works
    private val author: List<AuthorDto>? = null,
    private val genres: List<NameDto> = emptyList(),
    @SerialName("book_type") private val bookType: NameDto? = null,
    private val options: String? = null,
    // An object of options by value, or an empty array
    @SerialName("work_options") private val workOptions: JsonElement? = null,
    // An array of editions, or an object of them by index
    @SerialName("language_editions") private val languageEditions: JsonElement? = null,
    // An array of files, or false when the work has none
    private val trials: JsonElement? = null,
    @SerialName("image_samples") private val imageSamples: List<FileDto>? = null,
    @SerialName("sample_type") private val sampleType: String? = null,
    @SerialName("is_viewable_sample") private val isViewableSample: Boolean = false,
) {
    val date get() = DATE_FORMAT.tryParseDateTime(registDate)

    val trialFiles get() = (trials as? JsonArray)?.parseAs<List<TrialFileDto>>().orEmpty()

    fun toSManga(baseUrl: String, workUrl: (String) -> String) = SManga.create().apply {
        val credits = (creaters as? JsonObject)?.parseAs<CreditsDto>() ?: CreditsDto()
        url = workno
        title = workName
        thumbnail_url = imageMain?.url?.let { baseUrl.toHttpUrl().resolve(it) }?.toString()
        author = (credits.createdBy.map { it.name } + credits.scenarioBy.map { it.name } + this@ProductDto.author.orEmpty().map { it.name })
            .distinct()
            .joinToString()
            .ifEmpty { makerName }
        artist = credits.illustBy.joinToString { it.name }.ifEmpty { null }
        genre = buildList {
            workTypeString?.let(::add)
            bookType?.name?.let(::add)
            addAll(genres.map { it.name })
            if (options.orEmpty().split('#').any { it in DLsite.AI_OPTIONS }) add("AI Generated")
        }.joinToString().ifEmpty { null }
        description = description(null, workUrl)
        status = SManga.COMPLETED
        update_strategy = UpdateStrategy.ONLY_FETCH_ONCE
        memo = buildJsonObject { put("site", siteId) }
    }

    fun description(page: Element?, workUrl: (String) -> String): String? {
        val counts = (rateCountDetail as? JsonObject)?.parseAs<Map<Int, Int>>().orEmpty()
        val votes = counts.values.sum()
        val rating = if (votes > 0) counts.entries.sumOf { it.key * it.value } / votes.toDouble() else null
        val event = (workOptions as? JsonObject)?.parseAs<Map<String, WorkOptionDto>>()?.values
            ?.firstOrNull { it.category == "event" }
            ?.let { it.nameEn?.ifEmpty { null } ?: it.name }
        val editions = when (languageEditions) {
            is JsonArray -> languageEditions.parseAs<List<EditionDto>>()
            is JsonObject -> languageEditions.parseAs<Map<String, EditionDto>>().values
            else -> emptyList()
        }.filter { it.workno != workno }.map { it.label to workUrl(it.workno) }
        return MarkdownDescription.Builder()
            .rating(rating, max = 5, votes = votes)
            .apply { if (page != null) description(page) else description(intro) }
            // Doujin works (RJ) have a circle, books (BJ) a publisher
            .field(if (workno.startsWith("BJ")) "Publisher" else "Circle", makerName)
            .field("Label", labelName)
            .field("Series", seriesName)
            .field("Event", event)
            .field("Pages", pageNumber)
            .links(editions, label = "Editions")
            .build()
    }

    // Books show their sample in the site's book viewer, other works the sample images
    fun toSampleChapter(baseUrl: String): SChapter? {
        val readable = sampleType == "epub" || (sampleType == "images" && !imageSamples.isNullOrEmpty())
        if (!isViewableSample || !readable) return null
        return SChapter.create().apply {
            url = "/sample/$workno"
            name = "Sample"
            date_upload = date
            memo = buildJsonObject {
                put("sample", sampleType)
                put("workno", workno)
                if (sampleType == "images") putJsonArray("images") { imageSamples!!.forEach { add(baseUrl.toHttpUrl().resolve(it.url!!).toString()) } }
            }
        }
    }
}

@Serializable
class FileDto(val url: String? = null)

@Serializable
class TrialFileDto(
    @SerialName("relative_url") val relativeUrl: String,
    private val url: String,
) {
    val zipUrl get() = "https:$url"
}

@Serializable
class CreditsDto(
    @SerialName("created_by") val createdBy: List<NameDto> = emptyList(),
    @SerialName("scenario_by") val scenarioBy: List<NameDto> = emptyList(),
    @SerialName("illust_by") val illustBy: List<NameDto> = emptyList(),
)

@Serializable
class NameDto(val name: String)

@Serializable
class WorkOptionDto(
    val category: String? = null,
    val name: String? = null,
    @SerialName("name_en") val nameEn: String? = null,
)

@Serializable
class AuthorDto(@SerialName("author_name") val name: String)

@Serializable
class EditionDto(val workno: String, val label: String)

@Serializable
class GenreOption(val name: String, val id: String)

@Serializable
class ViewerTokenDto(
    private val prefix: String,
    private val expireAt: String,
    private val parameters: Map<String, String>,
) {
    val expiresAt get() = Instant.parse(expireAt)

    fun url(file: String): HttpUrl = prefix.toHttpUrl().newBuilder()
        .addPathSegment(file)
        .apply { parameters.forEach { addEncodedQueryParameter(it.key, it.value) } }
        .build()
}

@Serializable
class ViewerMetaDto(val pages: List<ViewerPageDto>)

@Serializable
class ViewerPageDto(val type: String, val src: String)

@Serializable
class TrialEntry(val name: String, val offset: Long, val compressedSize: Long, val method: Int)

private val DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss", Locale.ROOT).withZone(ZoneId.of("Asia/Tokyo"))
