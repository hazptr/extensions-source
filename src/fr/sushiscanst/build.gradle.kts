import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Sushiscan (unoriginal)"
    contentWarning = ContentWarning.MIXED
    libVersion = "1.6"
    theme = "crunchyscanst"

    source {
        lang = "fr"
        baseUrl = "https://sushiscan.st"
    }
}
