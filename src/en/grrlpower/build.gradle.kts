import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Grrl Power Comic"
    contentWarning = ContentWarning.SAFE
    libVersion = "1.6"

    source {
        lang = "en"
        baseUrl = "https://www.grrlpowercomic.com"
    }
}

dependencies {
    implementation(project(":lib:textinterceptor"))
}
