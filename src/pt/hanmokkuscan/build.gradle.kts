import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Hanmokku Scan"
    contentWarning = ContentWarning.MIXED
    libVersion = "1.6"
    theme = "zeistmanga"

    source {
        lang = "pt-BR"
        baseUrl = "https://hanmokkuscan.blogspot.com"
    }
}
