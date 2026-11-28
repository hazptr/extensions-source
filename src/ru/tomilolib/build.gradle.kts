import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Tomilo-lib"
    contentWarning = ContentWarning.MIXED
    libVersion = "1.6"

    source {
        lang = "ru"
        baseUrl = "https://tomilo-lib.ru"
    }
}
