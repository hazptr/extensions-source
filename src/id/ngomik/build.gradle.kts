import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Ngomik (unoriginal)"
    contentWarning = ContentWarning.SAFE
    libVersion = "1.6"
    theme = "mangathemesia"

    source {
        lang = "id"
        baseUrl = "https://02.ngomik.cc"
        id = 5360960267485387879L
    }
}
