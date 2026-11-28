import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Bakkin Self-hosted"
    contentWarning = ContentWarning.SAFE
    libVersion = "1.6"
    theme = "bakkin"

    source {
        lang = "en"
        baseUrl {
            custom("http://127.0.0.1/")
        }
    }
}
