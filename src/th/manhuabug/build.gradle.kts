import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "ManhuaBug"
    contentWarning = ContentWarning.SAFE
    libVersion = "1.6"
    theme = "madara"

    source {
        lang = "th"
        baseUrl = "https://www.manhuabug.com"
    }
}

dependencies {

    implementation(project(":lib:unpacker"))
}
