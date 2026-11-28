import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Hasta Team"
    contentWarning = ContentWarning.SAFE
    libVersion = "1.6"
    theme = "pizzareader"

    source {
        lang = "it"
        baseUrl = "https://reader.hastateam.com"
    }
}
