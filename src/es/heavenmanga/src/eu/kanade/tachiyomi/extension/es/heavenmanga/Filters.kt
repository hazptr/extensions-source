@file:Suppress("SpellCheckingInspection")

package eu.kanade.tachiyomi.extension.es.heavenmanga

import eu.kanade.tachiyomi.source.model.Filter

open class UriPartFilter(displayName: String, val vals: Array<Pair<String, String>>) : Filter.Select<String>(displayName, vals.map { it.first }.toTypedArray()) {
    fun toUriPart() = vals[state].second
}

class GenreFilter(genres: List<GenreDto>) :
    UriPartFilter(
        "Géneros",
        (listOf("<Seleccionar>" to "") + genres.map { it.name to it.slug }).toTypedArray(),
    )

class AlphabeticoFilter :
    UriPartFilter(
        "Alfabético",
        arrayOf(
            "<Seleccionar>" to "",
            "Other" to "Other",
            "A" to "a",
            "B" to "b",
            "C" to "c",
            "D" to "d",
            "E" to "e",
            "F" to "f",
            "G" to "g",
            "H" to "h",
            "I" to "i",
            "J" to "j",
            "K" to "k",
            "L" to "l",
            "M" to "m",
            "N" to "n",
            "O" to "o",
            "P" to "p",
            "Q" to "q",
            "R" to "r",
            "S" to "s",
            "T" to "t",
            "U" to "u",
            "V" to "v",
            "W" to "w",
            "X" to "x",
            "Y" to "y",
            "Z" to "z",
            "0-9" to "0-9",
        ),
    )

class ListaCompletasFilter :
    UriPartFilter(
        "Lista Completa",
        arrayOf(
            "<Seleccionar>" to "",
            "Lista Comis" to "comic",
            "Lista Manhwa" to "manhwa",
            "Lista Manhua" to "manhua",
            "Lista Adulto" to "adulto",
        ),
    )
