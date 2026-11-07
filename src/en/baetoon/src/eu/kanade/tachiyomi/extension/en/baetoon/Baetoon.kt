package eu.kanade.tachiyomi.extension.en.baetoon

import androidx.preference.PreferenceScreen
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import keiyoushi.annotation.Source
import keiyoushi.network.Fetch
import keiyoushi.network.get
import keiyoushi.source.ChapterUploadPreferences
import keiyoushi.source.KeiSource
import keiyoushi.utils.extractNextJs
import keiyoushi.utils.firstInstanceOrNull
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.parseAs
import keiyoushi.utils.toJsonElement
import kotlinx.serialization.json.JsonElement
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl

@Source
abstract class Baetoon :
    KeiSource(),
    ConfigurableSource {

    private val preferences by getPreferencesLazy()

    private val chapterUploads by lazy { ChapterUploadPreferences(preferences, chapterUploadStrings, onePerGroup = true) }

    private val rscHeaders get() = headersBuilder().set("rsc", "1").build()

    override val supportsFilterFetching = true

    override val supportsRelatedMangas = true

    override suspend fun getPopularManga(page: Int) = searchTitles(page, "most-view")

    override suspend fun getLatestUpdates(page: Int) = searchTitles(page, "lastest-updates")

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList) = searchTitles(
        page,
        filters.firstInstanceOrNull<SortFilter>()?.value ?: "most-view",
    ) {
        if (query.isNotBlank()) addQueryParameter("text", query.trim())
        filters.firstInstanceOrNull<AuthorFilter>()?.state?.takeIf { it.isNotBlank() }?.let { addQueryParameter("author", it.trim()) }
        filters.firstInstanceOrNull<KeywordFilter>()?.state?.takeIf { it.isNotBlank() }?.let { addQueryParameter("keywords", it.trim()) }
        filters.firstInstanceOrNull<GenreFilter>()?.value?.takeIf { it.isNotEmpty() }?.let { addQueryParameter("tag", it) }
    }

    private suspend fun searchTitles(page: Int, sort: String, params: HttpUrl.Builder.() -> Unit = {}): MangasPage {
        val url = "$baseUrl/api/titles/search".toHttpUrl().newBuilder().apply {
            addQueryParameter("sort", sort)
            params()
            addQueryParameter("page", page.toString())
            addQueryParameter("limit", "24")
        }.build()
        val result = client.get(url, Fetch.XHR.from("/advanced-search")).parseAs<SearchDto>()
        return MangasPage(result.data.map { it.toSManga() }, result.pagination.page < result.pagination.totalPages)
    }

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host != baseUrl.toHttpUrl().host) return null
        val id = url.pathSegments.getOrNull(1) ?: return null
        return when (url.pathSegments[0]) {
            "title-detail" -> fetchDetail(getMangaUrl(SManga.create().apply { this.url = id })).toSManga().apply { initialized = true }
            "chapter-detail" -> fetchReader(getChapterUrl(SChapter.create().apply { this.url = id })).toSManga()
            else -> null
        }
    }

    override fun getMangaUrl(manga: SManga) = "$baseUrl/title-detail/${manga.url}"

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val detail = fetchDetail(getMangaUrl(manga))
        return SMangaUpdate(detail.toSManga(), chapterList(detail.chapters))
    }

    override suspend fun fetchRelatedMangaList(manga: SManga) = fetchDetail(getMangaUrl(manga)).recommendations.map { it.toSManga() }

    private suspend fun fetchDetail(url: String): DetailDto = fetchRsc<DetailDto>(url)
        ?: throw Exception("Title not found")

    private fun chapterList(chapters: List<ChapterDto>): List<SChapter> {
        val uploads = chapterUploads.select(
            chapters.flatMap { chapter -> chapter.sources.map { chapter to it } },
            chapter = { it.first },
            groups = { listOf(it.second.sourceName) },
        )
        // The site lists a source's repeat uploads of a chapter under the same name
        val copies = HashMap<Pair<ChapterDto, String>, Int>()
        return uploads.map { (chapter, upload) ->
            val copy = copies.merge(chapter to upload.sourceName, 1, Int::plus)!!
            upload.toSChapter(chapter, if (copy > 1) "${upload.sourceName} ($copy)" else upload.sourceName)
        }
    }

    override fun getChapterUrl(chapter: SChapter) = "$baseUrl/chapter-detail/${chapter.url}"

    override suspend fun getPageList(chapter: SChapter): List<Page> = fetchReader(getChapterUrl(chapter)).pages.mapIndexed { index, url ->
        Page(index, imageUrl = url)
    }

    private suspend fun fetchReader(url: String): ReaderDto = fetchRsc<ReaderDto>(url)
        ?: throw Exception("Chapter not found")

    override suspend fun fetchFilterData(): JsonElement = fetchRsc<GenresDto>("$baseUrl/advanced-search")!!.initialGenres.toJsonElement()

    override fun getFilterList(data: JsonElement?) = FilterList(
        listOfNotNull(
            SortFilter(),
            AuthorFilter(),
            KeywordFilter(),
            data?.parseAs<List<String>>()?.let(::GenreFilter),
        ),
    )

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        chapterUploads.setupPreferenceScreen(screen)
    }

    // Without the bare `_rsc` param the site answers an RSC fetch with a 307 to it
    private suspend inline fun <reified T> fetchRsc(url: String): T? = client.get(
        url.toHttpUrl().newBuilder().addQueryParameter("_rsc", null).build(),
        rscHeaders,
        Fetch.XHR,
    ).extractNextJs<T>()
}
