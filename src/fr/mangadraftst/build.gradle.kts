import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Mangadraft (unoriginal)"
    contentWarning = ContentWarning.MIXED
    libVersion = "1.6"
    theme = "crunchyscanst"

    source {
        lang = "fr"
        baseUrl = "https://mangadraft.st"
    }
}
