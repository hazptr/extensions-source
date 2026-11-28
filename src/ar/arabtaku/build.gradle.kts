import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Arabtaku"
    contentWarning = ContentWarning.NSFW
    libVersion = "1.6"
    theme = "mangathemesia"

    source {
        lang = "ar"
        baseUrl = "https://arabtaku.com"
    }
}
