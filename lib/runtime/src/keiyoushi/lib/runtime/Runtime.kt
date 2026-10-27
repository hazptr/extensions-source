package keiyoushi.lib.runtime

import android.content.pm.PackageManager
import android.os.Build
import android.os.SystemClock
import dalvik.system.DexClassLoader
import keiyoushi.utils.applicationContext
import java.io.File
import java.util.zip.ZipFile

/**
 * Classes from the Keiyoushi Runtime extension: libraries too big, native or stateful to bundle in each
 * extension. Loaded once per app process and shared by every extension, so only framework, Kotlin and
 * OkHttp types cross over.
 */
object Runtime {
    private const val KEY = "keiyoushi.lib.runtime.v1"
    private const val PACKAGE = "eu.kanade.tachiyomi.extension.all.keiyoushiruntime"
    private const val RECHECK_MILLIS = 60_000L

    @Volatile
    private var checkedAt = Long.MIN_VALUE / 2

    private val ownSignatures by lazy {
        val apk = javaClass.classLoader!!.getResource("AndroidManifest.xml")!!.path
            .substringAfter("file:").substringBefore("!/")
        signatures(apk)
    }

    /**
     * [name] relative to the extension's package (`cronet.CronetRuntime`), or null while the extension
     * isn't installed; [prompt] asks the user to install it then.
     */
    fun loadClass(name: String, prompt: Boolean = true): Class<*>? = loader(prompt)?.loadClass("$PACKAGE.$name")

    private fun loader(prompt: Boolean): ClassLoader? {
        val properties = System.getProperties()
        (properties[KEY] as? ClassLoader)?.let { return it }

        val now = SystemClock.elapsedRealtime()
        if (now - checkedAt < RECHECK_MILLIS) return null
        checkedAt = now

        val apk = runtimeApk(prompt) ?: return null
        val loader = DexClassLoader(apk, null, nativeLibraries(apk), applicationContext.classLoader)
        return properties.putIfAbsent(KEY, loader) as? ClassLoader ?: loader
    }

    /** The extension's APK, installed privately by the app or on the device, when it's signed like this extension */
    private fun runtimeApk(prompt: Boolean): String? {
        val apk = File(applicationContext.filesDir, "exts/$PACKAGE.ext").takeIf { it.isFile }?.path
            ?: try {
                applicationContext.packageManager.getApplicationInfo(PACKAGE, 0).sourceDir
            } catch (_: PackageManager.NameNotFoundException) {
                if (prompt) InstallPrompt.show()
                return null
            }
        return apk.takeIf { ownSignatures.isNotEmpty() && signatures(it) == ownSignatures }
    }

    /** Native libraries load straight from the APK: they're stored uncompressed, not extracted */
    private fun nativeLibraries(apk: String): String? {
        val entries = ZipFile(apk).use { zip -> zip.entries().asSequence().map { it.name }.toList() }
        val abi = Build.SUPPORTED_ABIS.firstOrNull { abi -> entries.any { it.startsWith("lib/$abi/") } } ?: return null
        return "$apk!/lib/$abi"
    }

    @Suppress("DEPRECATION")
    private fun signatures(apk: String): Set<String> {
        val pm = applicationContext.packageManager
        val signatures = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            pm.getPackageArchiveInfo(apk, PackageManager.GET_SIGNING_CERTIFICATES)?.signingInfo?.apkContentsSigners
        } else {
            pm.getPackageArchiveInfo(apk, PackageManager.GET_SIGNATURES)?.signatures
        }
        return signatures.orEmpty().map { it.toCharsString() }.toSet()
    }
}
