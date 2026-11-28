import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Zerosum Online"
    contentWarning = ContentWarning.SAFE
    libVersion = "1.6"

    source {
        lang = "ja"
        baseUrl = "https://zerosumonline.com"
    }
}
