package eu.kanade.tachiyomi.extension.zh.bilibilimanga

import android.content.SharedPreferences
import android.util.Base64
import androidx.preference.ListPreference
import androidx.preference.PreferenceScreen
import androidx.preference.SwitchPreferenceCompat
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import keiyoushi.annotation.Source
import keiyoushi.network.Fetch
import keiyoushi.network.addCookie
import keiyoushi.network.get
import keiyoushi.network.post
import keiyoushi.network.rateLimit
import keiyoushi.source.KeiSource
import keiyoushi.utils.JSON_MEDIA_TYPE
import keiyoushi.utils.aesCbc
import keiyoushi.utils.aesDecrypt
import keiyoushi.utils.firstInstanceOrNull
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.parseAs
import keiyoushi.utils.toJsonString
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody
import java.math.BigInteger
import java.security.KeyPairGenerator
import java.security.interfaces.ECPublicKey
import java.security.spec.ECGenParameterSpec
import kotlin.time.Clock

@Source
abstract class BilibiliManga :
    KeiSource(),
    ConfigurableSource {

    private val preferences: SharedPreferences by getPreferencesLazy()

    override fun OkHttpClient.Builder.configureClient() = apply {
        rateLimit(2) { it.host == baseUrl.toHttpUrl().host }
        addInterceptor(ImageInterceptor(keyPair.private))
        addCookie {
            val buvid3 = preferences.getString(BUVID3_PREF, null) ?: return@addCookie emptyList()
            listOf("buvid3" to buvid3, "buvid4" to preferences.getString(BUVID4_PREF, "")!!)
        }
    }

    override suspend fun getPopularManga(page: Int) = classPage(page, order = 0)

    override suspend fun getLatestUpdates(page: Int) = classPage(page, order = 1)

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        if (query.isNotBlank()) {
            val body = SearchBody(query, page, PAGE_SIZE).toJsonString()
            val from = baseUrl.toHttpUrl().newBuilder().addPathSegment("search").addQueryParameter("keyword", query).build()
            val result = twirp("Search", body, from.toString()).parseAs<SearchDto>()
            return MangasPage(result.list.map { it.toSManga() }, page * PAGE_SIZE < result.totalNum)
        }
        return classPage(
            page,
            styleId = filters.firstInstanceOrNull<StyleFilter>()?.id ?: -1,
            areaId = filters.firstInstanceOrNull<AreaFilter>()?.id ?: -1,
            isFinish = filters.firstInstanceOrNull<StatusFilter>()?.id ?: -1,
            order = filters.firstInstanceOrNull<OrderFilter>()?.id ?: 0,
            specialTag = filters.firstInstanceOrNull<SpecialFilter>()?.id ?: 0,
            isFree = filters.firstInstanceOrNull<PriceFilter>()?.id ?: -1,
        )
    }

    // -1 (0 for specialTag) is the site's "all".
    private suspend fun classPage(
        page: Int,
        order: Int,
        styleId: Int = -1,
        areaId: Int = -1,
        isFinish: Int = -1,
        specialTag: Int = 0,
        isFree: Int = -1,
    ): MangasPage {
        val body = ClassPageBody(styleId, areaId, isFinish, order, specialTag, page, PAGE_SIZE, isFree, m2).toJsonString()
        val items = twirp("ClassPage", body, "/classify", styleId.toString()).parseAs<List<ClassPageItemDto>>()
        // Pages can come back short while more follow.
        return MangasPage(items.map { it.toSManga() }, items.isNotEmpty())
    }

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host != baseUrl.toHttpUrl().host) return null
        val id = url.pathSegments.firstNotNullOfOrNull { COMIC_SEGMENT.matchEntire(it)?.groupValues?.get(1) } ?: return null
        return comicDetail(id.toInt()).toSManga(baseUrl).apply { initialized = true }
    }

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val comic = comicDetail(manga.url.substringAfter("/mc").toInt())
        val showLocked = preferences.getBoolean(SHOW_LOCKED_PREF, false)
        val episodes = comic.episodes.filter { showLocked || !it.isPaywalled }
        return SMangaUpdate(comic.toSManga(baseUrl), episodes.map { it.toSChapter(comic.id) })
    }

    private suspend fun comicDetail(id: Int): ComicDto {
        val body = ComicIdBody(id).toJsonString()
        return twirp("ComicDetail", body, "/detail/mc$id", id.toString()).parseAs<ComicDto>()
    }

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val epId = chapter.url.substringAfterLast("/").toInt()
        val body = ImageIndexBody(epId, m2).toJsonString()
        val chapterUrl = getChapterUrl(chapter).toHttpUrl()
        val images = twirp("GetImageIndex", body, chapterUrl.toString(), epId.toString()).parseAs<ImageIndexDto>().images
        // The image path rides as the fragment: ImageToken is asked per page, its tokens expire in minutes.
        return images.mapIndexed { i, image -> Page(i, url = chapterUrl.newBuilder().fragment(image.path).build().toString()) }
    }

    override suspend fun getImageUrl(page: Page): String {
        val pageUrl = page.url.toHttpUrl()
        val urls = listOf(pageUrl.fragment + preferences.getString(IMAGE_QUALITY_PREF, IMAGE_QUALITY_DEFAULT)).toJsonString()
        val body = ImageTokenBody(urls, m1).toJsonString()
        // The urls field doubles as ImageToken's id field for the response key.
        val token = twirp("ImageToken", body, pageUrl.newBuilder().fragment(null).build().toString(), urls)
            .parseAs<List<ImageTokenDto>>().single()
        return token.completeUrl.toHttpUrl().newBuilder().addQueryParameter("code", "DanmakuInfo").build().toString()
    }

    override val supportsFilterFetching = true

    override suspend fun fetchFilterData(): JsonElement = twirp("AllLabel", "{}", "/classify")

    override fun getFilterList(data: JsonElement?): FilterList {
        val labels = data?.parseAs<LabelsDto>() ?: return FilterList()
        return FilterList(
            Filter.Header("搜索文字时忽略筛选"),
            OrderFilter(labels.orders),
            StyleFilter(labels.styles),
            AreaFilter(labels.areas),
            StatusFilter(labels.status),
            PriceFilter(labels.prices),
            SpecialFilter(labels.special),
        )
    }

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        SwitchPreferenceCompat(screen.context).apply {
            key = SHOW_LOCKED_PREF
            title = "显示付费章节"
            summary = "需购买的章节以 🔒 标记。已购章节在 WebView 登录后可读。"
            setDefaultValue(false)
        }.also(screen::addPreference)

        ListPreference(screen.context).apply {
            key = IMAGE_QUALITY_PREF
            title = "图片质量"
            entries = arrayOf("标准（网页阅读器默认，1100px）", "原图（约 2000px，每页数 MB）")
            entryValues = arrayOf(IMAGE_QUALITY_DEFAULT, "")
            summary = "%s"
            setDefaultValue(IMAGE_QUALITY_DEFAULT)
        }.also(screen::addPreference)
    }

    // keyTail: the request's id field, given for the signed methods, whose responses come encrypted.
    private suspend fun twirp(method: String, body: String, from: String, keyTail: String? = null): JsonElement {
        val buvid3 = buvid3()
        val url = baseUrl.toHttpUrl().newBuilder()
            .addPathSegments("twirp/comic.v1.Comic/$method")
            .addQueryParameter("device", "pc")
            .addQueryParameter("platform", "web")
            .apply { if (keyTail != null) addQueryParameter("ultra_sign", UltraSign.sign(body, Clock.System.now().toEpochMilliseconds())) }
            .addQueryParameter("nov", "27")
            .addQueryParameter("a", "810")
            .build()
        val apiHeaders = headers.newBuilder().apply { if (keyTail != null) set("x-bili-data-sn", DATA_SN) }.build()
        val response = client.post(url, apiHeaders, Fetch.XHR.from(from), body.toRequestBody(JSON_MEDIA_TYPE)).parseAs<ResponseDto>()
        if (response.code != 0) throw Exception(response.msg.ifEmpty { "Error ${response.code}" })
        val encrypted = response.bytesData ?: return response.data!!
        val key = bytesDataKey(buvid3, keyTail!!)
        return Base64.decode(encrypted, Base64.DEFAULT).aesDecrypt(aesCbc(key, key.copyOf(16))).decodeToString().parseAs<JsonElement>()
    }

    // 24 bytes: every other byte of buvid3, the platform, then the end of the request's id field; '=' fills the rest.
    private fun bytesDataKey(buvid3: String, keyTail: String): ByteArray {
        val key = ByteArray(24) { '='.code.toByte() }
        buvid3.encodeToByteArray().filterIndexed { i, _ -> i % 2 == 0 }.take(18).toByteArray().copyInto(key)
        "web".encodeToByteArray().copyInto(key, 18)
        keyTail.encodeToByteArray().takeLast(3).toByteArray().copyInto(key, 21)
        return key
    }

    private val buvidLock = Mutex()

    private suspend fun buvid3(): String = buvidLock.withLock {
        preferences.getString(BUVID3_PREF, null)?.let { return it }
        val buvid = client.get(BUVID_URL, Fetch.XHR.from(baseUrl)).parseAs<BuvidDto>().data
        preferences.edit().putString(BUVID3_PREF, buvid.buvid3).putString(BUVID4_PREF, buvid.buvid4).apply()
        buvid.buvid3
    }

    // The reader's P-256 key; the server encrypts images for its public key (m1).
    private val keyPair by lazy {
        KeyPairGenerator.getInstance("EC").apply { initialize(ECGenParameterSpec("secp256r1")) }.generateKeyPair()
    }

    private val m1 by lazy {
        val point = (keyPair.public as ECPublicKey).w
        val raw = byteArrayOf(4) + point.affineX.toFixedBytes() + point.affineY.toFixedBytes()
        Base64.encodeToString(raw, Base64.NO_WRAP)
    }

    private fun BigInteger.toFixedBytes() = toByteArray().takeLast(32).toByteArray().let { ByteArray(32 - it.size) + it }

    // The reader's fingerprint token, from the fields of a plain desktop browser.
    private val m2: String get() {
        val fingerprint = buildJsonObject {
            put("bilibili_0", 1)
            put("bilibili_16", "Error|at g|at encodeM2Sync|at Object.generate")
            put("bilibili_17", listOf(headers["User-Agent"], "zh-CN,zh", "x1920y1080c24p24", "8", "8", "Google Inc.", "Win32", "#", "#", "Asia/Shanghai", "false", "false", "#", "1920x969", "false", "#", "#", "2", "0", "0", "0").joinToString("|"))
            put("bilibili_18", "C.de.1|C.ga.1|C.pa.1|C.ca.1|H.cm.1|H.tb.1|H.tL.1|H.tn.1|H.gt.1|O.tp.1|O.tb.1|O.cm.1|O.gt.1|O.de.1|O.ga.1|O.pa.1|O.ca.1|W.rs.1|W.gr.1|W.gn.1|W2.rs.1|W2.gr.1|W2.gn.1|U.cL.1|U.rL.1|w.Bb.1|H.ss.1|H.sg.1")
            put("bilibili_19", "r|v")
        }
        // Base64 over the reader's own alphabet, 'z' for padding.
        return Base64.encodeToString(fingerprint.toString().encodeToByteArray(), Base64.NO_WRAP)
            .map { if (it == '=') 'z' else M2_ALPHABET[BASE64_ALPHABET.indexOf(it)] }
            .joinToString("")
    }

    companion object {
        private const val PAGE_SIZE = 15
        private const val BUVID_URL = "https://api.bilibili.com/x/frontend/finger/spi"
        private const val DATA_SN = "1E74C20E5720FBF3BB351965D7A9DFC1"
        private const val BUVID3_PREF = "buvid3"
        private const val BUVID4_PREF = "buvid4"
        private const val SHOW_LOCKED_PREF = "showLockedChapters"
        private const val IMAGE_QUALITY_PREF = "imageQuality"
        private const val IMAGE_QUALITY_DEFAULT = "@1100w.avif"
        private const val BASE64_ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/"
        private const val M2_ALPHABET = "ZBCDe0WGH1IJ9Kl3MNo2PQRstuVF+/=XY5Aab4cdEfghij6kLmnOpq7rSTU8vwxyz"
        private val COMIC_SEGMENT = Regex("""mc(\d+)""")
    }
}
