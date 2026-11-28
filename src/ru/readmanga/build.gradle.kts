import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "ReadManga"
    contentWarning = ContentWarning.SAFE
    libVersion = "1.6"
    theme = "grouple"

    source {
        baseUrl {
            custom("https://b.zazaza.me")
        }
        lang = "ru"
        id = 5L
    }
}
