import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "XYZ Comics"
    contentWarning = ContentWarning.NSFW
    libVersion = "1.6"

    source {
        name = "XYZ Comics"
        lang = "en"
        baseUrl = "https://xyzcomics.com"
    }
}
