import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "MangaKimi"
    contentWarning = ContentWarning.MIXED
    libVersion = "1.6"
    theme = "mangathemesia"

    source {
        lang = "th"
        baseUrl = "https://www.mangakimi.com"
    }
}

dependencies {

    implementation(project(":lib:unpacker"))
}
