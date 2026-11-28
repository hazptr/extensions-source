import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Violet Scans"
    pkgName = "en.shojoscans"
    contentWarning = ContentWarning.SAFE
    libVersion = "1.6"
    theme = "mangathemesia"

    source {
        lang = "en"
        baseUrl = "https://violetmanga.com"
        id = 9079184529211162476L
    }
}
