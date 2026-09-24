package eu.kanade.tachiyomi.extension.en.flamecomics

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Rect
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
import keiyoushi.utils.asJsoup
import keiyoushi.utils.parseAs
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.jsoup.nodes.Document
import java.io.ByteArrayOutputStream
import kotlin.time.Duration.Companion.seconds

@Source
abstract class FlameComics : KeiSource() {

    override val supportRelatedMangasBySearch = true

    override fun OkHttpClient.Builder.configureClient(): OkHttpClient.Builder = addInterceptor(::buildIdOutdatedInterceptor)
        .addInterceptor(::composedImageIntercept)
        .rateLimit(2, 2.seconds) { it.fragment != THUMBNAIL_FRAGMENT }

    override suspend fun getPopularManga(page: Int): MangasPage = fetchBrowseSeries()
        .sortedByDescending { it.views }
        .toMangasPage(page)

    override suspend fun getLatestUpdates(page: Int): MangasPage {
        val series = client.get(dataUrl { addPathSegment("index.json") })
            .parseAs<NextDataDto<LatestDto>>().pageProps.series
        return MangasPage(series.mapNotNull { it.toSManga() }, false)
    }

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val normalizedQuery = query.normalizeTitle()
        return fetchBrowseSeries().filter { series ->
            (listOf(series.title) + series.altTitles.orEmpty()).any { normalizedQuery in it.normalizeTitle() }
        }.toMangasPage(page)
    }

    private suspend fun fetchBrowseSeries(): List<SeriesDto> = client.get(dataUrl { addPathSegment("browse.json") })
        .parseAs<NextDataDto<BrowseDto>>().pageProps.series

    private fun List<SeriesDto>.toMangasPage(page: Int): MangasPage {
        val manga = mapNotNull { it.toSManga() }

        val itemsPerPage = 20
        val startIndex = (page - 1) * itemsPerPage
        val endIndex = minOf(page * itemsPerPage, manga.size)
        return MangasPage(manga.subList(startIndex, endIndex), endIndex < manga.size)
    }

    private fun String.normalizeTitle() = SPECIAL_CHARS_REGEX.replace(lowercase(), "")

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host != baseUrl.toHttpUrl().host || url.pathSegments.firstOrNull() != "series") return null
        val seriesId = url.pathSegments.getOrNull(1)?.toIntOrNull() ?: return null
        val manga = SManga.create().apply { this.url = "/series/$seriesId" }
        return getMangaUpdate(manga, emptyList(), fetchDetails = true, fetchChapters = false).manga
    }

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val seriesId = getMangaUrl(manga).toHttpUrl().pathSegments.last()
        val seriesPage = client.get(
            dataUrl {
                addPathSegment("series")
                addPathSegment("$seriesId.json")
                addQueryParameter("id", seriesId)
            },
        ).parseAs<NextDataDto<SeriesPageDto>>().pageProps

        return SMangaUpdate(
            manga = seriesPage.series.toSMangaDetails(),
            chapters = seriesPage.chapters.map { it.toSChapter() },
        )
    }

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val (seriesId, token) = getChapterUrl(chapter).toHttpUrl().pathSegments.drop(1)
        return client.get(
            dataUrl {
                addPathSegment("series")
                addPathSegment(seriesId)
                addPathSegment("$token.json")
                addQueryParameter("id", seriesId)
                addQueryParameter("token", token)
            },
        ).parseAs<NextDataDto<ChapterPageDto>>().pageProps.chapter.toPages()
    }

    // Next.js data routes are keyed by the site's build id, which changes on every deploy.
    @Volatile
    private var buildId: String? = null

    private suspend fun dataUrl(path: HttpUrl.Builder.() -> Unit): HttpUrl {
        val id = buildId ?: fetchBuildId(client.get(baseUrl).asJsoup()).also { buildId = it }

        return baseUrl.toHttpUrl().newBuilder()
            .addPathSegment("_next")
            .addPathSegment("data")
            .addPathSegment(id)
            .apply(path)
            .build()
    }

    private fun fetchBuildId(document: Document): String {
        val nextData = document.selectFirst("script#__NEXT_DATA__")?.data()
            ?: throw Exception("Failed to find __NEXT_DATA__")

        return nextData.parseAs<BuildIdDto>().buildId
    }

    private fun buildIdOutdatedInterceptor(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val response = chain.proceed(request)

        if (
            response.code == 404 &&
            request.url.run {
                host == baseUrl.removePrefix("https://") &&
                    pathSegments.getOrNull(0) == "_next" &&
                    pathSegments.getOrNull(1) == "data" &&
                    fragment != "DO_NOT_RETRY"
            } &&
            response.header("Content-Type")?.contains("text/html") != false
        ) {
            // The 404 page should have the current buildId
            val newBuildId = fetchBuildId(response.asJsoup())
            buildId = newBuildId

            // Redo request with new buildId
            val url = request.url.newBuilder()
                .setPathSegment(2, newBuildId)
                .fragment("DO_NOT_RETRY")
                .build()
            val newRequest = request.newBuilder()
                .url(url)
                .build()

            return chain.proceed(newRequest)
        }

        return response
    }

    private fun composedImageIntercept(chain: Interceptor.Chain): Response {
        if (!chain.request().url.toString().endsWith(COMPOSED_SUFFIX)) {
            return chain.proceed(chain.request())
        }

        val imageUrls = chain.request().url.toString()
            .removeSuffix(COMPOSED_SUFFIX)
            .split("%7C")

        var width = 0
        var height = 0

        val imageBitmaps = imageUrls.map { imageUrl ->
            val request = chain.request().newBuilder().url(imageUrl).build()
            val response = chain.proceed(request)

            val bitmap = BitmapFactory.decodeStream(response.body.byteStream())

            width += bitmap.width
            height = bitmap.height

            bitmap
        }

        val result = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)

        var left = 0

        imageBitmaps.forEach { bitmap ->
            val srcRect = Rect(0, 0, bitmap.width, bitmap.height)
            val dstRect = Rect(left, 0, left + bitmap.width, bitmap.height)

            canvas.drawBitmap(bitmap, srcRect, dstRect, null)

            left += bitmap.width
        }

        val output = ByteArrayOutputStream()
        result.compress(Bitmap.CompressFormat.PNG, 100, output)

        val responseBody = output.toByteArray().toResponseBody(MEDIA_TYPE)

        return Response.Builder()
            .code(200)
            .protocol(Protocol.HTTP_1_1)
            .request(chain.request())
            .message("OK")
            .body(responseBody)
            .build()
    }
    // Split Image Fixer End
}

private const val COMPOSED_SUFFIX = "?comp"
private val MEDIA_TYPE = "image/png".toMediaType()

internal const val THUMBNAIL_FRAGMENT = "thumbnail"

private val SPECIAL_CHARS_REGEX = Regex("""[^A-Za-z0-9 ]""")
