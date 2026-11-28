import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Rùa Hấp Chanh Dây"
    theme = "madara"
    contentWarning = ContentWarning.MIXED
    libVersion = "1.6"

    source {
        baseUrl = "https://ruahapchanhday2.com"
        lang = "vi"
    }
}
