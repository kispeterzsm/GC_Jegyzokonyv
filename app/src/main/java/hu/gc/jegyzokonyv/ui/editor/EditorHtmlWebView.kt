package hu.gc.jegyzokonyv.ui.editor

import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import java.io.File

@Composable
fun EditorHtmlWebView(
    html: String,
    draftDir: File,
    onHtmlChanged: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val bridge = remember(onHtmlChanged) { EditorBridge(onHtmlChanged) }
    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            WebView(ctx).apply {
                settings.javaScriptEnabled = true
                @Suppress("DEPRECATION")
                settings.allowFileAccess = true
                @Suppress("DEPRECATION")
                settings.allowFileAccessFromFileURLs = true
                settings.builtInZoomControls = true
                settings.displayZoomControls = false
                settings.loadWithOverviewMode = true
                settings.useWideViewPort = false
                settings.blockNetworkLoads = true
                addJavascriptInterface(bridge, BRIDGE_NAME)
                webViewClient = WebViewClient()
                setBackgroundColor(0x00000000)
            }
        },
        update = { view ->
            if (view.getTag(LOADED_HTML_TAG) == html) return@AndroidView
            val baseUrl = "file://${draftDir.absolutePath}/"
            val editableHtml = injectEditBridge(html)
            view.setTag(LOADED_HTML_TAG, html)
            view.loadDataWithBaseURL(baseUrl, editableHtml, "text/html", "utf-8", null)
        },
    )
}

private class EditorBridge(
    private val onHtmlChanged: (String) -> Unit,
) {
    @JavascriptInterface
    fun save(html: String) {
        onHtmlChanged(html)
    }
}

private fun injectEditBridge(html: String): String {
    if (!html.contains("contenteditable=\"true\"")) return html
    val cleanHtml = html.replace(Regex("""(?s)<script data-editor-bridge="true">.*?</script>"""), "")
    val script = """
        <script data-editor-bridge="true">
        (function() {
          var timer = null;
          function saveNow() {
            timer = null;
            if (window.$BRIDGE_NAME && window.$BRIDGE_NAME.save) {
              var clone = document.documentElement.cloneNode(true);
              clone.querySelectorAll('script[data-editor-bridge="true"]').forEach(function(node) {
                node.parentNode.removeChild(node);
              });
              window.$BRIDGE_NAME.save(clone.outerHTML);
            }
          }
          document.addEventListener('input', function(event) {
            if (!event.target || !event.target.isContentEditable) return;
            if (timer) window.clearTimeout(timer);
            timer = window.setTimeout(saveNow, 350);
          }, true);
          document.addEventListener('blur', function(event) {
            if (!event.target || !event.target.isContentEditable) return;
            if (timer) window.clearTimeout(timer);
            saveNow();
          }, true);
        })();
        </script>
    """.trimIndent()
    return if (cleanHtml.contains("</body>", ignoreCase = true)) {
        cleanHtml.replace("</body>", "$script</body>", ignoreCase = true)
    } else {
        cleanHtml + script
    }
}

private const val BRIDGE_NAME = "EditorBridge"
private const val LOADED_HTML_TAG = 0x55443322
