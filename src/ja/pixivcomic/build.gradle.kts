import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Pixiv Comic"
    contentWarning = ContentWarning.MIXED
    libVersion = "1.6"

    source {
        name = "Pixivコミック"
        lang = "ja"
        baseUrl = "https://comic.pixiv.net"
    }
}

dependencies {
    implementation(project(":lib:publus"))
}
