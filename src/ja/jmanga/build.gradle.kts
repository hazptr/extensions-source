import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Jmanga"
    contentWarning = ContentWarning.MIXED
    libVersion = "1.6"
    theme = "mangareader"

    source {
        lang = "ja"
        baseUrl {
            custom("https://jmanga.cloud")
        }
    }
}
