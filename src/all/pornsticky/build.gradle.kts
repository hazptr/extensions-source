import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "PornSticky"
    contentWarning = ContentWarning.NSFW
    libVersion = "1.6"

    source {
        lang = "all"
        baseUrl = "https://pornsticky.com"
    }

    deeplink {
        host("pornsticky.com")
        host("www.pornsticky.com")
        path("/..*")
    }
}
