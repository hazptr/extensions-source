package eu.kanade.tachiyomi.extension.ja.rawfree

import eu.kanade.tachiyomi.source.model.Filter

class GenreFilter(private val genres: List<Pair<String, String>>) : Filter.Select<String>("ジャンル", (listOf("すべて") + genres.map { it.first }).toTypedArray()) {
    val slug get() = if (state == 0) null else genres[state - 1].second
}
