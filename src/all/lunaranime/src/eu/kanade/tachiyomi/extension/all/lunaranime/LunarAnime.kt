package eu.kanade.tachiyomi.extension.all.lunaranime

import android.webkit.WebResourceResponse
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import keiyoushi.annotation.Source
import keiyoushi.network.get
import keiyoushi.network.rateLimit
import keiyoushi.source.KeiSource
import keiyoushi.utils.getTurnstileToken
import keiyoushi.utils.parseAs
import keiyoushi.utils.runWebView
import keiyoushi.utils.toJsonString
import kotlinx.serialization.json.JsonElement
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.Duration.Companion.seconds

@Source
abstract class LunarAnime : KeiSource() {

    private val internalLang: String = when (lang) {
        "pt-BR" -> "pt-br"
        else -> lang
    }

    private val apiurlHost by lazy { API_URL.toHttpUrl().host }
    private val cdnurlHost by lazy { CDN_URL.toHttpUrl().host }

    private val serenity = LunarSerenity(API_URL)

    // Per-series expiry of the site reader's clearance
    private val clearances = ConcurrentHashMap<String, Long>()

    override fun OkHttpClient.Builder.configureClient() = this
        .addInterceptor(serenity.interceptor())
        .addInterceptor { chain ->
            val request = chain.request()
            val url = request.url.toString()
            if (url.contains(cdnurlHost)) {
                val newRequest = request.newBuilder()
                    .header("Referer", "$baseUrl/")
                    .build()
                chain.proceed(newRequest)
            } else {
                chain.proceed(request)
            }
        }
        .rateLimit(2) { it.host == apiurlHost || it.host == cdnurlHost }

    // ============================== Popular ===============================

    override suspend fun getPopularManga(page: Int): MangasPage = getSearchMangaList(page, "", FilterList())

    // =============================== Latest ===============================

    override suspend fun getLatestUpdates(page: Int): MangasPage {
        val url = API_URL.toHttpUrl().newBuilder().apply {
            addPathSegments("api/manga/recent")
            addQueryParameter("page", page.toString())
            addQueryParameter("limit", "30")

            if (lang != "all") {
                addQueryParameter("language", internalLang)
            }
        }.build()

        val result = client.get(url).parseAs<LunarRecentResponse>()
        return MangasPage(
            mangas = result.mangas.map { it.toSManga() },
            hasNextPage = (result.page * result.limit) < result.totalCount,
        )
    }

    // =============================== Search ===============================

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val url = API_URL.toHttpUrl().newBuilder().apply {
            addPathSegments("api/manga/search")
            addQueryParameter("page", page.toString())
            addQueryParameter("limit", "30")
            if (query.isNotBlank()) {
                addQueryParameter("query", query)
            }

            if (lang != "all") {
                addQueryParameter("language", internalLang)
            }

            filters.forEach { filter ->
                when (filter) {
                    is StatusFilter -> filter.toValue()?.let { addQueryParameter("status", it) }
                    is TypeFilter -> filter.toValue()?.let { addQueryParameter("country", it) }
                    is LanguageFilter -> filter.toValue()?.let { addQueryParameter("language", it) }
                    is YearFilter -> {
                        val year = filter.state
                        if (year.isNotBlank() && year.toIntOrNull() != null) {
                            addQueryParameter("year", year)
                        }
                    }
                    is GenreFilter -> {
                        val genres = filter.toGenres()
                        if (genres.isNotEmpty()) {
                            addQueryParameter("genres", genres.joinToString(","))
                        }
                    }
                    else -> {}
                }
            }
            addQueryParameter("sort", "relevance")
        }.build()

