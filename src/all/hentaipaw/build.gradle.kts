import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "HentaiPaw"
    contentWarning = ContentWarning.NSFW
    libVersion = "1.6"

    source {
        lang = "all"
        baseUrl = "https://hentaipaw.com"
    }

    deeplink {
        path("/articles/..*")
        path("/viewer")
        path("/tags/..*")
        path("/artists/..*")
        path("/parodies/..*")
        path("/characters/..*")
        path("/groups/..*")
        path("/categories/..*")
    }
}
