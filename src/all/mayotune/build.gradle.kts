import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "MayoTune"
    contentWarning = ContentWarning.SAFE
    libVersion = "1.6"

    listOf("en", "ja").forEach {
        source {
            lang = it
            baseUrl = "https://mayochuu.xyz"
        }
    }
}
