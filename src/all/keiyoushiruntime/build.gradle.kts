import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

// Libraries other extensions load through lib:runtime; its source lists nothing
keiyoushi {
    name = "Keiyoushi Runtime"
    versionCode = 0
    contentWarning = ContentWarning.SAFE
    libVersion = "1.6"
    source {
        lang = "all"
        baseUrl = "https://github.com/keiyoushi/extensions-source"
    }
}

// Provided by the app
configurations.matching { it.name.endsWith("RuntimeClasspath") }.configureEach {
    exclude(group = "org.jetbrains.kotlin")
    exclude(group = "org.jetbrains.kotlinx")
    listOf(
        "activity", "annotation", "arch.core", "collection", "core", "customview", "fragment", "lifecycle",
        "loader", "savedstate", "versionedparcelable", "viewpager",
    ).forEach { exclude(group = "androidx.$it") }
}

dependencies {
    implementation(libs.cronet)
    implementation(libs.play.services.cronet)
    // Play services' superclasses, for ProGuard
    compileOnly(libs.androidx.fragment)
}
