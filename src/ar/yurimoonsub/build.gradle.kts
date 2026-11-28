import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Yuri Moon Sub"
    contentWarning = ContentWarning.NSFW
    libVersion = "1.6"
    theme = "zeistmanga"

    source {
        lang = "ar"
        baseUrl = "https://yurimoonsub.blogspot.com"
    }
}
