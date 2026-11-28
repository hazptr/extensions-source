plugins {
    alias(kei.plugins.multisrc)
}

keiyoushi {
    libVersion = "1.6"

    deeplink {
        path("/seri/..*")
    }
}

dependencies {
    api(project(":lib:ece"))
}
