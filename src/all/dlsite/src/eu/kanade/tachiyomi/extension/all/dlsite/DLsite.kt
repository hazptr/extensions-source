package eu.kanade.tachiyomi.extension.all.dlsite

import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import keiyoushi.annotation.Source
import keiyoushi.network.Fetch
import keiyoushi.network.XorInterceptor
import keiyoushi.network.get
import keiyoushi.network.post
import keiyoushi.network.rateLimit
import keiyoushi.source.KeiSource
import keiyoushi.utils.asJsoup
import keiyoushi.utils.attrOrNull
import keiyoushi.utils.decodeHex
import keiyoushi.utils.firstInstanceOrNull
import keiyoushi.utils.getArray
import keiyoushi.utils.getString
import keiyoushi.utils.parseAs
import keiyoushi.utils.string
import keiyoushi.utils.stringOrNull
import keiyoushi.utils.textOrNull
import keiyoushi.utils.toJsonElement
import keiyoushi.utils.toJsonString
import keiyoushi.zip.Entry
import keiyoushi.zip.zipDirectory
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.RequestBody
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import java.net.URLEncoder
import java.nio.charset.Charset
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes

@Source
abstract class DLsite : KeiSource() {

    private val language get() = LANGUAGES.getValue(lang)

    private val sessionMutex = Mutex()
    private var bookSession: BookSampleSession? = null

    override fun OkHttpClient.Builder.configureClient() = rateLimit(2) { it.host == baseUrl.toHttpUrl().host || it.host == PLAY_HOST }
        .addInterceptor(TrialInterceptor(TRIAL_HOST))
        // Book sample pages: XOR with the work's key, carried in the fragment
        .addInterceptor(XorInterceptor(SAMPLE_MEDIA_TYPE) { request -> request.url.fragment?.takeIf { request.url.host == SAMPLE_CDN_HOST }?.decodeHex() })

    override suspend fun getPopularManga(page: Int) = getSearchMangaList(page, "", FilterList())

    override suspend fun getLatestUpdates(page: Int) = getSearchMangaList(page, "", SortFilter.LATEST)

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val section = filters.firstInstanceOrNull<SectionFilter>()?.value ?: "maniax"
        val types = filters.firstInstanceOrNull<TypeFilter>()?.selected.orEmpty().ifEmpty { listOf("comic", "illust") }
        val genres = filters.firstInstanceOrNull<GenreFilter>()?.selected.orEmpty()
        val url = "$baseUrl/$section/fsr/ajax/=".toHttpUrl().newBuilder().apply {
            fun param(name: String, value: String) = addPathSegment(name).addPathSegment(value)
            param("language", "jp")
            // Like the site, spaces are written "+" in the keyword: it answers "%20" with a 403
            if (query.isNotBlank()) addPathSegment("keyword").addEncodedPathSegment(URLEncoder.encode(query.trim(), "UTF-8"))
            types.forEachIndexed { index, type -> param("work_type_category[$index]", type) }
            if (language.options.size > 1) param("options_and_or", "or")
            language.options.forEachIndexed { index, option -> param("options[$index]", option) }
            if (filters.firstInstanceOrNull<NoAiFilter>()?.state == true) {
                AI_OPTIONS.forEachIndexed { index, option -> param("options_not[$index]", option) }
            }
            if (genres.isNotEmpty()) param("genre_and_or", "and")
            genres.forEachIndexed { index, genre -> param("genre[$index]", genre) }
            param("order[0]", filters.firstInstanceOrNull<SortFilter>()?.value ?: "trend")
            param("per_page", PAGE_SIZE.toString())
            param("page", page.toString())
            param("show_type", "1")
            addQueryParameter("locale", language.locale)
        }.build()

