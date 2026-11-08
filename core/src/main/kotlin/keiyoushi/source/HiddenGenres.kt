package keiyoushi.source

import eu.kanade.tachiyomi.AppInfo

/**
 * The genres the user hides app wide (`AppInfo.getHiddenGenres`), matched against a site's own genres. A hidden name
 * stands for every spelling in its [SYNONYMS] group. On an app without the setting nothing is hidden.
 *
 * ```kotlin
 * val hidden = HiddenGenres.select(siteGenres) { it.name }
 * ```
 */
object HiddenGenres {
    /**
     * Genres sites spell in more than one way. A source adds its own spellings (another language, a site-specific
     * name) through `extra`, keyed by a group's first entry.
     */
    val SYNONYMS: List<List<String>> = listOf(
        listOf("Boys' Love", "BL", "Yaoi", "Shounen Ai", "Shonen Ai", "Danmei"),
        listOf("Girls' Love", "GL", "Yuri", "Shoujo Ai", "Shojo Ai"),
        listOf("Gender Bender", "Genderswap", "Gender Swap"),
        listOf("Gore", "Guro"),
        listOf("Loli", "Lolicon"),
        listOf("Shota", "Shotacon"),
        listOf("NTR", "Netorare"),
        listOf("Mecha", "Mechs"),
        listOf("Sci-Fi", "Science Fiction"),
        listOf("Sports", "Sport"),
        listOf("Shounen", "Shonen"),
        listOf("Shoujo", "Shojo"),
    )

    /** The genre names the user hides, as typed. */
    val names: Set<String>
        get() = try {
            AppInfo.getHiddenGenres()
        } catch (_: LinkageError) {
            emptySet()
        }

    /**
     * Every spelling of the hidden genres, for sites that exclude by name and ignore names they don't have. Needs no
     * genre list. A hidden genre with a spelling in [included] is left out, so a search that asks for it wins.
     */
    fun spellings(extra: Map<String, List<String>> = emptyMap(), included: Collection<String> = emptyList()): Set<String> {
        val hidden = names
        if (hidden.isEmpty()) return emptySet()
        val groups = SYNONYMS.map { it + extra[it.first()].orEmpty() }
        val wanted = included.mapTo(HashSet(), ::normalize)
        return hidden.flatMapTo(LinkedHashSet()) { name ->
            val group = groups.firstOrNull { group -> group.any { normalize(it) == normalize(name) } } ?: listOf(name)
            if (group.any { normalize(it) in wanted }) emptyList() else group
        }
    }

    /** The entries of [genres] whose name is a spelling of a hidden genre. Case, spaces and punctuation are ignored. */
    fun <T> select(genres: Iterable<T>, extra: Map<String, List<String>> = emptyMap(), name: (T) -> String): List<T> {
        val hidden = spellings(extra).mapTo(HashSet(), ::normalize)
        if (hidden.isEmpty()) return emptyList()
        return genres.filter { normalize(name(it)) in hidden }
    }

    private fun normalize(name: String) = name.lowercase().filter(Char::isLetterOrDigit)
}
