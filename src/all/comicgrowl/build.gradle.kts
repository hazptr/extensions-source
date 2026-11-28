import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Comic Growl"
    contentWarning = ContentWarning.SAFE
    libVersion = "1.6"
    theme = "comiciviewer"

    source {
        lang = "all"
        baseUrl = "https://comic-growl.com"
    }
}
