import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Twatt"
    contentWarning = ContentWarning.SAFE
    libVersion = "1.6"

    source {
        lang = "fr"
        baseUrl = "https://twatt.fr"
    }
}

dependencies {

    implementation(project(":lib:dataimage"))
}
