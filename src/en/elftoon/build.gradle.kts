import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Elf Toon"
    contentWarning = ContentWarning.SAFE
    libVersion = "1.6"
    theme = "vinetheme"

    source {
        lang = "en"
        baseUrl = "https://elftoon.net"
        versionId = 2
    }
}
