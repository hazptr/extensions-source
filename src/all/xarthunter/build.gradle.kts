import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "XArt Hunter"
    contentWarning = ContentWarning.NSFW
    libVersion = "1.6"
    theme = "masonry"

    source {
        lang = "all"
        baseUrl = "https://www.xarthunter.com"
    }
}
