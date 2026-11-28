import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Les Poroiniens"
    contentWarning = ContentWarning.MIXED
    libVersion = "1.6"

    source {
        lang = "fr"
        baseUrl = "https://lesporoiniens.org"
    }
}
