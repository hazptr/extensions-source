import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Hentai Lek"
    contentWarning = ContentWarning.NSFW
    libVersion = "1.6"

    source {
        name = "هنتاي ليك"
        lang = "ar"
        baseUrl = "https://hentailek.com"
    }
}
