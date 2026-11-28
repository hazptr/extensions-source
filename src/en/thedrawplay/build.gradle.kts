import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "The Draw Play"
    contentWarning = ContentWarning.SAFE
    libVersion = "1.6"

    source {
        baseUrl = "https://www.thedrawplay.com"
        lang = "en"
    }

    deeplink {
        host("www.thedrawplay.com")
        host("thedrawplay.com")
        path("/comic/..*")
    }
}

dependencies {
    implementation(project(":lib:textinterceptor"))
}
