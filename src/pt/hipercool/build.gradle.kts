import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "HipercooL"
    contentWarning = ContentWarning.NSFW
    libVersion = "1.6"
    theme = "hiper"

    source {
        name = "Hipercool"
        lang = "pt-BR"
        baseUrl {
            custom("https://lerhentais.com")
        }
        id = 2379514871370953957
    }
}