        val result = client.get(url).parseAs<LunarSearchResponse>()
        return MangasPage(
            mangas = result.manga.map { it.toSManga() },
            hasNextPage = result.page < result.totalPages,
        )
    }

    // =========================== Manga Details ============================

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        val segments = url.pathSegments.filter { it.isNotEmpty() }
        if (segments.size < 2 || segments[0] != "manga") return null

        return fetchMangaDetails(segments[1])
    }

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val slug = manga.url.substringAfterLast("/")

        return SMangaUpdate(
            manga = if (fetchDetails) fetchMangaDetails(slug) else manga,
            chapters = if (fetchChapters) fetchChapterList(slug) else chapters,
        )
    }

    private suspend fun fetchMangaDetails(slug: String): SManga {
        val url = API_URL.toHttpUrl().newBuilder()
            .addPathSegments("api/manga/title")
            .addPathSegment(slug)
            .build()

        return client.get(url).parseAs<LunarMangaResponse>().manga.toSManga()
    }

    // ============================== Chapters ==============================

    override fun getChapterUrl(chapter: SChapter): String {
        val url = chapter.url.substringBefore("?")
        return baseUrl + url
    }

    private suspend fun fetchChapterList(slug: String): List<SChapter> {
        val passwordUrl = API_URL.toHttpUrl().newBuilder()
            .addPathSegments("api/manga/password/info")
            .addPathSegment(slug)
            .build()
        val passwordInfo = client.get(passwordUrl).parseAs<LunarPasswordInfoResponse>()

        val requestUrl = API_URL.toHttpUrl().newBuilder()
            .addPathSegments("api/manga")
            .addPathSegment(slug)
            .build()
        val result = client.get(requestUrl).parseAs<LunarChapterListResponse>()

        return result.data.filter {
            lang == "all" || it.language == internalLang
        }.map { chapter ->
            val isLocked = passwordInfo.hasSeriesPassword ||
                passwordInfo.chapterPasswords.any {
                    it.chapterNumber == chapter.chapter && (it.language == null || it.language == chapter.language)
                }
            chapter.toSChapter(slug, isLocked)
        }.reversed()
    }

    // =============================== Pages ================================

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val chapterUrl = (baseUrl + chapter.url).toHttpUrl()
        val slug = chapterUrl.pathSegments[1]

        val clearedUntil = clearances[slug]?.takeIf { it > System.currentTimeMillis() }
        val images = clearedUntil?.let { readReaderPages(chapterUrl, slug, clearedUntil = it) }
            ?: readReaderPages(chapterUrl, slug, turnstileToken = fetchTurnstileToken(chapterUrl.toString()))
            ?: throw Exception("Site only served placeholder pages")

        return images.mapIndexed { index, imageUrl ->
            Page(index, url = chapter.url, imageUrl = API_URL.toHttpUrl().resolve(imageUrl)!!.toString())
        }
    }

    private suspend fun fetchTurnstileToken(chapterUrl: String): String? {
        val captcha = client.get("$API_URL/api/captcha/config").parseAs<LunarCaptchaConfig>()
        return if (captcha.mangaTurnstile) getTurnstileToken(chapterUrl, captcha.mangaTurnstileSiteKey) else null
    }

    // Runs the site's reader, which only hands real page urls to its own client. Null for placeholder pages.
    private suspend fun readReaderPages(
        chapterUrl: HttpUrl,
        slug: String,
        turnstileToken: String? = null,
        clearedUntil: Long? = null,
    ): List<String>? {
        val siteOrigin = baseUrl.toHttpUrl().run { "$scheme://$host" }
        val siteHost = baseUrl.toHttpUrl().host
        val language = chapterUrl.queryParameter("lang") ?: "en"
        val bridgeName = randomName()

        return runWebView(timeout = 60.seconds) {
            blockImages = true

            interceptRequest { request ->
                val url = request.url.toString().toHttpUrlOrNull() ?: return@interceptRequest null
                when {
                    url.host == TURNSTILE_HOST && url.encodedPath == TURNSTILE_SCRIPT_PATH && turnstileToken != null -> {
                        val script = "(${asset("turnstile.js")})(${turnstileToken.toJsonString()})"
                        WebResourceResponse("application/javascript", "utf-8", script.byteInputStream())
                    }
                    url.host == siteHost || url.host.endsWith(".$siteHost") || url.host == TURNSTILE_HOST -> null
                    else -> WebResourceResponse("text/plain", "utf-8", ByteArray(0).inputStream())
                }
            }

            webMessageBridge(bridgeName, setOf(siteOrigin)) { message ->
                val payload = message.parseAs<LunarReaderPayload>()
                val data = payload.data
                when {
                    payload.locked -> reject(Exception("Chapter is locked on the site"))
                    // Placeholder rounds leave out the slug
                    data == null || (payload.slug ?: slug) != slug || (data.language ?: language) != language -> Unit
                    data.isComingSoon -> resolve(emptyList())
                    data.images.isNotEmpty() && data.images.all { PLACEHOLDER_PATH in it } -> {
                        clearances.remove(slug)
                        resolve(null)
                    }
                    payload.cacheStatus == "revalidate" -> resolve(data.images)
                    else -> {
                        val now = System.currentTimeMillis()
                        val ttl = payload.cacheTtl?.times(1000)?.coerceAtMost(MAX_CLEARANCE_MS) ?: MAX_CLEARANCE_MS
                        clearances.values.removeIf { it < now }
                        clearances[slug] = now + ttl - CLEARANCE_MARGIN_MS
                        resolve(data.images)
                    }
                }
            }

            if (clearedUntil != null) {
                val key = "$CLEARANCE_KEY$slug".toJsonString()
                injectAtDocumentStart("sessionStorage.setItem($key, '$clearedUntil')", setOf(siteOrigin))
            }
            injectAtDocumentStart("(${asset("reader_pages.js")})(page => window.$bridgeName.postMessage(page))", setOf(siteOrigin))
            loadUrl(chapterUrl.toString())
        }
    }

    private fun randomName() = (1..(10..20).random())
        .map { (('a'..'z') + ('A'..'Z')).random() }
        .joinToString("")

    private fun asset(name: String) = javaClass.getResource("/assets/$name")!!.readText()

    override fun imageRequest(page: Page): Request {
        val imageHeaders = headersBuilder()
            .set("Referer", baseUrl + page.url)
            .set("Accept", "image/avif,image/webp,image/apng,image/svg+xml,image/*,*/*;q=0.8")
            .build()
        return GET(page.imageUrl!!, imageHeaders)
    }

    // ============================== Filters ===============================

    override fun getFilterList(data: JsonElement?): FilterList {
        val filters = mutableListOf<Filter<*>>(
            StatusFilter(),
            TypeFilter(),
        )

        if (lang == "all") {
            filters.add(LanguageFilter())
        }

        filters.addAll(
            listOf(
                YearFilter(),
                GenreFilter(),
            ),
        )

        return FilterList(filters)
    }

    companion object {
        private const val API_URL = "https://api.lunarx.to"
        private const val CDN_URL = "https://vault.lunarx.to"
        private const val TURNSTILE_HOST = "challenges.cloudflare.com"
        private const val TURNSTILE_SCRIPT_PATH = "/turnstile/v0/api.js"
        private const val PLACEHOLDER_PATH = "/api/cdn/p/"
        private const val CLEARANCE_KEY = "lunar_ts_clearance:v3:"
        private const val MAX_CLEARANCE_MS = 1_500_000L
        private const val CLEARANCE_MARGIN_MS = 15_000L
    }
}
