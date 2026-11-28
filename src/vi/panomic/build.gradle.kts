import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Panomic"
    contentWarning = ContentWarning.MIXED
    libVersion = "1.6"
    theme = "koi"

    source {
        lang = "vi"
        baseUrl {
            custom("https://panomic.online")
        }
    }
}
