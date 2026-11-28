import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "SelfManga"
    contentWarning = ContentWarning.SAFE
    libVersion = "1.6"
    theme = "grouple"

    source {
        baseUrl {
            custom("https://1.selfmanga.live")
        }
        lang = "ru"
        id = 5227602742162454547L
    }
}
