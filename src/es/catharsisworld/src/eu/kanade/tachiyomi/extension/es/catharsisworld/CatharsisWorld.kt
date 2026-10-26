package eu.kanade.tachiyomi.extension.es.catharsisworld

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.util.Base64
import eu.kanade.tachiyomi.network.HttpException
import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import keiyoushi.annotation.Source
import keiyoushi.network.get
import keiyoushi.network.post
import keiyoushi.network.rateLimit
import keiyoushi.source.KeiSource
import keiyoushi.utils.firstInstance
import keiyoushi.utils.firstInstanceOrNull
import keiyoushi.utils.parseAs
import keiyoushi.utils.sha256
import keiyoushi.utils.toJsonElement
import keiyoushi.utils.toJsonRequestBody
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.JsonElement
import okhttp3.Gzip
import okhttp3.Headers
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.Response
import okhttp3.ResponseBody.Companion.asResponseBody
import okhttp3.ResponseBody.Companion.toResponseBody
import okhttp3.brotli.Brotli
import okhttp3.zstd.Zstd
import okio.Buffer
import okio.InflaterSource
import okio.buffer
import java.io.IOException
import java.util.Locale
import java.util.zip.Inflater
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

@Source
abstract class CatharsisWorld : KeiSource() {

    private val apiUrl get() = "$baseUrl/api"

