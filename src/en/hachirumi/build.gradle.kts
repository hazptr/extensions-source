import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Hachirumi"
    contentWarning = ContentWarning.MIXED
    libVersion = "1.6"
    theme = "guya"

    source {
        lang = "en"
        baseUrl = "https://hachirumi.com"
    }
}
