import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "SeiManga"
    contentWarning = ContentWarning.MIXED
    libVersion = "1.6"
    theme = "grouple"

    source {
        baseUrl {
            custom("https://1.seimanga.me")
        }
        lang = "ru"
    }
}
