package eu.kanade.tachiyomi.extension.ja.klmanga

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
import keiyoushi.utils.parseAs
import okhttp3.FormBody
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import javax.net.ssl.SSLContext

@Source
abstract class Klmanga : KeiSource() {
    override fun OkHttpClient.Builder.configureClient() = apply {
        val trustManager = AiaTrustManager(network.client)
        val sslContext = SSLContext.getInstance("TLS").apply { init(null, arrayOf(trustManager), null) }
        sslSocketFactory(sslContext.socketFactory, trustManager)
        rateLimit(1) { it.encodedPath == AJAX_PATH }
    }

    override suspend fun getPopularManga(page: Int): MangasPage {
        val document = client.get(baseUrl, Fetch.DOCUMENT).asJsoup()
        return MangasPage(document.select(".hero-banners .swiper-slide").map(::mangaFromCard), false)
    }

    override suspend fun getLatestUpdates(page: Int): MangasPage = gridPage(pageUrl(page).build())

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage = gridPage(pageUrl(page).addQueryParameter("s", query).build())

    private fun pageUrl(page: Int) = baseUrl.toHttpUrl().newBuilder().apply {
        if (page > 1) addPathSegments("page/$page/")
    }

    private suspend fun gridPage(url: HttpUrl): MangasPage {
        val document = client.get(url, Fetch.DOCUMENT).asJsoup()
        val mangas = document.select(".grid-of-mangas .entry").map(::mangaFromCard)
        return MangasPage(mangas, document.selectFirst(".z-pagination a.next") != null)
    }

    private fun mangaFromCard(element: Element) = SManga.create().apply {
        val link = element.selectFirst(".name a")!!
        setUrlWithoutDomain(link.absUrl("href"))
        title = link.text().removeTitleSuffix()
        thumbnail_url = element.selectFirst(".thumb img")?.absUrl("src")
        genre = element.select(".genres a").joinToString { it.text() }.ifEmpty { null }
    }

    override suspend fun getMangasByUrl(url: HttpUrl, page: Int): MangasPage {
        val segments = url.pathSegments
        if (url.host != baseUrl.toHttpUrl().host || segments.size < 2 || segments[0] != GENRE_PATH || segments[1].isEmpty()) {
            return super.getMangasByUrl(url, page)
        }
        val genreUrl = baseUrl.toHttpUrl().newBuilder()
            .addPathSegment(GENRE_PATH)
            .addPathSegment(segments[1])
            .apply { if (page > 1) addPathSegment("page").addPathSegment(page.toString()) }
            .addPathSegment("")
            .build()
        return gridPage(genreUrl)
    }

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host != baseUrl.toHttpUrl().host) return null
        val segments = url.pathSegments
        // /manga-raw/page/N/ is the site's archive listing, not a series
        if (segments.size < 2 || segments[0] != MANGA_PATH || segments[1].isEmpty() || segments[1] == "page") return null
        val mangaUrl = baseUrl.toHttpUrl().newBuilder().addPathSegment(MANGA_PATH).addPathSegment(segments[1]).addPathSegment("").build()
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
        title = document.selectFirst(".z-single-mg h1.name")!!.text().removeTitleSuffix()
        thumbnail_url = document.selectFirst(".main-thumb img")?.absUrl("src")
        description = MarkdownDescription.Builder()
            .description(document.selectFirst(".z-single-mg .content-text"))
            .build()
        genre = document.select(".z-single-mg > .genres a").joinToString { it.text() }.ifEmpty { null }
    }

    private fun parseChapters(document: Document) = document.select(".chapter-box h4 a").map { element ->
        SChapter.create().apply {
            setUrlWithoutDomain(element.absUrl("href"))
            name = element.selectFirst("span")!!.text().removeTitleSuffix()
        }
    }

    override val supportsRelatedMangas get() = true

    override suspend fun fetchRelatedMangaList(manga: SManga): List<SManga> {
        val document = client.get(getMangaUrl(manga), Fetch.DOCUMENT).asJsoup()
        return document.select(".grid-related .entry").map(::mangaFromCard)
    }

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val chapterUrl = getChapterUrl(chapter)
        val document = client.get(chapterUrl, Fetch.DOCUMENT).asJsoup()
        val script = document.selectFirst("script:containsData(reading_chapter)")!!.data()
        val readingChapter = READING_CHAPTER_REGEX.find(script)!!.groupValues[1]
        val ajaxUrl = baseUrl.toHttpUrl().newBuilder().encodedPath(AJAX_PATH).build()
        val fetch = Fetch.XHR.from(chapterUrl)

        var imgIndex = 0
        val images = buildList {
            do {
                val body = FormBody.Builder()
                    .add("action", "z_do_ajax")
                    .add("_action", "decode_images")
                    .add("reading_chapter", readingChapter)
                    .add("img_index", imgIndex.toString())
                    .add("content", "")
                    .build()
                val result = client.post(ajaxUrl, fetch, body).parseAs<DecodeImagesDto>()
                val batch = Jsoup.parseBodyFragment(result.html, baseUrl).select("img").map { it.absUrl("src") }
                addAll(batch)
                val advanced = result.imgIndex > imgIndex
                imgIndex = result.imgIndex
                // Past the last page the site keeps answering going = 1 with no images
            } while (result.going == 1 && batch.isNotEmpty() && advanced)
        }

        return images.mapIndexed { index, url -> Page(index, imageUrl = url) }
    }

    private fun String.removeTitleSuffix() = replace(TITLE_SUFFIX, "")

    companion object {
        private const val MANGA_PATH = "manga-raw"
        private const val GENRE_PATH = "manga-genre"
        private const val AJAX_PATH = "/wp-admin/admin-ajax.php"
        private val READING_CHAPTER_REGEX = Regex("""reading_chapter:\s*(\d+)""")

        // Lists write the suffix with an en dash, some series pages with a hyphen
        private val TITLE_SUFFIX = Regex("""\s*\(Raw [-\u2013] Free\)""")
    }
}
