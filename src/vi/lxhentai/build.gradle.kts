import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "LXManga"
    contentWarning = ContentWarning.NSFW
    libVersion = "1.6"

    source {
        id = 6495630445796108150L
        lang = "vi"
        baseUrl {
            custom("https://lxmanga.top")
        }
    }

    deeplink {
        path("/truyen/..*")
    }
}
