package eu.kanade.tachiyomi.extension.es.catharsisworld

import android.util.Base64
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
import keiyoushi.utils.NativeWebViewApi
import keiyoushi.utils.firstInstance
import keiyoushi.utils.firstInstanceOrNull
import keiyoushi.utils.parseAs
import keiyoushi.utils.runWebView
import keiyoushi.utils.toJsonElement
import keiyoushi.utils.toJsonString
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.JsonElement
import okhttp3.Headers
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import kotlin.random.Random
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource

@Source
abstract class CatharsisWorld : KeiSource() {

    private val apiUrl get() = "$baseUrl/api"

    override fun OkHttpClient.Builder.configureClient(): OkHttpClient.Builder = addInterceptor(::pageInterceptor)
        .rateLimit(3, 1.seconds) { it.host == baseUrl.toHttpUrl().host }

    override fun Headers.Builder.configureHeaders(): Headers.Builder = add("System", "catharsis")
        .add("X-FK-Sistema", "3")

    override suspend fun getPopularManga(page: Int) = searchMangas(page, "", "-n_visitas", null, emptyList())

    override suspend fun getLatestUpdates(page: Int) = searchMangas(page, "", "-fecha_ultimo_capitulo", null, emptyList())

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val genres = filters.firstInstanceOrNull<GenreFilter>()?.state.orEmpty().filter { it.state }.map { it.id }
        return searchMangas(
            page = page,
            query = query,
            sort = filters.firstInstance<SortFilter>().value,
            status = filters.firstInstance<StatusFilter>().value,
            genres = genres,
        )
    }

    private suspend fun searchMangas(page: Int, query: String, sort: String, status: String?, genres: List<String>): MangasPage {
        val url = "$apiUrl/mangas".toHttpUrl().newBuilder().apply {
            addQueryParameter("page", page.toString())
            addQueryParameter("limit", PAGE_SIZE.toString())
            if (query.isNotBlank()) addQueryParameter("name", query.trim())
            if (genres.isNotEmpty()) addQueryParameter("genre", genres.joinToString("|"))
            status?.let { addQueryParameter("status", it) }
            addQueryParameter("sort", sort)
        }.build()
        val result = client.get(url).parseAs<MangaListDto>()
        return MangasPage(result.data.map { it.toSManga(baseUrl) }, page < result.totalPages)
    }

    override val supportsFilterFetching = true

    override suspend fun fetchFilterData(): JsonElement = client.get("$apiUrl/mangas/genres").parseAs<List<GenreDto>>().toJsonElement()

    override fun getFilterList(data: JsonElement?) = FilterList(
        buildList {
            add(SortFilter())
            add(StatusFilter())
            data?.parseAs<List<GenreDto>>()?.let { genres ->
                add(GenreFilter(genres.map { GenreCheckBox(it.nombre, it.id) }))
            }
        },
    )

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host != baseUrl.toHttpUrl().host) return null
        val segments = url.pathSegments
        if (segments.size < 2 || segments[0] != "manga") return null
        return client.get("$apiUrl/mangas/${segments[1]}").parseAs<MangaDto>().toSMangaDetails(baseUrl)
    }

    override fun getMangaUrl(manga: SManga) = "$baseUrl/manga/${manga.url}"

    override fun getChapterUrl(chapter: SChapter): String {
        val (mangaId, number) = chapter.url.split("/", limit = 2)
        return "$baseUrl/manga/$mangaId/chapter/$number"
    }

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val dto = client.get("$apiUrl/mangas/${manga.url}").parseAs<MangaDto>()
        return SMangaUpdate(
            dto.toSMangaDetails(baseUrl),
            dto.capitulos
                .filter { it.estado == null || it.estado == "publicado" }
                .sortedByDescending { it.number }
                .map { it.toSChapter(manga.url) },
        )
    }

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val total = reader(chapter.url).total.await()
        return List(total) { index ->
            val url = HttpUrl.Builder()
                .scheme("http")
                .host(PAGE_HOST)
                .addPathSegments(chapter.url)
                .addQueryParameter("page", index.toString())
                .build()
            Page(index, imageUrl = url.toString())
        }
    }

    // Page image: the page as the site's reader drew it
    private fun pageInterceptor(chain: Interceptor.Chain): Response {
        val request = chain.request()
        if (request.url.host != PAGE_HOST) return chain.proceed(request)
        val chapterUrl = request.url.encodedPath.removePrefix("/")
        val index = request.url.queryParameter("page")!!.toInt()
        val data = try {
            // The reader outlives this call, so only the wait is cancelled with it
            runBlocking {
                val page = async { withTimeout(PAGE_TIMEOUT) { awaitPage(chapterUrl, index) } }
                while (page.isActive && !chain.call().isCanceled()) delay(CANCEL_POLL)
                page.cancel()
                Base64.decode(page.await(), Base64.DEFAULT)
            }
        } catch (_: TimeoutCancellationException) {
            throw IOException("Timed out loading page ${index + 1}")
        } catch (e: Throwable) {
            throw e as? IOException ?: IOException(e.message, e)
        }
        return Response.Builder()
            .request(request)
            .protocol(Protocol.HTTP_1_1)
            .code(200)
            .message("OK")
            .body(data.toResponseBody("image/jpeg".toMediaType()))
            .build()
    }

    // A reader that closed on its own just before gets replaced
    private suspend fun awaitPage(chapterUrl: String, index: Int): String = try {
        awaitPage(reader(chapterUrl), index)
    } catch (_: ReaderClosedException) {
        awaitPage(reader(chapterUrl), index)
    }

    private suspend fun awaitPage(reader: ChapterReader, index: Int): String {
        val page = reader.want(index)
        try {
            return page.await()
        } finally {
            reader.unwant(index)
        }
    }

    private class ChapterReader {
        val total = CompletableDeferred<Int>()
        val pages = ConcurrentHashMap<Int, CompletableDeferred<String>>()

        // Pages the app waits on, with how many calls wait on each, and pages the site's reader has drawn
        private val waiters = HashMap<Int, Int>()
        val drawn: MutableSet<Int> = ConcurrentHashMap.newKeySet()

        val wanted: List<Int> get() = synchronized(waiters) { waiters.keys.sorted() }

        val busy get() = !total.isCompleted || synchronized(waiters) { waiters.isNotEmpty() }

        @Volatile
        var lastUsed = TimeSource.Monotonic.markNow()

        @Volatile
        var failure: Pair<Throwable, TimeSource.Monotonic.ValueTimeMark>? = null

        @Volatile
        var closed = false

        lateinit var job: Job

        fun page(index: Int) = pages.getOrPut(index) { CompletableDeferred() }.also { page ->
            if (closed) page.completeExceptionally(failure?.first ?: ReaderClosedException())
        }

        fun want(index: Int): CompletableDeferred<String> = synchronized(waiters) {
            waiters[index] = (waiters[index] ?: 0) + 1
            lastUsed = TimeSource.Monotonic.markNow()
            page(index)
        }

        fun unwant(index: Int) = synchronized(waiters) {
            val left = waiters.getValue(index) - 1
            if (left == 0) {
                waiters.remove(index)
                pages.remove(index)
            } else {
                waiters[index] = left
            }
            lastUsed = TimeSource.Monotonic.markNow()
        }

        fun close(error: Throwable) {
            closed = true
            total.completeExceptionally(error)
            pages.values.forEach { it.completeExceptionally(error) }
        }
    }

    private class ReaderClosedException : Exception("The site's reader closed")

    private val readerScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val readers = LinkedHashMap<String, ChapterReader>()

    // The open reader for a chapter, or a new one; a failed one replays its error for a few seconds
    private fun reader(chapterUrl: String): ChapterReader = synchronized(readers) {
        readers[chapterUrl]?.let { reader ->
            val failure = reader.failure
            if (failure != null && failure.second.elapsedNow() < READER_RETRY_DELAY) return reader
            if (!reader.closed) return reader
        }
        readers.values.removeAll { it.closed }
        if (readers.size >= MAX_READERS) {
            readers.entries.filterNot { it.value.busy }
                .maxByOrNull { it.value.lastUsed.elapsedNow() }
                ?.let { (key, reader) ->
                    reader.job.cancel()
                    readers.remove(key)
                }
        }
        val reader = ChapterReader()
        readers[chapterUrl] = reader
        reader.job = readerScope.launch {
            var error: Throwable = ReaderClosedException()
            try {
                runReader(chapterUrl, reader)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                error = e
                reader.failure = e to TimeSource.Monotonic.markNow()
            } finally {
                reader.close(error)
            }
        }
        reader
    }

    // One scroll every 2-3 s across all readers, at most 40 pages a minute: the site 429s bursts of page tickets
    private val turnLock = Any()
    private var nextTurn = TimeSource.Monotonic.markNow()
    private val pagesDrawn = ArrayDeque<TimeSource.Monotonic.ValueTimeMark>()

    private fun takeTurn(): Boolean = synchronized(turnLock) {
        while (pagesDrawn.firstOrNull()?.let { it.elapsedNow() > 1.minutes } == true) pagesDrawn.removeFirst()
        if (!nextTurn.hasPassedNow() || pagesDrawn.size >= PAGES_PER_MINUTE) return false
        nextTurn = TimeSource.Monotonic.markNow() + Random.nextLong(TURN_MS.first, TURN_MS.last).milliseconds
        true
    }

    private fun pageDrawn() = synchronized(turnLock) {
        pagesDrawn.addLast(TimeSource.Monotonic.markNow())
    }

    private fun slowDown() = synchronized(turnLock) {
        nextTurn = TimeSource.Monotonic.markNow() + SLOW_DOWN
    }

    // The site's own reader in a WebView, closed when no page was asked for in a while
    @OptIn(NativeWebViewApi::class)
    private suspend fun runReader(chapterUrl: String, reader: ChapterReader) {
        val readerUrl = getChapterUrl(SChapter.create().apply { url = chapterUrl }).toHttpUrl()
        val origin = "${readerUrl.scheme}://${readerUrl.host}"
        val (bridgeName, controlName) = List(2) { randomName() }
        val startedAt = TimeSource.Monotonic.markNow()
        var turns = 0
        runWebView<Unit>(timeout = Duration.INFINITE) {
            // The site refuses the stock WebView user agent
            userAgent = headers["User-Agent"]!!
            blockImages = true
            // The reader loads pages as they scroll into view, which needs a drawn WebView
            withNativeWebView { BackgroundSurface.host(it) }
            webMessageBridge(bridgeName, setOf(origin)) { payload ->
                val message = payload.parseAs<ReaderMessage>()
                when (message.type) {
                    "total" -> reader.total.complete(message.total!!)
                    "ready" -> if (reader.drawn.add(message.index!!)) pageDrawn()
                    "page" -> reader.page(message.index!!).complete(message.data!!)
                    "slow" -> slowDown()
                    "error" -> reject(readerError(message.status!!, message.code))
                }
            }
            // A login or redirect page in place of the reader
            onPageStarted { url ->
                val path = url.toHttpUrlOrNull()?.encodedPath
                if (path != readerUrl.encodedPath) reject(Exception("The site sent the reader to ${path ?: url}; open the chapter in WebView"))
            }
            poll(READER_POLL) {
                if (!reader.total.isCompleted && startedAt.elapsedNow() > READER_OPEN_TIMEOUT) {
                    reject(Exception("The site's reader didn't open the chapter"))
                    return@poll
                }
                val wanted = reader.wanted
                if (wanted.isEmpty() && reader.lastUsed.elapsedNow() > READER_IDLE) {
                    resolve(Unit)
                    return@poll
                }
                if (wanted.any { it !in reader.drawn } && takeTurn()) turns++
                evaluateJs("window.$controlName = ${ReaderControl(wanted, turns).toJsonString()};")
            }
            injectAtDocumentStart(
                "(${asset("reader.js")})(message => window.$bridgeName.postMessage(message), \"$controlName\")",
                setOf(origin),
            )
            loadUrl(readerUrl.toString())
        }
    }

    // Error codes the site's reader shows a message for
    private fun readerError(status: Int, code: String?): Exception = when {
        code == "DATACENTER_BLOCKED" -> Exception("The site doesn't allow reading through a VPN or proxy")
        code == "EARLY_ACCESS" -> Exception("This chapter is still in early access on the site")
        code == "CAP_FAILED" -> Exception("The site's anti-bot check failed")
        status == 401 -> Exception("This chapter requires login on the website")
        status == 403 -> Exception("The site refused this chapter (${code ?: status}); open it in WebView")
        else -> Exception("The site's reader shows no pages for this chapter; open it in WebView")
    }

    private fun randomName() = (1..(10..20).random())
        .map { (('a'..'z') + ('A'..'Z')).random() }
        .joinToString("")

    private fun asset(name: String) = javaClass.getResource("/assets/$name")!!.readText()

    companion object {
        private const val PAGE_SIZE = 24
        private const val PAGE_HOST = "catharsisworld.invalid"
        private const val MAX_READERS = 2
        private val TURN_MS = 2000L..3000L
        private const val PAGES_PER_MINUTE = 40
        private val SLOW_DOWN = 10.seconds
        private val READER_OPEN_TIMEOUT = 90.seconds
        private val PAGE_TIMEOUT = 90.seconds
        private val READER_IDLE = 60.seconds
        private val READER_RETRY_DELAY = 10.seconds
        private val READER_POLL = 500.milliseconds
        private val CANCEL_POLL = 250.milliseconds
    }
}
