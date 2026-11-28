package eu.kanade.tachiyomi.extension.ja.rawfree

import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.annotation.Source
import keiyoushi.network.Fetch
import keiyoushi.network.get
import keiyoushi.network.post
import keiyoushi.network.rateLimit
import keiyoushi.source.KeiSource
import keiyoushi.source.SMangaUpdate
import keiyoushi.utils.MarkdownDescription
import keiyoushi.utils.asJsoup
import keiyoushi.utils.firstInstanceOrNull
import keiyoushi.utils.parseAs
import keiyoushi.utils.textOrNull
import keiyoushi.utils.toJsonElement
import keiyoushi.utils.tryParseDate
import kotlinx.serialization.json.JsonElement
import okhttp3.FormBody
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.net.ssl.SSLContext

@Source
abstract class Rawfree : KeiSource() {
    override fun OkHttpClient.Builder.configureClient() = apply {
        val trustManager = AiaTrustManager(network.client)
        val sslContext = SSLContext.getInstance("TLS").apply { init(null, arrayOf(trustManager), null) }
        sslSocketFactory(sslContext.socketFactory, trustManager)
        rateLimit(1) { it.encodedPath == AJAX_PATH }
    }

    // Each old domain redirects to the current one
    override suspend fun fetchBaseUrls(client: OkHttpClient): List<String> {
        val url = client.get(baseUrl, Fetch.DOCUMENT).use { it.request.url }
        return if (SITE_HOST_REGEX.matches(url.host)) listOf("${url.scheme}://${url.host}") else emptyList()
    }

    override suspend fun getPopularManga(page: Int): MangasPage {
        val document = client.get(pageUrl(1, "ranking").build(), Fetch.DOCUMENT).asJsoup()
        return MangasPage(document.select(".entry-ma").map(::mangaFromCard), false)
    }

