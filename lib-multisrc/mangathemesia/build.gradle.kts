plugins {
    alias(kei.plugins.multisrc)
}

dependencies {
    api(project(":lib:i18n"))
}

keiyoushi {
    libVersion = "1.6"

    deeplink {
        path("/.*/..*")
        path("/..*")
    }
}
