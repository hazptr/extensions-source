import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Natsu"
    contentWarning = ContentWarning.SAFE
    libVersion = "1.6"
    theme = "natsuid"

    source {
        lang = "id"
        baseUrl = "https://natsu.one"
    }
}
