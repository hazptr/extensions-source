import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Read Berserk Manga"
    contentWarning = ContentWarning.SAFE
    libVersion = "1.6"
    theme = "mangacatalog"

    source {
        lang = "en"
        baseUrl = "https://readberserk.com"
    }
}