        // The results come as HTML, in a table for the Japanese locale and in a grid for the others
        val result = client.get(url, Fetch.XHR).parseAs<SearchDto>()
        val mangas = Jsoup.parseBodyFragment(result.searchResult, baseUrl).select("[data-list_item_product_id]").map { item ->
            toSManga(
                item.attr("data-list_item_product_id"),
                item.selectFirst(".work_name a[href]")!!,
                item.selectFirst("thumb-with-ng-filter-block")?.attr(":thumb-candidates"),
            ).apply {
                author = item.select(".author a").joinToString { it.text() }.ifEmpty { item.selectFirst(".maker_name a")?.textOrNull() }
            }
        }
        return MangasPage(mangas, result.pageInfo.lastIndice < result.pageInfo.count)
    }

    // A work in the site's lists, with its section in memo for the work page
    private fun toSManga(workno: String, link: Element, coverCandidates: String?) = SManga.create().apply {
        url = workno
        title = link.attrOrNull("title") ?: link.text()
        thumbnail_url = coverCandidates?.let(::coverUrl)
        memo = buildJsonObject { put("site", link.absUrl("href").toHttpUrl().pathSegments.first()) }
    }

    // The list's thumbnail candidates, a WebP and a JPEG, can be resized copies of the main image: it has the same path
    // under /modpub/, without the size
    private fun coverUrl(candidates: String): String? {
        val urls = CANDIDATE_REGEX.findAll(candidates).map { it.groupValues[1] }.toList()
        val url = urls.firstOrNull { it.endsWith(".jpg") } ?: urls.firstOrNull() ?: return null
        return baseUrl.toHttpUrl().resolve(url.replace("/resize/", "/modpub/").replace(SIZE_SUFFIX_REGEX, ""))?.toString()
    }

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host != baseUrl.toHttpUrl().host && url.host != PLAY_HOST) return null
        val workno = PRODUCT_PATH_REGEX.matchEntire(url.encodedPath)?.groupValues?.get(1) ?: return null
        val product = fetchProduct(workno)?.takeIf { it.workType in READABLE_TYPES } ?: return null
        return product.toSManga(baseUrl) { workUrl(it, product.siteId) }
    }

    override fun getMangaUrl(manga: SManga) = workUrl(manga.url, manga.memo["site"]?.stringOrNull)

    private fun workUrl(workno: String, site: String?) = "$baseUrl/${site ?: "maniax"}/work/=/product_id/$workno.html".toHttpUrl().newBuilder()
        .addQueryParameter("locale", language.locale)
        .build()
        .toString()

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate = coroutineScope {
        val page = if (fetchDetails) async { client.get(getMangaUrl(manga), Fetch.DOCUMENT).asJsoup() } else null
        val product = fetchProduct(manga.url) ?: throw Exception("This work isn't on sale anymore")
        val editionUrl = { workno: String -> workUrl(workno, product.siteId) }
        val updatedChapters = if (fetchChapters) {
            val trialFiles = product.trialFiles
            val trials = trialFiles.mapIndexed { index, trial ->
                async { trialChapters(product, trial, if (trialFiles.size > 1) "Trial ${index + 1}" else "Trial") }
            }
            trials.awaitAll().flatten() + listOfNotNull(product.toSampleChapter(baseUrl))
        } else {
            chapters
        }
        val details = if (page != null) {
            product.toSManga(baseUrl, editionUrl).apply { description = product.description(page.await().selectFirst("[itemprop=description]"), editionUrl) }
        } else {
            manga
        }
        SMangaUpdate(details, updatedChapters)
    }

    private suspend fun fetchProduct(workno: String): ProductDto? {
        val url = "$baseUrl/maniax/api/=/product.json".toHttpUrl().newBuilder()
            .addQueryParameter("workno", workno)
            .addQueryParameter("locale", language.locale)
            .build()
        return client.get(url, Fetch.XHR).parseAs<List<ProductDto>>().firstOrNull()
    }

    // One chapter per folder of images: some trials hold the pages once per language
    private suspend fun trialChapters(product: ProductDto, trial: TrialFileDto, label: String): List<SChapter> {
        val folders = trialFolders(trial.zipUrl).keys
        return folders.map { folder ->
            SChapter.create().apply {
                url = if (folders.size > 1) "/${trial.relativeUrl}/$folder" else "/${trial.relativeUrl}"
                name = buildList {
                    add(label)
                    if (folders.size > 1) add(folder.substringAfterLast('/').ifEmpty { "/" })
                }.joinToString(" - ")
                date_upload = product.date
                memo = buildJsonObject {
                    put("zip", trial.zipUrl)
                    put("folder", folder)
                    put("workno", product.workno)
                    put("site", product.siteId)
                }
            }
        }
    }

    // The image folders in archive order, each in page order. Trials made by Japanese tools store names in Shift-JIS
    private suspend fun trialFolders(zipUrl: String): Map<String, List<Entry>> = client.zipDirectory(zipUrl, charset = SHIFT_JIS).entries
        .filter { IMAGE_REGEX.containsMatchIn(it.name) }
        .sortedBy { it.localHeaderOffset }
        .groupBy { it.name.substringBeforeLast('/', "") }
        .mapValues { (_, entries) -> entries.sortedBy { entry -> NUMBER_REGEX.replace(entry.name.lowercase()) { it.value.padStart(10, '0') } } }

    override fun getChapterUrl(chapter: SChapter): String {
        if (chapter.memo["zip"] == null) return "https://$PLAY_HOST${chapter.url}"
        return workUrl(chapter.memo.getString("workno"), chapter.memo["site"]?.stringOrNull)
    }

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        chapter.memo["zip"]?.stringOrNull?.let { zipUrl ->
            val folder = trialFolders(zipUrl)[chapter.memo.getString("folder")] ?: throw Exception("Refresh the chapter list")
            return folder.mapIndexed { index, entry ->
                val data = TrialEntry(entry.name, entry.localHeaderOffset, entry.compressedSize, entry.method, entry.localHeaderAllowance).toJsonString()
                Page(index, imageUrl = "$zipUrl#$data")
            }
        }
        return if (chapter.memo.getString("sample") == "epub") {
            bookSamplePages(chapter.memo.getString("workno"))
        } else {
            chapter.memo.getArray("images").mapIndexed { index, image -> Page(index, imageUrl = image.string) }
        }
    }

    private suspend fun bookSamplePages(workno: String): List<Page> {
        val session = bookSampleSession(workno)
        return client.get(session.token.url("viewer-meta.json"), Fetch.XHR.from(sampleViewerUrl(workno)))
            .parseAs<ViewerMetaDto>()
            .pages
            .filter { it.type == "image" }
            .mapIndexed { index, page -> Page(index, url = "${sampleViewerUrl(workno)}#${page.src}") }
    }

    // Signed again when the session the page list was made with has expired
    override suspend fun getImageUrl(page: Page): String {
        val url = page.url.toHttpUrl()
        val session = bookSampleSession(url.pathSegments.last())
        return session.token.url(url.fragment!!).newBuilder().fragment(session.key).build().toString()
    }

    private suspend fun bookSampleSession(workno: String): BookSampleSession = sessionMutex.withLock {
        val current = bookSession
        if (current != null && current.workno == workno && current.token.expiresAt > Clock.System.now() + SESSION_MARGIN) {
            return@withLock current
        }
        val from = Fetch.XHR.from(sampleViewerUrl(workno))
        val token = client.post("https://$PLAY_HOST/api/sample/viewer/token/$workno", from, RequestBody.EMPTY).parseAs<ViewerTokenDto>()
        val key = client.get(token.url("decryption.key"), from).body.string().trim()
        BookSampleSession(workno, token, key).also { bookSession = it }
    }

    private class BookSampleSession(val workno: String, val token: ViewerTokenDto, val key: String)

    private fun sampleViewerUrl(workno: String) = "https://$PLAY_HOST/viewer/sample/$workno"

    override val supportsRelatedMangas = true

    // The work page's same-series, circle and label lists
    override suspend fun fetchRelatedMangaList(manga: SManga): List<SManga> = coroutineScope {
        client.get(getMangaUrl(manga), Fetch.DOCUMENT).asJsoup()
            .select(RELATED_LISTS.joinToString { "[data-href*=/type/$it/]" })
            .map { list -> async { client.get(list.absUrl("data-href"), Fetch.XHR).asJsoup() } }
            .awaitAll()
            .flatMap { it.select("li[data-prod]") }
            .filter { it.selectFirst("[data-work_type]")?.attr("data-work_type") in READABLE_TYPES }
            .map { item ->
                toSManga(
                    item.attr("data-prod"),
                    item.selectFirst("a[href*=/product_id/]:not(.work_thumb)")!!,
                    item.selectFirst("img-with-fallback")?.attr(":candidates"),
                )
            }
            .distinctBy { it.url }
            .filter { it.url != manga.url }
    }

    override val supportsFilterFetching = true

    override suspend fun fetchFilterData(): JsonElement {
        val url = "$baseUrl/maniax/genre/list".toHttpUrl().newBuilder().addQueryParameter("locale", language.locale).build()
        return client.get(url, Fetch.DOCUMENT).asJsoup()
            .select("a[href*=/works/genre/=/genre/]")
            .map { GenreOption(it.text().replace(COUNT_REGEX, ""), it.absUrl("href").toHttpUrl().pathSegments.last()) }
            .distinctBy { it.id }
            .toJsonElement()
    }

    override fun getFilterList(data: JsonElement?): FilterList {
        val genres = data?.parseAs<List<GenreOption>>().orEmpty()
        return FilterList(
            buildList {
                add(SectionFilter())
                add(TypeFilter())
                add(SortFilter())
                add(NoAiFilter())
                if (genres.isNotEmpty()) add(GenreFilter(genres))
            },
        )
    }

    private class Language(val locale: String, val options: List<String>)

    companion object {
        private const val PAGE_SIZE = 30
        private const val PLAY_HOST = "play.dlsite.com"
        private const val TRIAL_HOST = "trial.dlsite.com"
        private const val SAMPLE_CDN_HOST = "play.dl.dlsite.com"
        private val SAMPLE_MEDIA_TYPE = "image/webp".toMediaType()
        private val SESSION_MARGIN = 10.minutes

        // The site's locale for each source, and the language options of the works it lists
        private val LANGUAGES = mapOf(
            // Japanese works, and the ones that need no language
            "ja" to Language("ja_JP", listOf("JPN", "NM")),
            "en" to Language("en_US", listOf("ENG")),
            "zh-Hans" to Language("zh_CN", listOf("CHI_HANS")),
            "zh-Hant" to Language("zh_TW", listOf("CHI_HANT")),
            "ko" to Language("ko_KR", listOf("KO_KR")),
            "es" to Language("es_ES", listOf("SPA")),
            "fr" to Language("fr_FR", listOf("FRE")),
            "id" to Language("id_ID", listOf("IND")),
            "th" to Language("th_TH", listOf("THA")),
            "vi" to Language("vi_VN", listOf("VIE")),
            "pt-BR" to Language("pt_BR", listOf("POR")),
        )

        // Manga, CG and illustrations, webtoons
        private val READABLE_TYPES = setOf("MNG", "ICG", "WBT")

        // AI-generated and AI-assisted works
        val AI_OPTIONS = listOf("AIG", "AIP")

        private val RELATED_LISTS = listOf("same_series", "maker_works", "label_works")

        private val PRODUCT_PATH_REGEX = Regex("""/(?:[a-z-]+/(?:work|announce)/=/product_id/|(?:viewer/)?sample/)([A-Z]{2}\d+)(?:\.html)?""")
        private val CANDIDATE_REGEX = Regex("""'([^']+)'""")
        private val SIZE_SUFFIX_REGEX = Regex("""_\d+x\d+(?=\.\w+$)""")
        private val COUNT_REGEX = Regex("""\s*\([\d,.]+\)$""")
        private val IMAGE_REGEX = Regex("""\.(?:jpe?g|png|webp|gif)$""", RegexOption.IGNORE_CASE)
        private val NUMBER_REGEX = Regex("""\d+""")
        private val SHIFT_JIS = Charset.forName("MS932")
    }
}
