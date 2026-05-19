package hu.gc.jegyzokonyv.ui.editor

import android.graphics.Color
import android.webkit.JavascriptInterface
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import android.util.Base64
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import org.json.JSONObject
import java.io.File
import java.io.FileInputStream

@Composable
fun EditorHtmlWebView(
    html: String,
    draftDir: File,
    onHtmlChanged: (String) -> Unit,
    onEditableCellFocusedChanged: (Boolean) -> Unit,
    dictatedText: String?,
    dictationToken: Int,
    scrollToBottomToken: Int,
    modifier: Modifier = Modifier,
) {
    val bridge = remember(onHtmlChanged, onEditableCellFocusedChanged) {
        EditorBridge(onHtmlChanged, onEditableCellFocusedChanged)
    }
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
                webViewClient = DraftFileWebViewClient(draftDir)
                setBackgroundColor(Color.WHITE)
            }
        },
        update = { view ->
            (view.webViewClient as? DraftFileWebViewClient)?.draftDir = draftDir
            val htmlChanged = view.getTag(LOADED_HTML_TAG) != html
            val lastScrollToken = view.getTag(SCROLL_TO_BOTTOM_TOKEN_TAG) as? Int
            val pendingScrollToken = view.getTag(SCROLL_TO_BOTTOM_PENDING_TOKEN_TAG) as? Int
            val hasNewScrollRequest = lastScrollToken != null && scrollToBottomToken != lastScrollToken
            val shouldScrollAfterLoad = htmlChanged && (hasNewScrollRequest || pendingScrollToken == scrollToBottomToken)
            if (htmlChanged) {
                val baseUrl = draftDir.toWebViewBaseUrl()
                val displayHtml = embedDraftImages(html, draftDir)
                val editableHtml = injectEditBridge(displayHtml)
                view.setTag(LOADED_HTML_TAG, html)
                view.setTag(SCROLL_TO_BOTTOM_AFTER_LOAD_TAG, shouldScrollAfterLoad)
                if (lastScrollToken == null || shouldScrollAfterLoad) {
                    view.setTag(SCROLL_TO_BOTTOM_TOKEN_TAG, scrollToBottomToken)
                    view.setTag(SCROLL_TO_BOTTOM_PENDING_TOKEN_TAG, null)
                }
                view.loadDataWithBaseURL(baseUrl, editableHtml, "text/html", "utf-8", null)
            } else if (hasNewScrollRequest) {
                view.setTag(SCROLL_TO_BOTTOM_PENDING_TOKEN_TAG, scrollToBottomToken)
            }
            val lastToken = view.getTag(DICTATION_TOKEN_TAG) as? Int
            if (dictatedText != null && dictationToken != lastToken) {
                view.setTag(DICTATION_TOKEN_TAG, dictationToken)
                view.evaluateJavascript(
                    "window.EditorCommands && window.EditorCommands.insertText(${JSONObject.quote(dictatedText)});",
                    null,
                )
            }
        },
    )
}

private class EditorBridge(
    private val onHtmlChanged: (String) -> Unit,
    private val onEditableCellFocusedChanged: (Boolean) -> Unit,
) {
    @JavascriptInterface
    fun save(html: String) {
        onHtmlChanged(html)
    }

    @JavascriptInterface
    fun editableFocused(focused: Boolean) {
        onEditableCellFocusedChanged(focused)
    }
}

