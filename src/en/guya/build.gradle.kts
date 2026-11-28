import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Guya"
    contentWarning = ContentWarning.SAFE
    libVersion = "1.6"
    theme = "guya"

    source {
        lang = "en"
        baseUrl = "https://guya.cubari.moe"
    }
}