    override fun OkHttpClient.Builder.configureClient(): OkHttpClient.Builder = addInterceptor(::pageInterceptor)
        .addInterceptor(::cacheKeyInterceptor)
        .addNetworkInterceptor(::browserHeadersInterceptor)
        .rateLimit(24, 1.minutes, 2.seconds) { it.encodedPath.endsWith("/page-ticket") }
        .rateLimit(3, 1.seconds) { it.host == baseUrl.toHttpUrl().host }

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
        val dto = sessionMutex.withLock { openChapter(chapter.url) }
        return List(dto.paginas.size) { i ->
            val url = HttpUrl.Builder()
                .scheme("http")
                .host(PAGE_HOST)
                .addPathSegments(chapter.url)
                .addQueryParameter("page", i.toString())
                .build()
            Page(i, imageUrl = url.toString())
        }
    }

    override fun imageRequest(page: Page): Request = super.imageRequest(page).newBuilder()
        .addHeader("X-Catharsis-Reader", "1")
        .build()

    // Page image: a ticket per load, then the signed image
    private fun pageInterceptor(chain: Interceptor.Chain): Response {
        val request = chain.request()
        if (request.url.host != PAGE_HOST) return chain.proceed(request)
        val chapterUrl = request.url.encodedPath.removePrefix("/")
        val pageIndex = request.url.queryParameter("page")!!.toInt()
        val ticket = try {
            val session = runBlocking { chapterSession(chapterUrl) }
            mintPageTicket(chain, session, pageIndex, chapterUrl)
                ?: mintPageTicket(chain, runBlocking { chapterSession(chapterUrl, rejected = session) }, pageIndex, chapterUrl)
                ?: throw HttpException(401)
        } catch (e: IOException) {
            throw e
        } catch (e: Exception) {
            throw IOException(e.message, e)
        }
        val imageRequest = request.newBuilder()
            .url("$apiUrl/mangas/pages/${ticket.signedToken}")
            .header("Referer", chapterPageUrl(chapterUrl))
            .build()
        val response = chain.proceed(imageRequest)
        if (!response.isSuccessful) return response

        val body = response.body
        val source = body.source()
        if (!source.request(HEADER_SIZE.toLong())) return response
        if (source.buffer.size < HEADER_SIZE) return response
        val header = source.buffer.snapshot(HEADER_SIZE).toByteArray()
        val decoded = header.xor(ticket.xorByte)
        // XOR-ed with the ticket's byte unless it already starts with an image signature
        if (isImage(header) || !isImage(decoded)) return response

        val bytes = body.bytes()
        val decodedBytes = bytes.xor(ticket.xorByte)
        val bitmap = BitmapFactory.decodeByteArray(decodedBytes, 0, decodedBytes.size)
        if (bitmap == null || ticket.scrambleKey == null || bitmap.width < 200 || bitmap.height < 200) {
            return response.newBuilder()
                .body(decodedBytes.toResponseBody(body.contentType()))
                .build()
        }
        val descrambled = descramble(bitmap, ticket.scrambleKey)
        bitmap.recycle()
        val out = Buffer()
        descrambled.compress(Bitmap.CompressFormat.PNG, 100, out.outputStream())
        descrambled.recycle()
        return response.newBuilder()
            .body(out.readByteArray().toResponseBody("image/png".toMediaType()))
            .build()
    }

    private fun ByteArray.xor(key: Int): ByteArray = ByteArray(size) { (this[it].toInt() xor key).toByte() }

    private fun imul(a: Int, b: Int): Int = (a.toLong() * b.toLong()).toInt()

    private fun seededRandom(key: String): () -> Double {
        var hash = 0
        for (c in key) {
            hash = imul(hash, 31) + c.code
        }
        var state = hash
        return {
            state += 1831565813
            var y = state
            y = imul(y xor (y ushr 15), y or 1)
            y = y xor (y + imul(y xor (y ushr 7), y or 61))
            (y xor (y ushr 14)).toUInt().toLong().toDouble() / 4294967296.0
        }
    }

    private fun descramble(bitmap: Bitmap, key: String): Bitmap {
        val rand = seededRandom(key)
        val cols = 8
        val rows = 8
        val w = bitmap.width
        val h = bitmap.height

        val colBounds = IntArray(cols + 1)
        colBounds[0] = 0
        colBounds[cols] = w
        val baseColW = w / cols
        var x = 0
        for (i in 0 until cols - 1) {
            var cw = baseColW + Math.round((rand() * 0.7 - 0.35) * baseColW).toInt()
            val remaining = cols - 1 - i
            val maxW = w - x - remaining * 16
            cw = cw.coerceIn(16, maxOf(16, maxW))
            x += cw
            colBounds[i + 1] = x
        }

        val rowBounds = IntArray(rows + 1)
        rowBounds[0] = 0
        rowBounds[rows] = h
        val baseRowH = h / rows
        var yy = 0
        for (i in 0 until rows - 1) {
            var rh = baseRowH + Math.round((rand() * 0.7 - 0.35) * baseRowH).toInt()
            val remaining = rows - 1 - i
            val maxH = h - yy - remaining * 16
            rh = rh.coerceIn(16, maxOf(16, maxH))
            yy += rh
            rowBounds[i + 1] = yy
        }

        val colPerm = IntArray(cols) { it }
        for (i in cols - 1 downTo 1) {
            val j = (rand() * (i + 1)).toInt()
            val tmp = colPerm[i]
            colPerm[i] = colPerm[j]
            colPerm[j] = tmp
        }

        val rowPerm = IntArray(rows) { it }
        for (i in rows - 1 downTo 1) {
            val j = (rand() * (i + 1)).toInt()
            val tmp = rowPerm[i]
            rowPerm[i] = rowPerm[j]
            rowPerm[j] = tmp
        }

        val invCol = IntArray(cols)
        for (i in 0 until cols) invCol[colPerm[i]] = i
        val invRow = IntArray(rows)
        for (i in 0 until rows) invRow[rowPerm[i]] = i

        val shuffledColPos = IntArray(cols + 1)
        shuffledColPos[0] = 0
        for (i in 0 until cols) {
            val shuffledIdx = colPerm[i]
            shuffledColPos[i + 1] = shuffledColPos[i] + (colBounds[shuffledIdx + 1] - colBounds[shuffledIdx])
        }

        val shuffledRowPos = IntArray(rows + 1)
        shuffledRowPos[0] = 0
        for (i in 0 until rows) {
            val shuffledIdx = rowPerm[i]
            shuffledRowPos[i + 1] = shuffledRowPos[i] + (rowBounds[shuffledIdx + 1] - rowBounds[shuffledIdx])
        }

        val output = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        val paint = Paint().apply { isFilterBitmap = false }

        for (row in 0 until rows) {
            for (col in 0 until cols) {
                val dx = colBounds[col]
                val dy = rowBounds[row]
                val dw = colBounds[col + 1] - colBounds[col]
                val dh = rowBounds[row + 1] - rowBounds[row]
                val sx = shuffledColPos[invCol[col]]
                val sy = shuffledRowPos[invRow[row]]
                val src = Rect(sx, sy, sx + dw, sy + dh)
                val dst = Rect(dx, dy, dx + dw, dy + dh)
                canvas.drawBitmap(bitmap, src, dst, paint)
            }
        }
        return output
    }

    private fun isImage(bytes: ByteArray): Boolean {
        val b = IntArray(HEADER_SIZE) { bytes[it].toInt() and 0xFF }
        return (b[0] == 0x52 && b[1] == 0x49 && b[2] == 0x46 && b[3] == 0x46) ||
            (b[0] == 0x89 && b[1] == 0x50 && b[2] == 0x4E && b[3] == 0x47) ||
            (b[0] == 0xFF && b[1] == 0xD8 && b[2] == 0xFF) ||
            (b[0] == 0x47 && b[1] == 0x49 && b[2] == 0x46) ||
            (b[4] == 0x66 && b[5] == 0x74 && b[6] == 0x79 && b[7] == 0x70)
    }

    private class ChapterSession(val chapterId: Int, val token: String, val expiresAt: Instant)

    private class FailedOpen(val chapterUrl: String, val failedAt: Instant, val error: Exception)

    private val sessionMutex = Mutex()
    private val chapterSessions = HashMap<String, ChapterSession>()
    private var failedOpen: FailedOpen? = null
    private var currentReaderPass: ReaderPassDto? = null

    // Session for pageInterceptor; a failed open replays for 10 s
    private suspend fun chapterSession(chapterUrl: String, rejected: ChapterSession? = null): ChapterSession = sessionMutex.withLock {
        val now = Clock.System.now()
        chapterSessions[chapterUrl]
            ?.takeIf { it !== rejected && it.expiresAt > now + 1.minutes }
            ?.let { return@withLock it }
        failedOpen
            ?.takeIf { it.chapterUrl == chapterUrl && now - it.failedAt < 10.seconds }
            ?.let { throw it.error }
        try {
            openChapter(chapterUrl)
        } catch (e: Exception) {
            failedOpen = FailedOpen(chapterUrl, now, e)
            throw e
        }
        chapterSessions.getValue(chapterUrl)
    }

    // Caller holds sessionMutex
    private suspend fun openChapter(chapterUrl: String): PagesDto {
        val url = "$apiUrl/mangas/$chapterUrl"
        val pageHeaders = readerHeaders(chapterUrl)
        val dto = fetchChapter(url, readerPass(pageHeaders), pageHeaders)
            ?: run {
                currentReaderPass = null
                fetchChapter(url, readerPass(pageHeaders), pageHeaders)
            }
            ?: throw HttpException(403)
        val token = dto.chapterSessionToken
        val payload = Base64.decode(token.substringBefore("."), Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)
            .decodeToString()
            .parseAs<ChapterTokenPayload>()
        val now = Clock.System.now()
        chapterSessions.values.removeAll { it.expiresAt < now }
        chapterSessions[chapterUrl] = ChapterSession(payload.cid, token, Instant.fromEpochSeconds(payload.exp))
        failedOpen = null
        return dto
    }

    // Null when the site asks for a new reader pass
    private suspend fun fetchChapter(url: String, pass: ReaderPassDto, pageHeaders: Headers): PagesDto? {
        val passHeaders = pageHeaders.newBuilder().set("X-Catharsis-Reader-Pass", pass.pass).build()
        val response = client.get(url, passHeaders, ensureSuccess = false)
        if (response.isSuccessful) return response.parseAs<PagesDto>()
        if (response.code == 401) {
            response.close()
            throw Exception("This chapter requires login on the website")
        }
        val code = response.errorCode()
        if (code == "READER_PASS_REQUIRED") return null
        throw siteError(response.code, code)
    }

    // Null when the site rejects the session token: expired, or issued to another IP
    private fun mintPageTicket(chain: Interceptor.Chain, session: ChapterSession, pageIndex: Int, chapterUrl: String): PageTicketResponse? {
        val request = POST(
            "$apiUrl/mangas/chapters/${session.chapterId}/page-ticket",
            readerHeaders(chapterUrl),
            PageTicketRequest(pageIndex, session.token).toJsonRequestBody(),
        )
        var response = chain.proceed(request)
        // The site answers 429 when tickets come too quickly
        repeat(TICKET_RETRIES) {
            if (response.code != 429) return@repeat
            response.close()
            Thread.sleep(TICKET_RETRY_DELAY.inWholeMilliseconds)
            response = chain.proceed(request)
        }
        if (response.code == 401) {
            response.close()
            return null
        }
        if (!response.isSuccessful) {
            response.close()
            throw HttpException(response.code)
        }
        return response.parseAs<PageTicketResponse>()
    }

    // Reader pass from the site's Cap proof-of-work; caller holds sessionMutex
    private suspend fun readerPass(pageHeaders: Headers): ReaderPassDto {
        currentReaderPass
            ?.takeIf { Instant.fromEpochMilliseconds(it.expiresAt) > Clock.System.now() + 1.minutes }
            ?.let { return it }
        val system = client.get("$apiUrl/system", pageHeaders).parseAs<SystemDto>()
        val capUrl = system.capApiEndpoint.toHttpUrl()
        val challenge = client.post(capUrl.resolve("challenge")!!, RequestBody.EMPTY).parseAs<CapChallengeResponse>()
        val solutions = (1..challenge.challenge.count).map { i ->
            solveCap(
                salt = capPrng("${challenge.token}$i", challenge.challenge.saltSize),
                target = capPrng("${challenge.token}${i}d", challenge.challenge.difficulty),
            )
        }
        val redeemed = client.post(
            capUrl.resolve("redeem")!!,
            CapRedeemRequest(challenge.token, solutions).toJsonRequestBody(),
        ).parseAs<CapRedeemResponse>()
        val capToken = redeemed.token?.takeIf { redeemed.success }
            ?: throw Exception("Reader verification failed")
        val response = client.post(
            "$apiUrl/mangas/reader-pass",
            pageHeaders,
            ReaderPassRequest(capToken, system.capSitekey).toJsonRequestBody(),
            ensureSuccess = false,
        )
        if (!response.isSuccessful) throw siteError(response.code, response.errorCode())
        return response.parseAs<ReaderPassDto>().also { currentReaderPass = it }
    }

    // FNV-1a seeded xorshift32, as hex
    private fun capPrng(seed: String, length: Int): String {
        var state = seed.fold(FNV_OFFSET_BASIS) { hash, char -> (hash xor char.code) * FNV_PRIME }
        return buildString {
            while (this.length < length) {
                state = state xor (state shl 13)
                state = state xor (state ushr 17)
                state = state xor (state shl 5)
                append(state.toUInt().toString(16).padStart(8, '0'))
            }
        }.take(length)
    }

    private fun solveCap(salt: String, target: String): Int = generateSequence(0) { it + 1 }
        .first { "$salt$it".sha256().toHexString().startsWith(target) }

    private fun Response.errorCode(): String? {
        if (header("Content-Type")?.startsWith("application/json") != true) {
            close()
            return null
        }
        return parseAs<ApiErrorDto>().code
    }

    // Error codes the site's reader shows a message for
    private fun siteError(status: Int, code: String?): Exception = when (code) {
        "DATACENTER_BLOCKED" -> Exception("The site doesn't allow reading through a VPN or proxy")
        "EARLY_ACCESS" -> Exception("This chapter is still in early access on the site")
        else -> HttpException(status)
    }

    // The site's API varies on Origin and Accept-Encoding: the cache sees them as they go out
    private fun cacheKeyInterceptor(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val builder = request.newBuilder().header("Accept-Encoding", ACCEPT_ENCODING)
        if (request.url.host == baseUrl.toHttpUrl().host && request.method == "GET") builder.removeHeader("Origin")
        return chain.proceed(builder.build())
    }

    // What Chrome sends for the same request from the site's page, in its order; the site refuses a Chrome
    // user agent without its fetch metadata
    private fun browserHeadersInterceptor(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val sameOrigin = request.url.host == baseUrl.toHttpUrl().host
        val path = request.url.encodedPath
        val kind = when {
            !sameOrigin -> RequestKind.CROSS_SITE_FETCH
            path.startsWith("/api/mangas/pages/") -> RequestKind.PAGE_FETCH
            path.startsWith("/api/") -> RequestKind.API
            else -> RequestKind.IMAGE
        }
        val userAgent = request.header("User-Agent").orEmpty()
        val chromeVersion = CHROME_VERSION_REGEX.find(userAgent)?.groupValues?.get(1)?.toInt()
        val values = HashMap<String, String>()
        request.headers.forEach { (name, value) -> values[name.lowercase()] = value }
        values -= "cache-control"
        values["content-type"]?.takeIf { it.startsWith("application/json") }?.let { values["content-type"] = "application/json" }
        if (chromeVersion != null) {
            values["sec-ch-ua"] = chromeBrands(chromeVersion)
            values["sec-ch-ua-mobile"] = if ("Mobile" in userAgent) "?1" else "?0"
            values["sec-ch-ua-platform"] = "\"${platform(userAgent)}\""
        }
        if (kind == RequestKind.API) {
            values["system"] = "catharsis"
            values["x-fk-sistema"] = "3"
        }
        values["accept"] = when (kind) {
            RequestKind.API -> API_ACCEPT
            RequestKind.IMAGE -> IMAGE_ACCEPT
            else -> "*/*"
        }
        values["sec-fetch-site"] = if (sameOrigin) "same-origin" else "cross-site"
        values["sec-fetch-mode"] = if (kind == RequestKind.IMAGE) "no-cors" else "cors"
        values["sec-fetch-dest"] = if (kind == RequestKind.IMAGE) "image" else "empty"
        values["accept-language"] = acceptLanguage()
        if (chain.connection()?.protocol() == Protocol.HTTP_2) values["priority"] = if (kind == RequestKind.IMAGE) "i" else "u=1, i"

        val order = when {
            kind == RequestKind.API && request.method == "GET" -> API_GET_ORDER
            kind == RequestKind.API -> API_POST_ORDER
            kind == RequestKind.PAGE_FETCH -> PAGE_FETCH_ORDER
            else -> SUBRESOURCE_ORDER
        }
        val headers = Headers.Builder().apply {
            values["host"]?.let { add("Host", it) }
            order.forEach { name -> values[name]?.let { add(name, it) } }
            values.filterKeys { it != "host" && it != "cookie" && it !in order }.forEach { (name, value) -> add(name, value) }
            values["cookie"]?.let { add("cookie", it) }
        }.build()
        return chain.proceed(request.newBuilder().headers(headers).build()).decoded()
    }

    // Decodes the encodings Chrome offers, deflate included, as the request set its own Accept-Encoding
    private fun Response.decoded(): Response {
        if (code == 204 || code == 304 || request.method == "HEAD") return this
        val source = body.source()
        val decoded = when (header("Content-Encoding")?.lowercase()) {
            "gzip" -> Gzip.decompress(source)
            "br" -> Brotli.decompress(source)
            "zstd" -> Zstd.decompress(source)
            // zlib-wrapped as the spec says, or raw as some servers send
            "deflate" -> InflaterSource(source, Inflater(!source.request(1) || source.buffer[0] != ZLIB_HEADER))
            else -> return this
        }
        return newBuilder()
            .removeHeader("Content-Encoding")
            .removeHeader("Content-Length")
            .body(decoded.buffer().asResponseBody(body.contentType()))
            .build()
    }

    private fun platform(userAgent: String) = when {
        "Android" in userAgent -> "Android"
        "CrOS" in userAgent -> "Chrome OS"
        "Macintosh" in userAgent -> "macOS"
        "Linux" in userAgent -> "Linux"
        else -> "Windows"
    }

    private enum class RequestKind { API, PAGE_FETCH, IMAGE, CROSS_SITE_FETCH }

    // Chromium's brand list for a version, its placeholder brand included
    private fun chromeBrands(version: Int): String {
        val brands = arrayOfNulls<String>(3)
        val order = BRAND_ORDERS[version % BRAND_ORDERS.size]
        brands[order[0]] = "\"Not${GREASE_CHARS[version % GREASE_CHARS.size]}A${GREASE_CHARS[(version + 1) % GREASE_CHARS.size]}Brand\";v=\"${GREASE_VERSIONS[version % GREASE_VERSIONS.size]}\""
        brands[order[1]] = "\"Chromium\";v=\"$version\""
        brands[order[2]] = "\"Google Chrome\";v=\"$version\""
        return brands.joinToString()
    }

    private fun acceptLanguage(): String {
        val locale = Locale.getDefault()
        val language = Locale.Builder().setLanguage(locale.language).build().toLanguageTag()
        return if (locale.country.isEmpty()) language else "$language-${locale.country},$language;q=0.9"
    }

    // Requests from the site's reader carry the chapter page as Referer
    private fun readerHeaders(chapterUrl: String) = headersBuilder()
        .set("Referer", chapterPageUrl(chapterUrl))
        .build()

    private fun chapterPageUrl(chapterUrl: String) = getChapterUrl(SChapter.create().apply { url = chapterUrl })

    companion object {
        private const val PAGE_SIZE = 24
        private val CHROME_VERSION_REGEX = Regex("""Chrome/(\d+)""")
        private const val ACCEPT_ENCODING = "gzip, deflate, br, zstd"
        private const val ZLIB_HEADER = 0x78.toByte()
        private const val API_ACCEPT = "application/json, text/plain, */*"
        private const val IMAGE_ACCEPT = "image/jxl,image/avif,image/webp,image/apng,image/svg+xml,image/*,*/*;q=0.8"
        private val GREASE_CHARS = listOf(" ", "(", ":", "-", ".", "/", ")", ";", "=", "?", "_")
        private val GREASE_VERSIONS = listOf("8", "99", "24")

        // Chrome's header orders for these requests
        private val API_GET_ORDER = listOf(
            "sec-ch-ua-platform", "system", "sec-ch-ua", "sec-ch-ua-mobile", "x-fk-sistema", "x-catharsis-reader-pass", "accept",
            "user-agent", "sec-fetch-site", "sec-fetch-mode", "sec-fetch-dest", "referer", "accept-encoding", "accept-language", "priority",
        )
        private val API_POST_ORDER = listOf(
            "content-length", "sec-ch-ua-platform", "system", "sec-ch-ua", "sec-ch-ua-mobile", "x-catharsis-reader-pass", "x-fk-sistema",
            "accept", "content-type", "user-agent", "origin", "sec-fetch-site", "sec-fetch-mode", "sec-fetch-dest", "referer",
            "accept-encoding", "accept-language", "priority",
        )
        private val PAGE_FETCH_ORDER = listOf(
            "x-catharsis-reader", "sec-ch-ua-platform", "user-agent", "sec-ch-ua", "sec-ch-ua-mobile", "accept", "sec-fetch-site",
            "sec-fetch-mode", "sec-fetch-dest", "referer", "accept-encoding", "accept-language", "priority",
        )
        private val SUBRESOURCE_ORDER = listOf(
            "content-length", "sec-ch-ua-platform", "user-agent", "sec-ch-ua", "content-type", "sec-ch-ua-mobile", "accept", "origin",
            "sec-fetch-site", "sec-fetch-mode", "sec-fetch-dest", "referer", "accept-encoding", "accept-language", "priority",
        )
        private val BRAND_ORDERS = listOf(listOf(0, 1, 2), listOf(0, 2, 1), listOf(1, 0, 2), listOf(1, 2, 0), listOf(2, 0, 1), listOf(2, 1, 0))
        private const val TICKET_RETRIES = 3
        private val TICKET_RETRY_DELAY = 5.seconds
        private const val HEADER_SIZE = 12
        private const val PAGE_HOST = "catharsisworld.invalid"
        private const val FNV_OFFSET_BASIS = -0x7ee3623b
        private const val FNV_PRIME = 0x01000193
    }
}