    override suspend fun getLatestUpdates(page: Int): MangasPage = listPage(pageUrl(page, "browse"))

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        if (query.isNotEmpty()) {
            val document = client.get(pageUrl(page).addQueryParameter("s", query).build(), Fetch.DOCUMENT).asJsoup()
            // Past the last page and for no results the site redirects to its home page
            val mangas = document.select("body.search-results .entry-ma").map(::mangaFromCard)
            return MangasPage(mangas, mangas.isNotEmpty())
        }
        val genre = filters.firstInstanceOrNull<GenreFilter>()?.slug
            ?: return getLatestUpdates(page)
        return listPage(pageUrl(page, CATEGORY_PATH, genre))
    }

    private fun pageUrl(page: Int, vararg segments: String) = baseUrl.toHttpUrl().newBuilder().apply {
        segments.forEach { addPathSegment(it) }
        if (page > 1) addPathSegment("page").addPathSegment(page.toString())
        if (segments.isNotEmpty() || page > 1) addPathSegment("")
    }

    private suspend fun listPage(url: HttpUrl.Builder): MangasPage {
        val document = client.get(url.build(), Fetch.DOCUMENT).asJsoup()
        val mangas = document.select(".entry-ma").map(::mangaFromCard)
        return MangasPage(mangas, document.selectFirst(".pagination a.next") != null)
    }

    private fun mangaFromCard(element: Element) = SManga.create().apply {
        val link = element.selectFirst(".ma-name a")!!
        setUrlWithoutDomain(link.absUrl("href"))
        title = link.text().replace(TITLE_SUFFIX, "")
        thumbnail_url = element.selectFirst(".thumb img")?.absUrl("src")
    }

    override suspend fun getMangasByUrl(url: HttpUrl, page: Int): MangasPage {
        val list = url.pathSegments.firstOrNull()?.takeIf { it in LIST_PATHS }
        val slug = url.pathSegments.getOrNull(1)?.takeIf { it.isNotEmpty() }
        if (!url.isSiteHost() || list == null || slug == null) return super.getMangasByUrl(url, page)
        return listPage(pageUrl(page, list, slug))
    }

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (!url.isSiteHost()) return null
        val segments = url.pathSegments.filter { it.isNotEmpty() }
        val slug = when {
            segments.size == 2 && segments[0] == MANGA_PATH -> segments[1]
            segments.size == 1 -> {
                val chapter = client.get(pageUrl(1, segments[0]).build(), Fetch.DOCUMENT).asJsoup()
                chapter.selectFirst(".manga-name a")?.absUrl("href")?.toHttpUrl()?.pathSegments?.getOrNull(1) ?: return null
            }
            else -> return null
        }
        val mangaUrl = pageUrl(1, MANGA_PATH, slug).build()
        val document = client.get(mangaUrl, Fetch.DOCUMENT).asJsoup()
        return parseDetails(document).apply {
            setUrlWithoutDomain(mangaUrl.toString())
            initialized = true
        }
    }

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val document = client.get(getMangaUrl(manga), Fetch.DOCUMENT).asJsoup()
        return SMangaUpdate(parseDetails(document), parseChapters(document))
    }

    private fun parseDetails(document: Document) = SManga.create().apply {
        val info = document.selectFirst(".ma-box")!!
        title = info.selectFirst("h1.name")!!.text().replace(TITLE_SUFFIX, "")
        thumbnail_url = info.selectFirst(".main-thumb img")?.absUrl("src")
        genre = info.select(".genres-wrap a").joinToString { it.text() }.ifEmpty { null }
        status = when (info.selectFirst("h1.name + span")?.text()) {
            "OnGoing" -> SManga.ONGOING
            else -> SManga.UNKNOWN
        }
        description = MarkdownDescription.Builder()
            .info(info.selectFirst("div:has(> h1.name) + p")?.textOrNull())
            .description(info.selectFirst(".note-box"))
            .build()
    }

    private fun parseChapters(document: Document) = document.select(".entry-chapter").map { element ->
        SChapter.create().apply {
            val link = element.selectFirst("a")!!
            setUrlWithoutDomain(link.absUrl("href"))
            name = link.text()
            date_upload = DATE_FORMAT.tryParseDate(element.selectFirst(".date")?.text(), ZONE)
        }
    }

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val chapterUrl = getChapterUrl(chapter)
        val document = client.get(chapterUrl, Fetch.DOCUMENT).asJsoup()
        val reader = document.selectFirst("script:containsData(decode_images)")!!.data()
        val postId = POST_ID_REGEX.find(reader)!!.groupValues[1]
        val nonce = NONCE_REGEX.find(document.selectFirst("script:containsData(const zing =)")!!.data())!!.groupValues[1]
        val ajaxUrl = baseUrl.toHttpUrl().newBuilder().encodedPath(AJAX_PATH).build()
        val fetch = Fetch.XHR.from(chapterUrl)

        var imgIndex = 0
        val images = buildList {
            do {
                val body = FormBody.Builder()
                    .add("action", "z_do_ajax")
                    .add("_action", "decode_images")
                    .add("p", postId)
                    .add("img_index", imgIndex.toString())
                    .add("content", "")
                    .add("nonce", nonce)
                    .build()
                val result = client.post(ajaxUrl, fetch, body).parseAs<DecodeImagesDto>()
                val batch = Jsoup.parseBodyFragment(result.html, baseUrl).select("img").map { it.absUrl("src") }
                addAll(batch)
                val advanced = result.imgIndex > imgIndex
                imgIndex = result.imgIndex
            } while (result.going == 1 && batch.isNotEmpty() && advanced)
        }

        return images.mapIndexed { index, url -> Page(index, imageUrl = url) }
    }

    override val supportsFilterFetching = true

    override suspend fun fetchFilterData(): JsonElement {
        val document = client.get(pageUrl(1, "browse").build(), Fetch.DOCUMENT).asJsoup()
        return document.select(".hero-genres a").map { link ->
            link.text() to link.absUrl("href").toHttpUrl().pathSegments[1]
        }.toJsonElement()
    }

    override fun getFilterList(data: JsonElement?): FilterList {
        val genres = data?.parseAs<List<Pair<String, String>>>().orEmpty()
        if (genres.isEmpty()) return FilterList()
        return FilterList(
            Filter.Header("キーワード検索ではジャンルは無視されます"),
            GenreFilter(genres),
        )
    }

    // Links on the site's earlier domains too
    private fun HttpUrl.isSiteHost() = host == baseUrl.toHttpUrl().host || SITE_HOST_REGEX.matches(host)

    companion object {
        private val SITE_HOST_REGEX = Regex("""rawfree\.[a-z]+""")
        private const val MANGA_PATH = "manga-raw"
        private const val CATEGORY_PATH = "category"
        private val LIST_PATHS = setOf(CATEGORY_PATH, "manga-tag")
        private const val AJAX_PATH = "/wp-admin/admin-ajax.php"
        private val POST_ID_REGEX = Regex("""\bp:\s*(\d+)""")
        private val NONCE_REGEX = Regex(""""nonce":"(\w+)"""")
        private val DATE_FORMAT = DateTimeFormatter.ofPattern("M月 d, yyyy", Locale.ROOT)
        private val ZONE = ZoneId.of("Asia/Tokyo")

        // Lists write the suffix with an en dash, some series pages with a hyphen
        private val TITLE_SUFFIX = Regex("""\s*\(Raw [-\u2013] Free\)$""")
    }
}
