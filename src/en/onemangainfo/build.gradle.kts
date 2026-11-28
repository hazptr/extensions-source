import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "OneManga.info"
    contentWarning = ContentWarning.MIXED
    libVersion = "1.6"
    theme = "mangahub"

    source {
        lang = "en"
        baseUrl = "https://onemanga.info"
        versionId = 2
    }
}
