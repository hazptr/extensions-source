import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "TuttoAnimeManga"
    contentWarning = ContentWarning.SAFE
    libVersion = "1.6"
    theme = "pizzareader"

    source {
        lang = "it"
        baseUrl = "https://tuttoanimemanga.net"
    }
}
