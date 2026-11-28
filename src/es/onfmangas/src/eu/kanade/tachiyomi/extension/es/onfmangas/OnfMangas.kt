package eu.kanade.tachiyomi.extension.es.onfmangas

import android.content.SharedPreferences
import androidx.preference.PreferenceScreen
import androidx.preference.SwitchPreferenceCompat
import app.cash.quickjs.QuickJs
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.annotation.Source
import keiyoushi.network.Fetch
import keiyoushi.network.fetch
import keiyoushi.network.get
import keiyoushi.network.post
import keiyoushi.source.KeiSource
import keiyoushi.source.SMangaUpdate
import keiyoushi.utils.JSON_MEDIA_TYPE
import keiyoushi.utils.MarkdownDescription
import keiyoushi.utils.asJsoup
import keiyoushi.utils.attrOrNull
import keiyoushi.utils.firstInstanceOrNull
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.parseAs
import keiyoushi.utils.selectLast
import keiyoushi.utils.textOrNull
import keiyoushi.utils.toJsonElement
import kotlinx.serialization.json.JsonElement
import okhttp3.Cookie
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.jsoup.nodes.Document
import java.io.IOException
import kotlin.time.Clock

@Source
abstract class OnfMangas :
    KeiSource(),
    ConfigurableSource {

    private val preferences: SharedPreferences by getPreferencesLazy()

    override fun OkHttpClient.Builder.configureClient(): OkHttpClient.Builder = addInterceptor(::onfTokenInterceptor)
        .addInterceptor(::pageRetryInterceptor)

    // The site's reader tries each of a page's urls in turn, then the last one again with a fresh `retry` parameter
    private fun pageRetryInterceptor(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val page = request.tag(PageUrls::class.java) ?: return chain.proceed(request)

        val urls = listOf(request.url) + page.alternatives
        for (url in urls) {
            val response = try {
                chain.proceed(pageImageRequest(url, page.chapterUrl))
            } catch (e: IOException) {
                if (chain.call().isCanceled()) throw e
                continue
            }
            if (response.isSuccessful) return response
            response.close()
        }

        val retryUrl = urls.last().newBuilder()
            .setQueryParameter("retry", Clock.System.now().toEpochMilliseconds().toString())
            .build()
        return chain.proceed(pageImageRequest(retryUrl, page.chapterUrl))
    }

    private fun onfTokenInterceptor(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val response = chain.proceed(request)

        if (response.body.contentType()?.subtype != "html") return response
        if ("Verificando" !in response.peekBody(8192).string()) return response

        val cookieString = solveOnfCheck(response) ?: throw IOException(CHECK_FAILED)
        val cookie = Cookie.parse(request.url, cookieString)
        client.cookieJar.saveFromResponse(request.url, listOfNotNull(cookie))

        return chain.proceed(request)
    }

    private fun solveOnfCheck(response: Response): String? {
        val document = response.asJsoup()
        val script = document.selectFirst("script")?.data() ?: throw IOException(CHECK_FAILED)

        return QuickJs.create().use { js ->
            js.evaluate(
                """
            var window = { location: {} };
            var document = { cookie: null };
            var location = window.location;
            var setTimeout = function(fn, _) { fn(); };

            $script

            document.cookie;
                """.trimIndent(),
            )?.toString()
        }
    }

    override val supportsFilterFetching = true

    private val authorPlaceholders = setOf("Autor Desconocido", "Desconocido", "Sin dato")

    override suspend fun getPopularManga(page: Int): MangasPage {
        val document = client.get("$baseUrl/populares.php", Fetch.DOCUMENT).asJsoup()
        // The podium lists #2 before #1
        val entries = document.select("a.pop-podium-card.gold") + document.select("a.pop-podium-card:not(.gold), a.pop-card")
        val mangas = entries.map { element ->
            SManga.create().apply {
                title = element.selectFirst(".pop-podium-name, .pop-name")!!.text()
                setUrlWithoutDomain(element.attr("abs:href"))
                thumbnail_url = element.selectFirst("img")?.attrOrNull("abs:src")
            }
        }
        return MangasPage(mangas, false)
    }

    override suspend fun getLatestUpdates(page: Int): MangasPage = getSearchMangaList(page, "", FilterList())

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val url = "$baseUrl/mangas.php".toHttpUrl().newBuilder()
            .addQueryParameter("q", query)
            .addQueryParameter("page", page.toString())
            .addQueryParameter("tab", filters.firstInstanceOrNull<TabFilter>()?.selected ?: "general")

        filters.firstInstanceOrNull<GenreFilter>()?.state.orEmpty()
            .filter { it.state }
            .forEach { url.addQueryParameter("generos[]", it.id) }

        val document = client.get(url.build(), Fetch.DOCUMENT).asJsoup()
        val mangas = document.select(".manga-grid .manga-card").map { element ->
            SManga.create().apply {
                title = element.selectFirst(".manga-title")!!.text()
                setUrlWithoutDomain(element.selectFirst("a")!!.attr("abs:href"))
                thumbnail_url = element.selectFirst(".card-cover img")?.attrOrNull("abs:src")
            }
        }
        val hasNextPage = document.selectFirst(".pagination a.page-btn:contains(Siguiente)") != null
        return MangasPage(mangas, hasNextPage)
    }

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host.removePrefix("www.") != baseUrl.toHttpUrl().host) return null
        if (url.pathSegments[0] != "manga" || url.pathSegments.getOrNull(1).isNullOrEmpty()) return null

        val document = client.get(url, Fetch.DOCUMENT).asJsoup()
        return parseMangaDetails(document).apply {
            setUrlWithoutDomain(document.selectFirst("link[rel=canonical]")!!.attr("abs:href"))
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
        return SMangaUpdate(parseMangaDetails(document), parseChapterList(document))
    }

    private fun parseMangaDetails(document: Document): SManga = SManga.create().apply {
        title = document.selectFirst(".manga-title")!!.text()
        author = document.selectFirst(".author-link")?.textOrNull()?.takeUnless { it in authorPlaceholders }
        description = MarkdownDescription.Builder()
            .description(document.selectFirst(".manga-description")?.textOrNull()?.takeUnless { it == "Información no completada" })
            .altTitles(document.select(".onf-alt-desktop .mal-alt-titulo").eachText(), "Nombres alternativos")
            .build()
        genre = document.select(".genre-tag").eachText().joinToString().ifEmpty { null }
        thumbnail_url = document.selectFirst(".manga-poster")?.attrOrNull("abs:src")

        val statusText = document.selectLast(".manga-meta span")?.text().orEmpty().lowercase()
        status = when {
            "emisión" in statusText -> SManga.ONGOING
            "finalizado" in statusText -> SManga.COMPLETED
            else -> SManga.UNKNOWN
        }
    }

    private fun parseChapterList(document: Document): List<SChapter> {
        val chapters = document.selectFirst("script:containsData(const chaptersData =)")!!.data()
            .substringAfter("const chaptersData = ")
            .substringBefore("\n")
            .trim()
            .removeSuffix(";")
        val onePerGroup = preferences.getBoolean(PREF_ONE_PER_GROUP, false)

        return chapters.parseAs<List<ChapterDto>>()
            .sortedWith(compareByDescending<ChapterDto> { it.numberFloat }.thenByDescending { it.date })
            .flatMap { it.toSChapters(onePerGroup) }
    }

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val chapterUrl = getChapterUrl(chapter)
        val document = client.get(chapterUrl, Fetch.DOCUMENT).asJsoup()
        val script = document.selectFirst("script:containsData(/reader_pages.php)")!!.data()
        val body = readerPagesBody(script) ?: return emptyList()
        return client.post("$baseUrl/reader_pages.php", Fetch.XHR.from(chapterUrl), body.toRequestBody(JSON_MEDIA_TYPE))
            .parseAs<ReaderPagesResponse>()
            .pages
            .mapIndexed { index, dto -> dto.toPage(index, document.location().toHttpUrl()) }
    }

    // The reader script's own request body for its page list, null when the chapter has no pages
    private fun readerPagesBody(script: String): String? = QuickJs.create().use { js ->
        js.evaluate(
            """
            var body;
            var window = {};
            var fetch = function(url, options) {
                if (url.indexOf('/reader_pages.php') !== -1) body = options.body;
                return { then: function() { return this; } };
            };

            $script

            body;
            """.trimIndent(),
        ) as String?
    }

    override fun getImageRequest(page: Page): Request {
        val pageUrl = page.url.toHttpUrl()
        // Page lists the app cached from before carry no fragment
        val alternatives = pageUrl.fragment?.parseAs<List<String>>().orEmpty().map { pageUrl.resolve(it)!! }
        return pageImageRequest(page.imageUrl!!.toHttpUrl(), pageUrl).newBuilder()
            .tag(PageUrls::class.java, PageUrls(pageUrl, alternatives))
            .build()
    }

    // The site's reader sends the chapter page as Referer to its own image proxy only, which answers 403 without it
    private fun pageImageRequest(url: HttpUrl, chapterUrl: HttpUrl): Request = if (url.host == chapterUrl.host) {
        Request.Builder().url(url).headers(headers).fetch(Fetch.IMAGE.from(chapterUrl)).build()
    } else {
        Request.Builder().url(url).headers(headersBuilder().removeAll("Referer").build()).fetch(Fetch.IMAGE).build()
    }

    private class PageUrls(val chapterUrl: HttpUrl, val alternatives: List<HttpUrl>)

    override suspend fun fetchFilterData(): JsonElement {
        val document = client.get("$baseUrl/mangas.php", Fetch.DOCUMENT).asJsoup()
        return document.select("label.cat-checkbox").map { label ->
            GenreDto(label.selectFirst("input")!!.attr("value"), label.selectFirst(".cat-label")!!.text())
        }.toJsonElement()
    }

    override fun getFilterList(data: JsonElement?): FilterList = FilterList(
        buildList {
            add(TabFilter())
            data?.parseAs<List<GenreDto>>()?.let { add(GenreFilter(it)) }
        },
    )

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        SwitchPreferenceCompat(screen.context).apply {
            key = PREF_ONE_PER_GROUP
            title = "Una versión por grupo"
            summary = "Oculta las copias extra de un capítulo subidas por el mismo grupo"
            setDefaultValue(false)
        }.also(screen::addPreference)
    }

    companion object {
        private const val PREF_ONE_PER_GROUP = "one_version_per_group"
        private const val CHECK_FAILED = "No se pudo pasar la verificación del sitio, ábrelo en WebView"
    }
}
