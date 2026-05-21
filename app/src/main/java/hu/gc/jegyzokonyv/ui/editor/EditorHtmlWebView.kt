package hu.gc.jegyzokonyv.ui.editor

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Matrix
import android.media.ExifInterface
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
    val hasImagePreviews = html.contains("data-editor-image-preview=\"true\"")
    val hasExportGuides = html.contains("repeat-header") || html.contains("repeat-footer") || html.contains("template-page-break")
    if (!hasEditableContent && !hasToggleChecks && !hasImagePreviews && !hasExportGuides) return html
    val cleanHtml = html
        .replace(Regex("""(?s)<script data-editor-bridge="true">.*?</script>"""), "")
        .replace(Regex("""(?s)<style data-editor-runtime="true">.*?</style>"""), "")
    val runtimeStyle = """
        <style data-editor-runtime="true">
          @media screen {
            .repeat-header, .repeat-footer { display: block !important; margin: 10px 0 !important; padding: 8px !important; border: 1px dashed #6b7280 !important; background: #f8fafc !important; }
            .repeat-header::before, .repeat-footer::before { display: block; margin-bottom: 6px; color: #475569; font-size: 11px; font-weight: 600; }
            .repeat-header::before { content: "Ismétlődő fejléc"; }
            .repeat-footer::before { content: "Ismétlődő lábléc"; }
            .template-page-break { position: relative !important; height: 1px !important; margin: 18px 0 !important; border-top: 1px dashed #999 !important; }
            .template-page-break::after { content: "Oldaltörés"; position: relative; top: -9px; left: 50%; transform: translateX(-50%); display: inline-block; background: #fff; color: #666; font-size: 11px; padding: 0 6px; }
            .image-page { break-inside: avoid; page-break-inside: avoid; }
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
            .editor-image-preview {
              display: block !important;
              width: 100%;
              min-height: 180px !important;
              height: 42vh !important;
              max-height: 520px !important;
              background-size: contain !important;
              background-repeat: no-repeat !important;
              background-position: center !important;
            }
            .photo-block .editor-image-preview {
              width: 100% !important;
              border-radius: 6px;
            }
            body.safety-walkthrough .observation-image-preview {
              width: 100% !important;
              height: 26vh !important;
              min-height: 140px !important;
              max-height: none !important;
              margin: 0 auto 6px !important;
              border: 1px solid #000 !important;
              background-color: #f3f3f3 !important;
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
                var style = preview.getAttribute('data-original-style');
                if (style) img.setAttribute('style', style);
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
          function applyCheckStyle(cell) {
            var checked = (cell.textContent || '').trim() === '✓';
            var bg = checked ? cell.getAttribute('data-check-bg') : cell.getAttribute('data-x-bg');
            var color = checked ? cell.getAttribute('data-check-color') : cell.getAttribute('data-x-color');
            if (bg) cell.style.background = bg;
            if (color) cell.style.color = color;
          }
          function toggleCheck(cell) {
            var current = (cell.textContent || '').trim();
            cell.textContent = current === '✓' ? 'X' : '✓';
            applyCheckStyle(cell);
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
            if (!activeEditable || !activeEditable.scrollIntoView) return;
            activeEditable.scrollIntoView({ block: 'nearest', inline: 'nearest', behavior: 'auto' });
          }
          function notifyEditableFocusAfterBlur() {
            window.setTimeout(function() {
              var focused = document.activeElement && document.activeElement.isContentEditable;
              if (!focused) activeEditable = null;
              notifyFocus(!!focused);
            }, 0);
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
            return true;
          }
          window.EditorCommands = {
            insertText: insertText
          };
          document.querySelectorAll('[data-toggle-check="true"]').forEach(applyCheckStyle);
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
            if (timer) window.clearTimeout(timer);
            timer = window.setTimeout(saveNow, 350);
          }, true);
          document.addEventListener('focusin', function(event) {
            if (!event.target || !event.target.isContentEditable) return;
            activeEditable = event.target;
            storeSelection();
            notifyFocus(true);
            window.setTimeout(scrollActiveIntoView, 250);
          }, true);
          document.addEventListener('selectionchange', storeSelection, true);
          document.addEventListener('focusout', function(event) {
            if (!event.target || !event.target.isContentEditable) return;
            storeSelection();
            if (timer) window.clearTimeout(timer);
            saveNow();
            notifyEditableFocusAfterBlur();
          }, true);
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
        if (!isRelativeDraftImage(src) && !src.startsWith("/")) return@replace match.value
        val image = resolveDraftImage(draftDir, src) ?: return@replace match.value
        val dataUri = previewDataUri(image) ?: return@replace match.value
        val originalClass = Regex("""\bclass="([^"]*)"""")
            .find(match.value)
            ?.groupValues
            ?.get(1)
            .orEmpty()
        val originalStyle = Regex("""\bstyle="([^"]*)"""")
            .find(match.value)
            ?.groupValues
            ?.get(1)
            .orEmpty()
        val previewClasses = buildList {
            add("editor-image-preview")
            if (originalClass.isBlank() || originalClass.split(Regex("\\s+")).contains("observation-image")) {
                add("observation-image-preview")
            }
            if (originalClass.isNotBlank()) add(originalClass)
        }.joinToString(" ")
        val previewStyle = "background-image:url($dataUri)"

        """<div class="$previewClasses" style="$previewStyle" data-editor-image-preview="true" data-relative-src="$src" data-original-class="$originalClass" data-original-style="$originalStyle"></div>"""
    }
}

private fun resolveDraftImage(draftDir: File, src: String): File? {
    val normalizedSrc = src
        .substringBefore('#')
        .substringBefore('?')
        .trim()
    if (normalizedSrc.startsWith("/")) return File(normalizedSrc).takeIf { it.isFile }
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
        val preview = image.previewImageBytes()
        val (mimeType, bytes) = preview ?: ((image.imageMimeType() ?: "image/jpeg") to image.readBytes())
        "data:$mimeType;base64," + Base64.encodeToString(bytes, Base64.NO_WRAP)
    }.getOrNull()

private fun File.previewImageBytes(): Pair<String, ByteArray>? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(absolutePath, bounds)
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

    val sampleSize = calculateInSampleSize(bounds.outWidth, bounds.outHeight, MAX_PREVIEW_IMAGE_SIDE)
    val options = BitmapFactory.Options().apply {
        inSampleSize = sampleSize
        inPreferredConfig = Bitmap.Config.ARGB_8888
    }
    val decoded = BitmapFactory.decodeFile(absolutePath, options) ?: return null
    val oriented = decoded.applyExifOrientation(readExifOrientation())
    if (oriented !== decoded) decoded.recycle()

    val scaled = oriented.scaleDown(MAX_PREVIEW_IMAGE_SIDE)
    if (scaled !== oriented) oriented.recycle()

    return java.io.ByteArrayOutputStream().use { output ->
        val hasAlpha = scaled.hasAlpha()
        val format = if (hasAlpha) Bitmap.CompressFormat.PNG else Bitmap.CompressFormat.JPEG
        val mimeType = if (hasAlpha) "image/png" else "image/jpeg"
        val quality = if (hasAlpha) 100 else PREVIEW_JPEG_QUALITY
        scaled.compress(format, quality, output)
        if (!scaled.isRecycled) scaled.recycle()
        mimeType to output.toByteArray()
    }
}

private fun calculateInSampleSize(width: Int, height: Int, maxSide: Int): Int {
    var sampleSize = 1
    var sampledWidth = width
    var sampledHeight = height
    while (sampledWidth / 2 >= maxSide || sampledHeight / 2 >= maxSide) {
        sampleSize *= 2
        sampledWidth /= 2
        sampledHeight /= 2
    }
    return sampleSize
}

private fun Bitmap.scaleDown(maxSide: Int): Bitmap {
    val longestSide = maxOf(width, height)
    if (longestSide <= maxSide) return this
    val scale = maxSide.toFloat() / longestSide.toFloat()
    val targetWidth = (width * scale).toInt().coerceAtLeast(1)
    val targetHeight = (height * scale).toInt().coerceAtLeast(1)
    return Bitmap.createScaledBitmap(this, targetWidth, targetHeight, true)
}

private fun File.readExifOrientation(): Int =
    runCatching {
        ExifInterface(absolutePath).getAttributeInt(
            ExifInterface.TAG_ORIENTATION,
            ExifInterface.ORIENTATION_NORMAL,
        )
    }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)

private fun Bitmap.applyExifOrientation(orientation: Int): Bitmap {
    val matrix = Matrix()
    when (orientation) {
        ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.setScale(-1f, 1f)
        ExifInterface.ORIENTATION_ROTATE_180 -> matrix.setRotate(180f)
        ExifInterface.ORIENTATION_FLIP_VERTICAL -> {
            matrix.setRotate(180f)
            matrix.postScale(-1f, 1f)
        }
        ExifInterface.ORIENTATION_TRANSPOSE -> {
            matrix.setRotate(90f)
            matrix.postScale(-1f, 1f)
        }
        ExifInterface.ORIENTATION_ROTATE_90 -> matrix.setRotate(90f)
        ExifInterface.ORIENTATION_TRANSVERSE -> {
            matrix.setRotate(-90f)
            matrix.postScale(-1f, 1f)
        }
        ExifInterface.ORIENTATION_ROTATE_270 -> matrix.setRotate(-90f)
        else -> return this
    }
    return runCatching { Bitmap.createBitmap(this, 0, 0, width, height, matrix, true) }.getOrDefault(this)
}

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
        "(function(){ function b(){ window.scrollTo(0, Math.max(document.body.scrollHeight, document.documentElement.scrollHeight)); } window.requestAnimationFrame(b); window.setTimeout(b,250); })();",
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
private const val MAX_PREVIEW_IMAGE_SIDE = 1600
private const val PREVIEW_JPEG_QUALITY = 82
