package io.github.keiyoushi.gradle.internal

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.gradle.api.Project
import org.gradle.api.provider.Provider
import java.util.concurrent.ConcurrentHashMap

/** Gradle property: path to the repo's versions.json, the last published versionCode of every package. */
internal const val PUBLISHED_VERSIONS_PROPERTY = "kei.publishedVersions"

private val parsedVersions = ConcurrentHashMap<String, Map<String, Int>>()

/** Published versionCode per package name, empty when [PUBLISHED_VERSIONS_PROPERTY] isn't set. */
internal fun Project.publishedVersionCodes(): Provider<Map<String, Int>> {
    val path = providers.gradleProperty(PUBLISHED_VERSIONS_PROPERTY)
    return providers.fileContents(rootProject.layout.projectDirectory.file(path)).asText
        .map { text ->
            parsedVersions.computeIfAbsent(text) {
                Json.parseToJsonElement(it).jsonObject.mapValues { (_, entry) ->
                    entry.jsonObject.getValue("versionCode").jsonPrimitive.int
                }
            }
        }
        .orElse(path.map { error("$PUBLISHED_VERSIONS_PROPERTY: $it does not exist") })
        .orElse(emptyMap())
}

/** The first Android versionCode of a libVersion: "1.6" is 106000. */
internal fun libVersionBase(libVersion: String): Int = libVersion.split(".").joinToString("") { it.padStart(2, '0') }.toInt() * 1000

/**
 * The versionCode a build of [packageName] ships with: one past its last published version, or the start of
 * [libVersion]'s range for a package never published. Matches next_version_code() in publish-repo.py.
 */
internal fun nextVersionCode(packageName: String, libVersion: String, published: Map<String, Int>): Int {
    val base = libVersionBase(libVersion)
    val code = maxOf(base, (published[packageName] ?: -1) + 1)
    check(code - base < 1000) { "$packageName: versionCode $code is past the end of libVersion $libVersion's range" }
    return code
}
