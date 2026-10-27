package keiyoushi.lib.runtime

import android.app.AlertDialog
import android.content.Context
import android.os.Handler
import android.os.Looper
import keiyoushi.utils.applicationContext
import keiyoushi.utils.ui.topActivity

/** Asks to install the Keiyoushi Runtime extension, once per app process and at most once a week */
internal object InstallPrompt {
    private const val SHOWN = "keiyoushi.lib.runtime.prompt"
    private const val PREFERENCES = "keiyoushi_lib_runtime"
    private const val ASKED_AT = "asked_at"
    private const val INTERVAL_MILLIS = 7 * 24 * 60 * 60 * 1000L

    fun show() {
        if (System.getProperties().putIfAbsent(SHOWN, true) != null) return
        val preferences = applicationContext.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
        if (System.currentTimeMillis() - preferences.getLong(ASKED_AT, 0) < INTERVAL_MILLIS) return

        Handler(Looper.getMainLooper()).post {
            val activity = runCatching { topActivity() }.getOrElse {
                System.getProperties().remove(SHOWN)
                return@post
            }
            val answered = { preferences.edit().putLong(ASKED_AT, System.currentTimeMillis()).apply() }
            AlertDialog.Builder(activity)
                .setTitle("Keiyoushi Runtime")
                .setMessage("Some sources work better with the Keiyoushi Runtime extension. You can install it from Browse \u2192 Extensions.")
                .setPositiveButton(android.R.string.ok) { _, _ -> answered() }
                .setOnCancelListener { answered() }
                .show()
        }
    }
}
