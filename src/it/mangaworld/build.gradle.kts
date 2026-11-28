import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Mangaworld"
    contentWarning = ContentWarning.SAFE
    libVersion = "1.6"
    theme = "mangaworld"

    source {
        lang = "it"
        baseUrl = "https://www.mangaworld.me"
    }
}
