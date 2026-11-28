import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "MangaIsekaiThai"
    contentWarning = ContentWarning.MIXED
    libVersion = "1.6"
    theme = "madara"

    source {
        lang = "th"
        baseUrl = "https://www.mangaisekaithai.net"
    }
}

dependencies {

    implementation(project(":lib:unpacker"))
}
