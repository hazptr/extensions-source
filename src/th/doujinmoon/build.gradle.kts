import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Doujin Moon"
    contentWarning = ContentWarning.NSFW
    libVersion = "1.6"
    theme = "mangathemesia"

    source {
        lang = "th"
        baseUrl = "https://doujinmoon.com"
    }
}
