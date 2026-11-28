import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "AralosBD"
    contentWarning = ContentWarning.SAFE
    libVersion = "1.6"

    source {
        lang = "fr"
        baseUrl = "https://aralosbd.fr"
    }
}
