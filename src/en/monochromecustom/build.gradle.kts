import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Monochrome Custom"
    contentWarning = ContentWarning.SAFE
    libVersion = "1.6"
    theme = "monochrome"

    source {
        lang = "en"
        baseUrl {
            custom("https://monochromecms.netlify.app")
        }
    }
}
