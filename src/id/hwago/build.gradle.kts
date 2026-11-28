import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Hwago"
    contentWarning = ContentWarning.MIXED
    libVersion = "1.6"
    theme = "hwalumi"

    source {
        lang = "id"
        baseUrl = "https://01.hwago.com"
    }
}
