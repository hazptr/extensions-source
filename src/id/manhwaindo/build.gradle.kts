import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Manhwa Indo"
    contentWarning = ContentWarning.MIXED
    libVersion = "1.6"
    theme = "manhwaindo"

    source {
        lang = "id"
        baseUrl = "https://www.manhwaindo.my"
    }
}
