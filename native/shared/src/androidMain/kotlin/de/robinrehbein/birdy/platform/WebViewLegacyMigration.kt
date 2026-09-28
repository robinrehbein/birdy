package de.robinrehbein.birdy.platform

import android.annotation.SuppressLint
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlin.coroutines.resume

/**
 * Reads the Capacitor app's WebView `localStorage` with a hidden WebView (platform.md §4.7).
 *
 * This only finds data on an **in-place update of the same package** (`de.robinrehbein.birdy`):
 * Android's WebView storage is scoped per-package, so a fresh WebView created here sees the same
 * on-disk data the old Capacitor `WebView` wrote, at the same origin. A side-by-side install or a
 * fresh install has no Capacitor data at all — that is the normal case and returns an empty map.
 *
 * Every failure (timeout, WebView creation/load error, unreadable script result) throws
 * [LegacyReadException] instead, so the caller leaves the migration pending and retries on the
 * next launch rather than marking it done with nothing imported.
 *
 * RISK (unverified, see platform.md §4.7): this assumes Capacitor Android's documented default
 * origin `https://localhost` for the bundled WebView (no `server.hostname` override in this repo's
 * `capacitor.config.json`). This must be confirmed against the installed `@capacitor/android`
 * version before shipping — e.g. by inspecting `document.location.origin` in the live Capacitor
 * APK via `chrome://inspect` — since loading the wrong origin here would silently find no data.
 */
class WebViewLegacyMigration(private val context: Context) : LegacyMigration {
    override suspend fun readLegacyStorage(): Map<String, String> {
        val outcome = withTimeoutOrNull(TIMEOUT_MS) {
            try {
                readOnMainThread()
            } catch (e: LegacyReadException) {
                throw e
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (t: Throwable) {
                throw LegacyReadException("Hidden WebView failed", t)
            }
        } ?: throw LegacyReadException("Timed out reading legacy localStorage")
        return outcome
    }

    @SuppressLint("SetJavaScriptEnabled")
    private suspend fun readOnMainThread(): Map<String, String> = withContext(Dispatchers.Main) {
        val result = suspendCancellableCoroutine<Result<Map<String, String>>> { cont ->
            val webView = WebView(context)
            var resumed = false
            fun finish(result: Result<Map<String, String>>) {
                if (resumed) return
                resumed = true
                webView.stopLoading()
                webView.destroy()
                if (cont.isActive) cont.resume(result)
            }
            fun fail(reason: String, cause: Throwable? = null) =
                finish(Result.failure(LegacyReadException(reason, cause)))
            cont.invokeOnCancellation {
                if (!resumed) {
                    resumed = true
                    webView.stopLoading()
                    webView.destroy()
                }
            }
            try {
                webView.settings.javaScriptEnabled = true
                webView.settings.domStorageEnabled = true
                webView.settings.cacheMode = WebSettings.LOAD_NO_CACHE
                webView.webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView, url: String) {
                        val keysJs = StorageKeys.LEGACY_KEYS.joinToString(",") { "\"$it\"" }
                        val script = """
                            (function() {
                              var keys = [$keysJs];
                              var out = {};
                              for (var i = 0; i < keys.length; i++) {
                                var v = localStorage.getItem(keys[i]);
                                if (v !== null) out[keys[i]] = v;
                              }
                              return JSON.stringify(out);
                            })();
                        """.trimIndent()
                        view.evaluateJavascript(script) { rawResult ->
                            val parsed = parseLegacyScriptResult(rawResult)
                            if (parsed != null) finish(Result.success(parsed)) else fail("Unreadable script result")
                        }
                    }

                    @Deprecated("Deprecated in Java")
                    override fun onReceivedError(view: WebView, errorCode: Int, description: String?, failingUrl: String?) {
                        fail("WebView load error $errorCode: $description")
                    }
                }
                // Load a blank page at the exact origin the Capacitor app used, so this WebView
                // instance is scoped to the same localStorage data on disk.
                webView.loadDataWithBaseURL(ORIGIN, "<html><body></body></html>", "text/html", "utf-8", null)
            } catch (t: Throwable) {
                fail("WebView setup failed", t)
            }
            // Belt-and-braces timeout independent of the coroutine-level one, in case the WebView
            // never calls back at all.
            Handler(Looper.getMainLooper()).postDelayed({ fail("WebView never called back") }, TIMEOUT_MS)
        }
        result.getOrThrow()
    }

    companion object {
        private const val TIMEOUT_MS = 5000L
        private const val ORIGIN = "https://localhost/"
    }
}

/**
 * Parses the `evaluateJavascript` result of the key-reading script: a JSON string containing a
 * JSON object. Returns null when the result is missing or malformed (a failed read, not "no data").
 */
internal fun parseLegacyScriptResult(rawResult: String?): Map<String, String>? {
    if (rawResult == null || rawResult == "null") return null
    return runCatching {
        val unwrapped = Json.decodeFromString<String>(rawResult)
        val obj = Json.parseToJsonElement(unwrapped) as? JsonObject ?: return null
        obj.mapNotNull { (k, v) -> (v as? JsonPrimitive)?.contentOrNull?.let { k to it } }.toMap()
    }.getOrNull()
}
