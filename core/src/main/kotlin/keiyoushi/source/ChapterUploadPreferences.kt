package keiyoushi.source

import android.content.SharedPreferences
import androidx.preference.EditTextPreference
import androidx.preference.ListPreference
import androidx.preference.PreferenceScreen

/**
 * The chapter list settings for a site that lists several uploads of a chapter (other groups, official and
 * scans, re-uploads): which uploads to list, the groups "One per chapter" prefers, and the groups left out.
 *
 * Add the settings with [setupPreferenceScreen] and pass the site's uploads through [select].
 *
 * @param strings the settings' text: pass the source's `chapterUploadStrings`, generated per language from
 * core/translations
 * @param onePerGroup the site can list several uploads of a chapter from one group, so "One per group" is
 * offered and is the default; otherwise the default lists every upload
 * @param defaultPreferredGroups the site's official groups as it names them, or "Official" when the site
 * marks its official uploads instead (see [select])
 */
class ChapterUploadPreferences(
    private val preferences: SharedPreferences,
    private val strings: Strings,
    private val onePerGroup: Boolean = false,
    private val defaultPreferredGroups: List<String> = emptyList(),
) {
    enum class Mode(internal val value: String) {
        ALL_UPLOADS("all"),
        ONE_PER_GROUP("group"),
        ONE_PER_CHAPTER("chapter"),
    }

    private val modes = listOfNotNull(Mode.ALL_UPLOADS, Mode.ONE_PER_GROUP.takeIf { onePerGroup }, Mode.ONE_PER_CHAPTER)

    private val defaultMode = if (onePerGroup) Mode.ONE_PER_GROUP else Mode.ALL_UPLOADS

    val mode: Mode
        get() {
            val value = preferences.getString(MODE_KEY, null)
            return modes.firstOrNull { it.value == value } ?: defaultMode
        }

    private val preferredGroups: List<String>
        get() = preferences.getString(PREFERRED_GROUPS_KEY, defaultPreferredGroups.joinToString())!!.groupList()

    private val blockedGroups: List<String>
        get() = preferences.getString(BLOCKED_GROUPS_KEY, "")!!.groupList()

    /** Whether any of [groups] is blocked */
    fun isBlocked(groups: List<String?>): Boolean {
        val blocked = blockedGroups.toSet()
        return groups.any { it?.normalized() in blocked }
    }

    /**
     * The uploads the chapter list shows, in their order in [uploads], with blocked groups left out.
     * Among a chapter's uploads (or a group's, for "One per group") the kept one is: a [complete] one, then
     * for "One per chapter" the first preferred group, then the best by [rank], then the first in [uploads].
     *
     * @param chapter what an upload is a copy of: chapter number, volume and number, or the site's row
     * @param groups the names the upload is matched by, case-insensitive: its group first ("One per group"
     * goes by it, and uploads without one count as one group), then any other name or id the site gives it.
     * Add "Official" for an upload the site marks as official
     * @param complete false for partial, preview or flagged uploads, which are only kept when nothing else is
     * @param rank the site's own order for uploads of the same chapter: votes, likes, score or the site's pick
     */
    fun <T> select(
        uploads: List<T>,
        chapter: (T) -> Any,
        groups: (T) -> List<String?>,
        complete: (T) -> Boolean = { true },
        rank: Comparator<in T> = Comparator { _, _ -> 0 },
    ): List<T> = selectUploads(uploads, mode, preferredGroups, blockedGroups, chapter, groups, complete, rank)

    fun setupPreferenceScreen(screen: PreferenceScreen) {
        val preferred = EditTextPreference(screen.context).apply {
            key = PREFERRED_GROUPS_KEY
            title = strings.preferredGroups
            dialogTitle = title
            summary = strings.preferredGroupsSummary +
                defaultPreferredGroups.takeIf { it.isNotEmpty() }?.let { " " + strings.defaultValue.format(it.joinToString()) }.orEmpty()
            setDefaultValue(defaultPreferredGroups.joinToString())
            setEnabled(mode == Mode.ONE_PER_CHAPTER)
        }

        ListPreference(screen.context).apply {
            key = MODE_KEY
            title = strings.chapterList
            entries = modes.map {
                when (it) {
                    Mode.ALL_UPLOADS -> strings.chapterListAllUploads
                    Mode.ONE_PER_GROUP -> strings.chapterListOnePerGroup
                    Mode.ONE_PER_CHAPTER -> strings.chapterListOnePerChapter
                }
            }.toTypedArray()
            entryValues = modes.map { it.value }.toTypedArray()
            summary = "%s"
            setDefaultValue(defaultMode.value)
            setOnPreferenceChangeListener { _, newValue ->
                preferred.setEnabled(newValue == Mode.ONE_PER_CHAPTER.value)
                true
            }
        }.also(screen::addPreference).apply {
            // A value saved by the source's own earlier setting that this list doesn't offer
            if (value !in entryValues) value = defaultMode.value
        }

        screen.addPreference(preferred)

        EditTextPreference(screen.context).apply {
            key = BLOCKED_GROUPS_KEY
            title = strings.blockedGroups
            dialogTitle = title
            summary = strings.blockedGroupsSummary
            setDefaultValue("")
        }.let(screen::addPreference)
    }

    companion object {
        private const val MODE_KEY = "pref_chapter_list"
        private const val PREFERRED_GROUPS_KEY = "pref_preferred_groups"
        private const val BLOCKED_GROUPS_KEY = "pref_blocked_groups"

        /**
         * Moves a source's own earlier chapter list settings to these. A dedupe switch that was on becomes
         * "One per chapter". Call it from the `getPreferences` migration block.
         */
        fun migrate(
            preferences: SharedPreferences,
            dedupeSwitchKey: String? = null,
            preferredGroupsKey: String? = null,
            blockedGroupsKey: String? = null,
        ) {
            val editor = preferences.edit()
            dedupeSwitchKey?.takeIf(preferences::contains)?.let {
                if (preferences.getBoolean(it, false)) editor.putString(MODE_KEY, Mode.ONE_PER_CHAPTER.value)
                editor.remove(it)
            }
            listOfNotNull(preferredGroupsKey to PREFERRED_GROUPS_KEY, blockedGroupsKey to BLOCKED_GROUPS_KEY).forEach { (old, new) ->
                if (old == null || !preferences.contains(old)) return@forEach
                editor.putString(new, preferences.getString(old, ""))
                editor.remove(old)
            }
            editor.apply()
        }
    }

    class Strings(
        val chapterList: String,
        val chapterListAllUploads: String,
        val chapterListOnePerGroup: String,
        val chapterListOnePerChapter: String,
        val preferredGroups: String,
        val preferredGroupsSummary: String,
        val blockedGroups: String,
        val blockedGroupsSummary: String,
        val defaultValue: String,
    )
}

