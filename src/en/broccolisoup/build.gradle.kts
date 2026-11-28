import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Broccoli Soup"
    contentWarning = ContentWarning.SAFE
    libVersion = "1.6"

    source {
        lang = "en"
        baseUrl = "https://politeandgood.com"
    }
}

dependencies {

    implementation(project(":lib:textinterceptor"))
}
