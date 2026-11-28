import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "HentaiFox"
    contentWarning = ContentWarning.NSFW
    libVersion = "1.6"
    theme = "galleryadults"

    listOf("en", "ja", "zh", "ko", "all").forEach { language ->
        source {
            lang = language
            baseUrl = "https://hentaifox.com"
        }
    }
}
