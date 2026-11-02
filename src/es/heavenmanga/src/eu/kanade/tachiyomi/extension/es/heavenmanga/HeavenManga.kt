package eu.kanade.tachiyomi.extension.es.heavenmanga

import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import keiyoushi.annotation.Source
import keiyoushi.network.Fetch
import keiyoushi.network.get
import keiyoushi.source.KeiSource
import keiyoushi.utils.asJsoup
import keiyoushi.utils.attrOrNull
import keiyoushi.utils.parseAs
import keiyoushi.utils.textOrNull
import keiyoushi.utils.toJsonElement
import keiyoushi.utils.tryParseDateTime
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.json.JsonElement
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

@Source
abstract class HeavenManga : KeiSource() {

    override suspend fun getPopularManga(page: Int): MangasPage {
        val url = baseUrl.toHttpUrl().newBuilder()
            .addPathSegment("top")
            .addQueryParameter("orderby", "views")
            .addQueryParameter("pages", page.toString())
            .build()
        val document = client.get(url, Fetch.DOCUMENT).asJsoup()
        return parseMangaGrid(document)
    }

    private fun parseMangaGrid(document: Document): MangasPage {
        val mangas = document.select("div.page-item-detail:not(:has(span.book-type:containsOwn(Novela)))").map { element ->
            SManga.create().apply {
                title = element.selectFirst("div.manga-name")!!.text()
                setUrlWithoutDomain(element.selectFirst("a")!!.absUrl("href"))
                thumbnail_url = element.selectFirst("img")?.attrOrNull("abs:src")
            }
        }
        val hasNextPage = document.selectFirst("ul.pagination a[rel=next]") != null
        return MangasPage(mangas, hasNextPage)
    }

    override suspend fun getLatestUpdates(page: Int): MangasPage {
        val url = baseUrl.toHttpUrl().newBuilder()
            .apply { if (page > 1) addQueryParameter("pages", page.toString()) }
            .build()
        val document = client.get(url, Fetch.DOCUMENT).asJsoup()

        val mangas = document.selectFirst("div.col-lg-8 > div#loop-content")
            ?.select("div.list-group-item:not(:has(div:containsOwn(Novela)))")
            ?.map { element ->
                val link = element.selectFirst("a")!!
                SManga.create().apply {
                    this.url = "/manga/" + link.absUrl("href").toHttpUrl().pathSegments[1]
                    title = link.selectFirst(".captitle")?.text() ?: link.text()
                }
            }
            ?.distinctBy { it.url }
            ?: emptyList()

        val hasNextPage = document.selectFirst("ul.pagination a[rel=next]") != null

        return MangasPage(mangas, hasNextPage)
    }

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        if (query.isBlank() && filters.none { it is UriPartFilter && it.state != 0 }) return getPopularManga(page)

        val url = baseUrl.toHttpUrl().newBuilder()
        if (query.isNotBlank()) {
            if (query.length < 3) throw Exception("Search needs at least 3 characters")
            url.addPathSegment("buscar")
                .addQueryParameter("query", query)
        } else {
            filters.forEach { filter ->
                when (filter) {
                    is GenreFilter -> if (filter.state != 0) {
                        url.addPathSegment("genero").addPathSegment(filter.toUriPart() + ".html")
                    }

                    is AlphabeticoFilter -> if (filter.state != 0) {
                        url.addPathSegments("letra/manga.html").addQueryParameter("alpha", filter.toUriPart())
                    }

                    is ListaCompletasFilter -> if (filter.state != 0) {
                        url.addPathSegment(filter.toUriPart())
                    }

                    else -> {}
                }
            }
        }

        if (page > 1) url.addQueryParameter("pages", page.toString())

        val document = client.get(url.build(), Fetch.DOCUMENT).asJsoup()

        if (query.isBlank()) {
            return parseMangaGrid(document)
        }

