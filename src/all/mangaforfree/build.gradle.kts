import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "MangaForFree.net"
    contentWarning = ContentWarning.NSFW
    libVersion = "1.6"
    theme = "madara"

    listOf("en", "ko", "all").forEach {
        source {
            lang = it
            baseUrl = "https://mangaforfree.net"
        }
    }
}
