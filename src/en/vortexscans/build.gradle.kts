import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Vortex Scans"
    pkgName = "en.arvenscans"
    contentWarning = ContentWarning.SAFE
    libVersion = "1.6"
    theme = "iken"

    source {
        baseUrl = "https://vortexscans.org"
        lang = "en"
    }
}
