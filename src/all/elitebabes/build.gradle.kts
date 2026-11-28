import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Elite Babes"
    contentWarning = ContentWarning.NSFW
    libVersion = "1.6"
    theme = "masonry"

    source {
        lang = "all"
        baseUrl = "https://www.elitebabes.com"
    }
}
