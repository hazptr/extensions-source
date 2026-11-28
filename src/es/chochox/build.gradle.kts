import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "ChoChoX"
    contentWarning = ContentWarning.NSFW
    libVersion = "1.6"
    theme = "vercomics"

    source {
        lang = "es"
        baseUrl = "https://chochox.com"
    }
}
