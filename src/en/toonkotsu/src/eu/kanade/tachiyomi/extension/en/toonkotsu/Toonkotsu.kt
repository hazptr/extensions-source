package eu.kanade.tachiyomi.extension.en.toonkotsu

import android.util.Base64
import androidx.preference.EditTextPreference
import androidx.preference.ListPreference
import androidx.preference.PreferenceScreen
import androidx.preference.SwitchPreferenceCompat
import eu.kanade.tachiyomi.network.HttpException
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import keiyoushi.annotation.Source
import keiyoushi.network.get
import keiyoushi.network.post
import keiyoushi.source.KeiSource
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.getTurnstileToken
import keiyoushi.utils.parseAs
import keiyoushi.utils.toJsonElement
import keiyoushi.utils.toJsonRequestBody
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.JsonElement
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Response
import okhttp3.ResponseBody.Companion.asResponseBody
import okio.buffer
import okio.cipherSource
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

@Source
abstract class Toonkotsu :
    KeiSource(),
    ConfigurableSource {

    private val preferences by getPreferencesLazy {
        if (contains(PREF_DEDUPLICATE_CHAPTERS)) {
            val editor = edit().remove(PREF_DEDUPLICATE_CHAPTERS)
            if (getBoolean(PREF_DEDUPLICATE_CHAPTERS, false)) editor.putString(PREF_CHAPTER_LIST, ONE_PER_CHAPTER)
            editor.apply()
        }
    }

    override val supportsFilterFetching = true

    override val supportsRelatedMangas = true

    override fun OkHttpClient.Builder.configureClient(): OkHttpClient.Builder = addInterceptor(::decryptImage)

    override suspend fun getPopularManga(page: Int): MangasPage = browse(page, "popular")

    override suspend fun getLatestUpdates(page: Int): MangasPage = browse(page, "latest")

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage = browse(page, "relevance") {
        if (query.isNotBlank()) addQueryParameter("q", query.trim())
        filters.forEach { filter ->
            when (filter) {
                is SortFilter -> {
                    setQueryParameter("sort", filter.value)
                    setQueryParameter("order", filter.order)
                }
                is SelectFilter -> if (filter.value.isNotEmpty()) setQueryParameter(filter.param, filter.value)
                is GenreFilter -> {
                    filter.state.filter { it.isIncluded() }.takeIf { it.isNotEmpty() }
                        ?.let { genres -> addQueryParameter("genres", genres.joinToString(",") { it.name }) }
                    filter.state.filter { it.isExcluded() }.takeIf { it.isNotEmpty() }
                        ?.let { genres -> addQueryParameter("exclude", genres.joinToString(",") { it.name }) }
                }
                else -> {}
            }
        }
    }

    private suspend fun browse(page: Int, sort: String, params: HttpUrl.Builder.() -> Unit = {}): MangasPage {
        val url = "$baseUrl/cdn/v1/browse".toHttpUrl().newBuilder().apply {
            addQueryParameter("sort", sort)
            addQueryParameter("order", "desc")
            addQueryParameter("page", page.toString())
            addQueryParameter("limit", "42")
            if (!preferences.getBoolean(PREF_ADULT, false)) addQueryParameter("sfw", "1")
            params()
        }.build()
        val data = client.get(url).parseAs<BrowseDto>()
        return MangasPage(data.items.map { it.toSManga(baseUrl) }, data.hasNextPage)
    }

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host.removePrefix("www.") != baseUrl.toHttpUrl().host.removePrefix("www.") || url.pathSegments.firstOrNull() != "title") return null
        val hid = url.pathSegments.getOrNull(1)?.let { HID_REGEX.find(it) }?.value ?: return null
        val manga = SManga.create().apply { this.url = "/title/$hid" }
        return getMangaUpdate(manga, emptyList(), fetchDetails = true, fetchChapters = false).manga
    }

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val data = fetchSeries(manga)
        val allUploads = data.chapters.flatMap { it.withAlts() }
        // The site splits rows by volume when 3+ chapter numbers span several volumes
        val byVolume = allUploads.filter { it.n != null && it.numericVolume != null }
            .groupBy({ it.n }, { it.numericVolume })
            .count { (_, volumes) -> volumes.distinct().size > 1 } >= 3
        val hiddenGroups = commaList(PREF_HIDDEN_GROUPS)
        val uploads = allUploads.filterNot { upload -> hiddenGroups.any { upload.isFromSource(it) || upload.isFromGroup(it) } }
        val preferredSources = commaList(PREF_PREFERRED_SOURCES)
        val preferredGroups = commaList(PREF_PREFERRED_GROUPS)
        val sourceRank = { upload: ChapterDto -> preferredSources.rankOf(upload::isFromSource) }
        val groupRank = { upload: ChapterDto -> preferredGroups.rankOf(upload::isFromGroup) }
        // One upload per row, in the site's order; preferred sources sit where the site's own preferred source does
        val picks = uploads.sortedWith(compareBy({ it.partial }, sourceRank, { it.parentId != null }))
            .distinctBy { it.row(byVolume) }
            .toSet()
        val chapterList = when (preferences.getString(PREF_CHAPTER_LIST, ONE_PER_GROUP)) {
            ONE_PER_GROUP -> {
                val complete = picks.filterNot { it.partial }.mapTo(HashSet()) { it.row(byVolume, byGroup = false) }
                // The site hides a partial-only row when another group has the full chapter
                uploads.filter { it in picks && (!it.partial || it.row(byVolume, byGroup = false) !in complete) }
            }
            ONE_PER_CHAPTER ->
                uploads.filter { it in picks }
                    .groupBy { it.chapterKey }
                    .map { (_, same) -> same.sortedWith(compareBy({ it.partial }, groupRank, { !it.isOfficial })).first() }
            else -> uploads
        }
        return SMangaUpdate(
            data.series.toSManga(baseUrl),
            chapterList.map { it.toSChapter(manga.url, withSourceCode = it !in picks) },
        )
    }

    private fun commaList(key: String) = preferences.getString(key, null).orEmpty().split(',').map(String::trim).filter(String::isNotEmpty)

    private fun List<String>.rankOf(matches: (String) -> Boolean) = indexOfFirst(matches).takeIf { it >= 0 } ?: size

    override suspend fun fetchRelatedMangaList(manga: SManga): List<SManga> = fetchSeries(manga).recs.map { it.toSManga(baseUrl) }

    private suspend fun fetchSeries(manga: SManga): SeriesResponse {
        val hid = getMangaUrl(manga).toHttpUrl().pathSegments[1]
        return client.get("$baseUrl/cdn/v1/series/$hid").parseAs()
    }

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val chapterUrl = getChapterUrl(chapter)
        val id = chapterUrl.toHttpUrl().pathSegments.last()
        return fetchChapter(id, chapterUrl).chapter.pages.mapIndexed { index, page ->
            Page(index, imageUrl = "$baseUrl${page.src}#${page.k}.${page.n}")
        }
    }

    private suspend fun fetchChapter(id: String, chapterUrl: String, unlocked: Boolean = false): ChapterResponse {
        val unlocksBefore = unlocks
        val response = client.get("$baseUrl/api/manga/chapter/$id", ensureSuccess = false)
        if (response.isSuccessful) return response.parseAs()
        if (response.code != 429) {
            response.close()
            throw HttpException(response.code)
        }
        val limit = response.parseAs<LimitDto>()
        if (unlocked || limit.code != "manga-limit" || limit.reason != "verify") {
            throw Exception(limit.error ?: "HTTP 429")
        }
        unlock(chapterUrl, unlocksBefore)
        return fetchChapter(id, chapterUrl, unlocked = true)
    }

    private val unlockMutex = Mutex()

    @Volatile
    private var unlocks = 0

    // Site's "Confirm you're human" reader check
    private suspend fun unlock(chapterUrl: String, unlocksBefore: Int) = unlockMutex.withLock {
        if (unlocks != unlocksBefore) return@withLock
        val token = getTurnstileToken(url = chapterUrl, siteKey = TURNSTILE_SITE_KEY)
        client.post("$baseUrl/api/manga/unlock", body = UnlockBody(token).toJsonRequestBody()).close()
        unlocks++
    }

    private fun decryptImage(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val fragment = request.url.fragment
        if (fragment == null || !request.url.encodedPath.startsWith("/cdn/e/")) return chain.proceed(request)

        val response = chain.proceed(request)
        if (!response.isSuccessful) return response

        val key = Base64.decode(fragment.substringBefore("."), Base64.URL_SAFE)
        val nonce = Base64.decode(fragment.substringAfter("."), Base64.URL_SAFE)
        val cipher = Cipher.getInstance("AES/CTR/NoPadding").apply {
            init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), IvParameterSpec(nonce.copyOf(16)))
        }
        val body = response.body.source().cipherSource(cipher).buffer()
            .asResponseBody(response.body.contentType(), response.body.contentLength())
        return response.newBuilder().body(body).build()
    }

    override suspend fun fetchFilterData(): JsonElement = client.get("$baseUrl/cdn/v1/facets?sfw=0").parseAs<FacetsDto>().toJsonElement()

    override fun getFilterList(data: JsonElement?): FilterList {
        val facets = data?.parseAs<FacetsDto>()
        return FilterList(
            listOfNotNull(
                SortFilter(),
                facets?.let { TypeFilter(it.types.map(FacetDto::name)) },
                StatusFilter(),
                DemographicFilter(),
                facets?.let { GenreFilter(it.genres.map(FacetDto::name).filterNot(DEMOGRAPHICS::contains)) },
            ),
        )
    }

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        SwitchPreferenceCompat(screen.context).apply {
            key = PREF_ADULT
            title = "Show 18+ content"
            summary = "Same as the 18+ option in the site's content rating menu"
            setDefaultValue(false)
        }.let(screen::addPreference)

        val preferredGroups = EditTextPreference(screen.context).apply {
            key = PREF_PREFERRED_GROUPS
            title = "Preferred groups"
            summary = "Group names, comma-separated. One per chapter shows the first listed group that has a complete upload"
            dialogTitle = "Preferred groups"
            setDefaultValue("")
            setEnabled(preferences.getString(PREF_CHAPTER_LIST, ONE_PER_GROUP) == ONE_PER_CHAPTER)
        }

        ListPreference(screen.context).apply {
            key = PREF_CHAPTER_LIST
            title = "Chapter list"
            entries = arrayOf("All uploads", "One per group, like the site's list", "One per chapter")
            entryValues = arrayOf(ALL_UPLOADS, ONE_PER_GROUP, ONE_PER_CHAPTER)
            summary = "%s"
            setDefaultValue(ONE_PER_GROUP)
            setOnPreferenceChangeListener { _, newValue ->
                preferredGroups.setEnabled(newValue == ONE_PER_CHAPTER)
                true
            }
        }.let(screen::addPreference)

        screen.addPreference(preferredGroups)

        EditTextPreference(screen.context).apply {
            key = PREF_HIDDEN_GROUPS
            title = "Hidden groups and sources"
            summary = "Group names or source codes, comma-separated. Their uploads are left out of the chapter list"
            dialogTitle = "Hidden groups and sources"
            setDefaultValue("")
        }.let(screen::addPreference)

        EditTextPreference(screen.context).apply {
            key = PREF_PREFERRED_SOURCES
            title = "Preferred sources"
            summary = "Source codes, comma-separated, as shown in parentheses in All uploads. Picks which copy of a chapter a group shows"
            dialogTitle = "Preferred sources"
            setDefaultValue("")
        }.let(screen::addPreference)
    }

    companion object {
        private const val PREF_ADULT = "show_hentai"
        private const val PREF_DEDUPLICATE_CHAPTERS = "pref_deduplicate_chapters"
        private const val PREF_CHAPTER_LIST = "pref_chapter_list"
        private const val PREF_PREFERRED_SOURCES = "pref_preferred_sources"
        private const val PREF_PREFERRED_GROUPS = "pref_preferred_groups"
        private const val PREF_HIDDEN_GROUPS = "pref_hidden_groups"
        private const val ALL_UPLOADS = "all"
        private const val ONE_PER_GROUP = "group"
        private const val ONE_PER_CHAPTER = "chapter"
        private const val TURNSTILE_SITE_KEY = "0x4AAAAAAFQpQeG5qpmLQtf9"
        private val HID_REGEX = Regex("""^[a-z][0-9a-z]{4}(?=-|$)""")
    }
}