private fun injectEditBridge(html: String): String {
    val hasEditableContent = html.contains("contenteditable=\"true\"")
    val hasToggleChecks = html.contains("data-toggle-check=\"true\"")
    if (!hasEditableContent && !hasToggleChecks) return html
    val cleanHtml = html
        .replace(Regex("""(?s)<script data-editor-bridge="true">.*?</script>"""), "")
        .replace(Regex("""(?s)<style data-editor-runtime="true">.*?</style>"""), "")
    val runtimeStyle = """
        <style data-editor-runtime="true">
          @media screen {
            body.safety-walkthrough { font-size: 10px !important; }
            body.safety-walkthrough .safety-page { min-height: auto !important; padding: 12px 8px 18px !important; }
            body.safety-walkthrough .safety-header { margin-bottom: 8px !important; }
            body.safety-walkthrough .safety-header th { font-size: 13px !important; padding: 3px 4px !important; }
            body.safety-walkthrough .intro-body { margin: 0 16px !important; font-size: 14px !important; }
            body.safety-walkthrough .risk-matrix,
            body.safety-walkthrough .risk-levels,
            body.safety-walkthrough .observation-body { width: 100% !important; }
            body.safety-walkthrough .observation-image {
              display: block !important;
              width: 100% !important;
              height: auto !important;
              max-height: 26vh !important;
              object-fit: contain !important;
              margin: 0 auto 6px !important;
              border: 1px solid #000 !important;
              background: #f3f3f3 !important;
            }
            body.safety-walkthrough .observation-table { font-size: 12px !important; }
            body.safety-walkthrough .observation-table th { width: 25% !important; }
            body.safety-walkthrough .observation-table th,
            body.safety-walkthrough .observation-table td { padding: 2px 4px !important; }
          }
        </style>
    """.trimIndent()
    val script = """
        <script data-editor-bridge="true">
        (function() {
          var timer = null;
          var activeEditable = null;
          var savedRange = null;
          function saveNow() {
            timer = null;
            if (window.$BRIDGE_NAME && window.$BRIDGE_NAME.save) {
              var clone = document.documentElement.cloneNode(true);
              clone.querySelectorAll('script[data-editor-bridge="true"]').forEach(function(node) {
                node.parentNode.removeChild(node);
              });
              clone.querySelectorAll('img[data-relative-src]').forEach(function(img) {
                img.setAttribute('src', img.getAttribute('data-relative-src'));
                img.removeAttribute('data-relative-src');
              });
              clone.querySelectorAll('[data-editor-image-preview="true"]').forEach(function(preview) {
                var img = preview.ownerDocument.createElement('img');
                img.setAttribute('src', preview.getAttribute('data-relative-src'));
                var classes = preview.getAttribute('data-original-class');
                if (classes) img.setAttribute('class', classes);
                preview.parentNode.replaceChild(img, preview);
              });
              window.$BRIDGE_NAME.save(clone.outerHTML);
            }
          }
          function notifyFocus(focused) {
            if (window.$BRIDGE_NAME && window.$BRIDGE_NAME.editableFocused) {
              window.$BRIDGE_NAME.editableFocused(focused);
            }
          }
          function toggleCheck(cell) {
            var current = (cell.textContent || '').trim();
            cell.textContent = current === '✓' ? 'X' : '✓';
            saveNow();
          }
          function storeSelection() {
            var selection = window.getSelection();
            if (!selection || selection.rangeCount === 0) return;
            var range = selection.getRangeAt(0);
            if (activeEditable && activeEditable.contains(range.commonAncestorContainer)) {
              savedRange = range.cloneRange();
            }
          }
          function scrollActiveIntoView() {
            if (!activeEditable) return;
            [120, 450].forEach(function(delay) {
              window.setTimeout(function() {
                var table = activeEditable.closest && activeEditable.closest('table');
                (table || activeEditable).scrollIntoView({ block: table ? 'center' : 'nearest', inline: 'nearest', behavior: 'smooth' });
              }, delay);
            });
          }
          function insertText(text) {
            if (!activeEditable) return false;
            activeEditable.focus();
            var selection = window.getSelection();
            if (savedRange) {
              selection.removeAllRanges();
              selection.addRange(savedRange);
            }
            if (selection && selection.rangeCount > 0 && activeEditable.contains(selection.getRangeAt(0).commonAncestorContainer)) {
              var range = selection.getRangeAt(0);
              range.deleteContents();
              range.insertNode(document.createTextNode(text));
              range.collapse(false);
              selection.removeAllRanges();
              selection.addRange(range);
            } else {
              activeEditable.appendChild(document.createTextNode(text));
            }
            storeSelection();
            saveNow();
            scrollActiveIntoView();
            return true;
          }
          window.EditorCommands = {
            insertText: insertText
          };
          document.addEventListener('click', function(event) {
            var target = event.target;
            if (!target || !target.closest) return;
            var cell = target.closest('[data-toggle-check="true"]');
            if (!cell) return;
            event.preventDefault();
            toggleCheck(cell);
          }, true);
          document.addEventListener('input', function(event) {
            if (!event.target || !event.target.isContentEditable) return;
            activeEditable = event.target;
            storeSelection();
            scrollActiveIntoView();
            if (timer) window.clearTimeout(timer);
            timer = window.setTimeout(saveNow, 350);
          }, true);
          document.addEventListener('focusin', function(event) {
            if (!event.target || !event.target.isContentEditable) return;
            activeEditable = event.target;
            storeSelection();
            notifyFocus(true);
            scrollActiveIntoView();
          }, true);
          document.addEventListener('selectionchange', storeSelection, true);
          document.addEventListener('blur', function(event) {
            if (!event.target || !event.target.isContentEditable) return;
            storeSelection();
            if (timer) window.clearTimeout(timer);
            saveNow();
          }, true);
          if (window.visualViewport) {
            window.visualViewport.addEventListener('resize', scrollActiveIntoView);
            window.visualViewport.addEventListener('scroll', scrollActiveIntoView);
          }
        })();
        </script>
    """.trimIndent()
    return if (cleanHtml.contains("</body>", ignoreCase = true)) {
        cleanHtml.replace("</body>", "$runtimeStyle$script</body>", ignoreCase = true)
    } else {
        cleanHtml + runtimeStyle + script
    }
}

