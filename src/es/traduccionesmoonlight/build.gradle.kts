import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Traducciones Moonlight"
    contentWarning = ContentWarning.MIXED
    libVersion = "1.6"
    theme = "moonlighttl"

    source {
        lang = "es"
        baseUrl = "https://traduccionesmoonlight.com"
        versionId = 3
    }
}
