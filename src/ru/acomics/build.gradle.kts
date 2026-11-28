import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "AComics"
    contentWarning = ContentWarning.MIXED
    libVersion = "1.6"

    source {
        lang = "ru"
        baseUrl = "https://acomics.ru"
    }
}