private fun String.normalized() = trim().lowercase()

private fun String.groupList() = split(',').map { it.normalized() }.filter(String::isNotEmpty)

internal fun <T> selectUploads(
    uploads: List<T>,
    mode: ChapterUploadPreferences.Mode,
    preferredGroups: List<String>,
    blockedGroups: List<String>,
    chapter: (T) -> Any,
    groups: (T) -> List<String?>,
    complete: (T) -> Boolean,
    rank: Comparator<in T>,
): List<T> {
    val blocked = blockedGroups.map { it.normalized() }.toSet()
    val shown = uploads.filterNot { upload -> groups(upload).any { it?.normalized() in blocked } }
    val preferred = preferredGroups.map { it.normalized() }
    val preferredRank = { upload: T ->
        val names = groups(upload).mapNotNull { it?.normalized() }
        preferred.indexOfFirst { it in names }.takeIf { it >= 0 } ?: preferred.size
    }
    val byCompleteness = compareBy<IndexedValue<T>> { !complete(it.value) }
    val byRank = compareBy<IndexedValue<T>, T>(rank) { it.value }
    val kept = when (mode) {
        ChapterUploadPreferences.Mode.ALL_UPLOADS -> return shown
        ChapterUploadPreferences.Mode.ONE_PER_GROUP -> shown.withIndex()
            .groupBy { chapter(it.value) to groups(it.value).firstOrNull()?.normalized() }
            .values.mapTo(HashSet()) { same -> same.minWith(byCompleteness.then(byRank)).index }
        ChapterUploadPreferences.Mode.ONE_PER_CHAPTER -> shown.withIndex()
            .groupBy { chapter(it.value) }
            .values.mapTo(HashSet()) { same -> same.minWith(byCompleteness.thenBy { preferredRank(it.value) }.then(byRank)).index }
    }
    return shown.filterIndexed { index, _ -> index in kept }
}
