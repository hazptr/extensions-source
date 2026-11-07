package keiyoushi.source

import keiyoushi.source.ChapterUploadPreferences.Mode
import org.junit.Assert.assertEquals
import org.junit.Test

class ChapterUploadPreferencesTest {
    private class Upload(
        val id: String,
        val number: Int,
        val group: String?,
        val votes: Int = 0,
        val partial: Boolean = false,
        val official: Boolean = false,
    )

    private val uploads = listOf(
        Upload("2a", 2, "Alpha", votes = 1),
        Upload("2b", 2, "Beta", votes = 5),
        Upload("2a2", 2, "alpha ", votes = 9),
        Upload("2o", 2, "VIZ", votes = 0, official = true),
        Upload("1b", 1, "Beta", partial = true, votes = 9),
        Upload("1a", 1, "Alpha"),
        Upload("1n", 1, null),
        Upload("1n2", 1, null),
    )

    private fun select(
        mode: Mode,
        preferred: List<String> = emptyList(),
        blocked: List<String> = emptyList(),
        list: List<Upload> = uploads,
    ) = selectUploads(
        list,
        mode,
        preferred,
        blocked,
        chapter = { it.number },
        groups = { listOf(it.group, "Official".takeIf { _ -> it.official }) },
        complete = { !it.partial },
        rank = compareByDescending { it.votes },
    ).map { it.id }

    @Test
    fun `all uploads keeps everything in order`() {
        assertEquals(uploads.map { it.id }, select(Mode.ALL_UPLOADS))
    }

    @Test
    fun `blocked groups are left out in every mode, trimmed and case-insensitive`() {
        Mode.entries.forEach { mode ->
            assertEquals(mode.name, emptyList<String>(), select(mode, blocked = listOf(" ALPHA")).filter { it.startsWith("2a") || it == "1a" })
        }
    }

    @Test
    fun `one per group keeps the best ranked copy of a group`() {
        assertEquals(listOf("2b", "2a2", "2o", "1b", "1a", "1n"), select(Mode.ONE_PER_GROUP))
    }

    @Test
    fun `one per chapter without preferred groups goes by completeness then rank`() {
        assertEquals(listOf("2a2", "1a"), select(Mode.ONE_PER_CHAPTER))
    }

    @Test
    fun `one per chapter takes the first preferred group that has the chapter`() {
        assertEquals(listOf("2b", "1a"), select(Mode.ONE_PER_CHAPTER, preferred = listOf("Beta", "Alpha")))
    }

    @Test
    fun `a complete upload beats a preferred partial one`() {
        assertEquals(listOf("2b", "1a"), select(Mode.ONE_PER_CHAPTER, preferred = listOf("beta")))
    }

    @Test
    fun `preferred Official matches uploads marked official`() {
        assertEquals(listOf("2o", "1a"), select(Mode.ONE_PER_CHAPTER, preferred = listOf("Official")))
    }

    @Test
    fun `preferred official name matches by name`() {
        assertEquals(listOf("2o", "1a"), select(Mode.ONE_PER_CHAPTER, preferred = listOf("viz")))
    }

    @Test
    fun `kept uploads stay in their order`() {
        val list = listOf(Upload("5a", 5, "A"), Upload("4a", 4, "A"), Upload("5b", 5, "B", votes = 3))
        assertEquals(listOf("4a", "5b"), select(Mode.ONE_PER_CHAPTER, list = list))
    }

    @Test
    fun `ties keep the first upload`() {
        val tied = listOf(Upload("x", 1, "A"), Upload("y", 1, "B"))
        assertEquals(listOf("x"), select(Mode.ONE_PER_CHAPTER, list = tied))
    }
}
