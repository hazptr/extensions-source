import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Minda Fansub"
    theme = "madara"
    contentWarning = ContentWarning.NSFW
    libVersion = "1.6"

    source {
        baseUrl {
            custom("https://mindafansub.nl")
        }
        lang = "tr"
    }
}
