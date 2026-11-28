import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Manhuashe"
    contentWarning = ContentWarning.MIXED
    libVersion = "1.6"

    source {
        name = "漫画社"
        lang = "zh"
        baseUrl {
            mirrors(
                "https://www.311s.com",
                "https://www.m206.com",
            )
        }
    }
}
