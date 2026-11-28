import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "GTO The Great Site"
    contentWarning = ContentWarning.SAFE
    libVersion = "1.6"
    theme = "pizzareader"

    source {
        lang = "it"
        baseUrl = "https://reader.gtothegreatsite.net"
        versionId = 2
    }
}
