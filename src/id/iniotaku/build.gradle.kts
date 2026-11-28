import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "IniOtaku"
    contentWarning = ContentWarning.MIXED
    libVersion = "1.6"
    theme = "zeistmanga"

    source {
        lang = "id"
        baseUrl = "https://www.iniotaku2.com"
    }
}
