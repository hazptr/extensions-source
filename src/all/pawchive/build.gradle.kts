import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Pawchive"
    contentWarning = ContentWarning.NSFW
    libVersion = "1.6"
    theme = "kemono"

    source {
        lang = "all"
        baseUrl = "https://pawchive.pw"
    }
}