        // Result cards sit inside an outer div with the same class
        val mangas = document.select("div.c-tabs-item__content:has(> div > div.tab-thumb)").map { element ->
            SManga.create().apply {
                element.selectFirst("h4 a")!!.let {
                    title = it.text()
                    setUrlWithoutDomain(it.absUrl("href"))
                }
                thumbnail_url = element.selectFirst("img")?.attrOrNull("abs:data-src")
            }
        }
        val hasNextPage = document.selectFirst("ul.pagination a[rel=next]") != null
        return MangasPage(mangas, hasNextPage)
    }

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host != baseUrl.toHttpUrl().host) return null
        val segments = url.pathSegments.filter { it.isNotEmpty() }
        if (segments.firstOrNull() != "manga") return null
        val slug = when (segments.getOrNull(1)) {
            null, "leer" -> return null
            // Feed links are /manga/ver/<slug>/<chapter>, reader links /manga/ver/<chapter id>
            "ver" -> when {
                segments.size < 3 -> return null

                segments.size == 3 -> client.get(url, Fetch.DOCUMENT).asJsoup()
                    .selectFirst("a.endReadingButtons")?.absUrl("href")?.toHttpUrl()?.pathSegments?.getOrNull(1)
                    ?: return null

                else -> segments[2]
            }
            else -> segments[1]
        }
        val manga = SManga.create().apply { this.url = "/manga/$slug" }
        return getMangaUpdate(manga, emptyList(), fetchDetails = true, fetchChapters = false).manga
    }

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate = coroutineScope {
        val details = if (fetchDetails) async { fetchDetails(manga) } else null
        val chapterList = if (fetchChapters) async { fetchChapterList(manga) } else null

        SMangaUpdate(details?.await() ?: manga, chapterList?.await() ?: chapters)
    }

    private suspend fun fetchDetails(manga: SManga): SManga {
        val document = client.get(getMangaUrl(manga), Fetch.DOCUMENT).asJsoup()
        return manga.apply {
            title = document.selectFirst("div.post-title .manga-title")!!.text()
            genre = (listOfNotNull(document.summaryField("Type")) + document.select("div.genres-content a").eachText())
                .joinToString().ifEmpty { null }
            thumbnail_url = document.selectFirst("div.summary_image img")?.attrOrNull("abs:data-src")
            val synopsis = document.selectFirst("div.description-summary p")?.wholeText()
                ?.lines()?.map { it.trim() }?.filter { it.isNotEmpty() }?.joinToString("\n\n")?.ifEmpty { null }
            val altTitles = document.summaryField("Titulo Alt")?.let { "**Títulos alternativos:** $it" }
            description = listOfNotNull(synopsis, altTitles).joinToString("\n\n").ifEmpty { null }
        }
    }

    private suspend fun fetchChapterList(manga: SManga): List<SChapter> {
        val mangaUrl = getMangaUrl(manga).toHttpUrl()
        val url = mangaUrl.newBuilder()
            .addQueryParameter("columns[0][data]", "number")
            .addQueryParameter("columns[0][orderable]", "true")
            .addQueryParameter("columns[1][data]", "created_at")
            .addQueryParameter("columns[1][searchable]", "true")
            .addQueryParameter("order[0][column]", "1")
            .addQueryParameter("order[0][dir]", "desc")
            .addQueryParameter("start", "0")
            .addQueryParameter("length", CHAPTER_LIST_LIMIT.toString())
            .build()

        val headers = headers.newBuilder()
            .add("X-Requested-With", "XMLHttpRequest")
            .build()

        val result = client.get(url, headers, Fetch.XHR.from(mangaUrl)).parseAs<PayloadChaptersDto>()
        val chapterBaseUrl = mangaUrl.toString().removeSuffix("/")

        return result.data
            .map {
                val row = Jsoup.parseBodyFragment(it.numberHtml)
                SChapter.create().apply {
                    name = row.selectFirst("h3")!!.text()
                    scanlator = row.selectFirst("span.greenSpan")?.textOrNull()
                    setUrlWithoutDomain("$chapterBaseUrl/${it.slug}#${it.id}")
                    date_upload = dateFormat.tryParseDateTime(it.createdAt, ZoneOffset.UTC)
                }
            }
            .sortedByDescending { CHAPTER_NUMBER_REGEX.find(it.name)?.value?.toFloatOrNull() ?: -1f }
    }

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val chapterUrl = getChapterUrl(chapter)
        val chapterId = chapterUrl.toHttpUrl().fragment
            ?: throw Exception("Chapter id missing, refresh the chapter list")

        val document = client.get("$baseUrl/manga/ver/$chapterId", Fetch.DOCUMENT.from(chapterUrl)).asJsoup()
        val jsonString = document.selectFirst("script:containsData(pUrl)")?.data()
            ?.let { PAGES_REGEX.find(it)?.groupValues?.get(1) }
            ?.removeTrailingComma()
            ?: throw Exception("Page list not found")

        val pages = jsonString.parseAs<List<PageDto>>()
        return pages.mapIndexed { i, dto -> Page(i, imageUrl = dto.imgURL) }
    }

    override val supportsFilterFetching = true

    override suspend fun fetchFilterData(): JsonElement = client.get("$baseUrl/top", Fetch.DOCUMENT).asJsoup()
        .select("div.widget-content a[href*=/genero/]")
        .map { GenreDto(it.text(), it.absUrl("href").toHttpUrl().pathSegments.last().removeSuffix(".html")) }
        .filter { it.slug != "novela" }
        .toJsonElement()

    override fun getFilterList(data: JsonElement?) = FilterList(
        listOfNotNull(
            Filter.Header("NOTA: Los filtros se ignoran si se utiliza la búsqueda de texto."),
            Filter.Header("Sólo se puede utilizar un filtro a la vez."),
            Filter.Separator(),
            data?.parseAs<List<GenreDto>>()?.let(::GenreFilter),
            AlphabeticoFilter(),
            ListaCompletasFilter(),
        ),
    )

    private fun Document.summaryField(heading: String): String? = selectFirst("div.post-content_item:has(div.summary-heading h5:containsOwn($heading)) div.summary-content")
        ?.textOrNull()
        ?.takeUnless { it == "Desconocido" }

    private fun String.removeTrailingComma() = replace(TRAILING_COMMA_REGEX, "$1")

    companion object {
        private val dateFormat = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
        private val PAGES_REGEX = """pUrl\s*=\s*(\[[\s\S]*?\])\s*;""".toRegex()
        private val TRAILING_COMMA_REGEX = """,\s*(\}|\])""".toRegex()
        private val CHAPTER_NUMBER_REGEX = """\d+(\.\d+)?""".toRegex()
        private const val CHAPTER_LIST_LIMIT = 10000
    }
}
