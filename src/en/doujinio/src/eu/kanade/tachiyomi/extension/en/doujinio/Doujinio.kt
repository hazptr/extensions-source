package eu.kanade.tachiyomi.extension.en.doujinio

import eu.kanade.tachiyomi.network.HttpException
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import keiyoushi.annotation.Source
import keiyoushi.network.getDocument
import keiyoushi.network.getXhr
import keiyoushi.network.postXhr
import keiyoushi.network.rateLimit
import keiyoushi.source.KeiSource
import keiyoushi.utils.firstInstanceOrNull
import keiyoushi.utils.parseAs
import keiyoushi.utils.toJsonElement
import keiyoushi.utils.toJsonRequestBody
import keiyoushi.utils.toJsonString
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.json.JsonElement
import okhttp3.CacheControl
import okhttp3.Headers
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Response
import java.net.URLDecoder
import java.util.concurrent.TimeUnit

const val LATEST_LIMIT = 20

@Source
abstract class Doujinio : KeiSource() {

    private val baseUrlHost get() = baseUrl.toHttpUrl().host

    override fun OkHttpClient.Builder.configureClient() = apply {
        addInterceptor(WatermarkRemover())
        rateLimit(2) { it.host == baseUrlHost }
    }

    // What the site's own API calls send
    private val apiHeaders get() = headersBuilder()
        .set("Accept", "application/json, text/plain, */*")
        .set("X-Requested-With", "XMLHttpRequest")
        .build()

    // POSTs also carry the site's CSRF token from its XSRF-TOKEN cookie
    private suspend fun postHeaders(): Headers {
        val token = xsrfToken() ?: run {
            client.getDocument(baseUrl, CacheControl.Builder().maxAge(0, TimeUnit.SECONDS).build()).close()
            xsrfToken()
        }
        return apiHeaders.newBuilder().apply { token?.let { set("X-XSRF-TOKEN", URLDecoder.decode(it, "UTF-8")) } }.build()
    }

    private fun xsrfToken() = client.cookieJar.loadForRequest(baseUrl.toHttpUrl()).firstOrNull { it.name == "XSRF-TOKEN" }?.value

    // Latest

    override suspend fun getLatestUpdates(page: Int): MangasPage {
        val response = client.postXhr(
            "$baseUrl/api/mangas/newest",
            postHeaders(),
            body = LatestRequest(
                limit = LATEST_LIMIT,
                offset = (page - 1) * LATEST_LIMIT,
            ).toJsonRequestBody(),
        )
        val latest = response.parseData<List<Manga>>().map { it.toSManga() }
        return MangasPage(latest, hasNextPage = latest.size >= LATEST_LIMIT)
    }

    // Popular

    override suspend fun getPopularManga(page: Int): MangasPage {
        val response = client.getXhr("$baseUrl/api/mangas/popular", apiHeaders)
        return MangasPage(
            response.parseData<List<Manga>>().map { it.toSManga() },
            hasNextPage = false,
        )
    }

    // Search

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val sortFilter = filters.firstInstanceOrNull<SortFilter>()!!
        val tags = filters.firstInstanceOrNull<TagGroup>()?.tagIds ?: emptyList()

        val response = client.postXhr(
            "$baseUrl/api/mangas/search",
            postHeaders(),
            body = SearchRequest(
                query,
                page,
                tags,
                sortFilter.sort,
                sortFilter.order,
            ).toJsonRequestBody(),
        )

        val result = response.parseData<SearchResponse>()
        return MangasPage(
            result.data.map { it.toSManga() },
            hasNextPage = result.to?.let { it < result.total } ?: false,
        )
    }

    // Details + Chapters

    override fun getMangaUrl(manga: SManga) = "$baseUrl/manga/${getIdFromUrl(manga.url)}"

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (baseUrlHost != url.host || url.pathSegments.size < 2) return null
        return fetchMangaDetails(url.pathSegments[1])
    }

    private suspend fun fetchMangaDetails(id: String): SManga {
        val response = client.getXhr("$baseUrl/api/mangas/$id", apiHeaders)
        return response.parseData<Manga>().toSManga()
    }

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate = coroutineScope {
        val id = getIdFromUrl(manga.url)

        val mangaDeferred = async { if (fetchDetails) fetchMangaDetails(id) else manga }
        val chaptersDeferred = async {
            if (fetchChapters) {
                val response = client.getXhr("$baseUrl/api/chapters".toHttpUrl().newBuilder().addQueryParameter("manga_id", id).build(), apiHeaders)
                response.parseData<List<Chapter>>().map { it.toSChapter() }.reversed()
            } else {
                chapters
            }
        }
        SMangaUpdate(mangaDeferred.await(), chaptersDeferred.await())
    }

    override fun getChapterUrl(chapter: SChapter) = "$baseUrl/${chapter.url}"

    // Page List

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val chapterApiUrl = "$baseUrl/api/mangas/${getIdsFromUrl(chapter.url)}"
        val response = client.getXhr(chapterApiUrl + "/manifest", apiHeaders, ensureSuccess = false)

        if (response.code == 401) {
            response.close()
            error("Login through WebView to read")
        }
        if (!response.isSuccessful) {
            response.close()
            throw HttpException(response.code)
        }

        val fragment = runCatching {
            val res = client.getXhr(chapterApiUrl + "/chm", apiHeaders)
            "#" + res.parseAs<MangaKeys>().toJsonString()
        }.getOrElse { "" }

        return response.parseAs<ChapterManifest>().toPageList(fragment)
    }

    override val supportRelatedMangasBySearch = true

    // Filters

    override val supportsFilterFetching = true

    override suspend fun fetchFilterData(): JsonElement {
        val response = client.getXhr("$baseUrl/api/tags", apiHeaders)
        return response.parseData<List<Tag>>().toJsonElement()
    }

    override fun getFilterList(data: JsonElement?) = FilterList(
        buildList {
            data?.parseAs<List<Tag>>()?.let { add(TagGroup(it)) }
            add(SortFilter())
        },
    )

    // Utilities

    private inline fun <reified T> Response.parseData(): T = parseAs<PageResponse<T>>().data

    private fun getIdFromUrl(url: String) = url.split("/").last()

    private fun getIdsFromUrl(url: String) = "${url.split("/")[1]}/${url.split("/").last()}"
}
