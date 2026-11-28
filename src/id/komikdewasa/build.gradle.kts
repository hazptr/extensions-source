import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Komik Dewasa"
    contentWarning = ContentWarning.NSFW
    libVersion = "1.6"
    theme = "mangathemesia"

    source {
        name = "Komik Dewasak"
        lang = "id"
        baseUrl = "https://komikdewasa.mom"
    }
}