private fun embedDraftImages(html: String, draftDir: File): String {
    return html.replace(Regex("""<img\b[^>]*\bsrc="([^"]+)"[^>]*>""")) { match ->
        val src = match.groupValues[1]
        if (!isRelativeDraftImage(src)) return@replace match.value
        val image = resolveDraftImage(draftDir, src) ?: return@replace match.value
        val dataUri = previewDataUri(image) ?: return@replace match.value
        val originalClass = Regex("""\bclass="([^"]*)"""")
            .find(match.value)
            ?.groupValues
            ?.get(1)
            .orEmpty()
        val className = originalClass.ifBlank { "observation-image" }

        """<img class="$className" src="$dataUri" data-relative-src="$src">"""
    }
}

private fun resolveDraftImage(draftDir: File, src: String): File? {
    val normalizedSrc = src
        .substringBefore('#')
        .substringBefore('?')
        .trim()
    if (!isRelativeDraftImage(normalizedSrc)) return null

    return runCatching {
        val root = draftDir.canonicalFile
        val file = File(root, android.net.Uri.decode(normalizedSrc)).canonicalFile
        val rootPath = root.path + File.separator
        file.takeIf { it.isFile && (it == root || it.path.startsWith(rootPath)) }
    }.getOrNull()
}

private fun previewDataUri(image: File): String? =
    runCatching {
        val mimeType = image.imageMimeType() ?: "image/jpeg"
        "data:$mimeType;base64," + Base64.encodeToString(image.readBytes(), Base64.NO_WRAP)
    }.getOrNull()

private fun isRelativeDraftImage(src: String): Boolean =
    src.isNotBlank() &&
        !src.startsWith("/") &&
        !src.startsWith("//") &&
        !src.contains(":")

private class DraftFileWebViewClient(
    var draftDir: File,
) : WebViewClient() {
    override fun onPageFinished(view: WebView?, url: String?) {
        super.onPageFinished(view, url)
        if (view?.getTag(SCROLL_TO_BOTTOM_AFTER_LOAD_TAG) == true) {
            view.setTag(SCROLL_TO_BOTTOM_AFTER_LOAD_TAG, false)
            view.scrollDocumentToBottom()
        }
    }

    override fun shouldInterceptRequest(view: WebView?, request: WebResourceRequest?): WebResourceResponse? {
        val uri = request?.url ?: return null
        if (uri.scheme != "file") return null

        val root = runCatching { draftDir.canonicalFile }.getOrNull() ?: return null
        val requested = runCatching { File(uri.path.orEmpty()).canonicalFile }.getOrNull() ?: return null
        val rootPath = root.path + File.separator
        if (requested != root && !requested.path.startsWith(rootPath)) return null
        val mimeType = requested.imageMimeType() ?: return null
        if (!requested.isFile) return null

        return runCatching {
            WebResourceResponse(mimeType, null, FileInputStream(requested))
        }.getOrNull()
    }
}

private fun WebView.scrollDocumentToBottom() {
    evaluateJavascript(
        "(function(){ function b(){ window.scrollTo({ top: Math.max(document.body.scrollHeight, document.documentElement.scrollHeight), behavior: 'smooth' }); } window.setTimeout(b,150); window.setTimeout(b,600); window.setTimeout(b,1200); })();",
        null,
    )
}

private fun File.toWebViewBaseUrl(): String =
    "file://${absolutePath.trimEnd('/')}/"

private fun File.imageMimeType(): String? =
    when (extension.lowercase()) {
        "jpg", "jpeg" -> "image/jpeg"
        "png" -> "image/png"
        "webp" -> "image/webp"
        else -> null
    }

private const val BRIDGE_NAME = "EditorBridge"
private const val LOADED_HTML_TAG = 0x55443322
private const val DICTATION_TOKEN_TAG = 0x55443323
private const val SCROLL_TO_BOTTOM_TOKEN_TAG = 0x55443324
private const val SCROLL_TO_BOTTOM_AFTER_LOAD_TAG = 0x55443325
private const val SCROLL_TO_BOTTOM_PENDING_TOKEN_TAG = 0x55443326
