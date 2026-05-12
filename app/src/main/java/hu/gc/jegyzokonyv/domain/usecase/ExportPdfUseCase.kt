package hu.gc.jegyzokonyv.domain.usecase

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.media.ExifInterface
import android.net.Uri
import android.os.SystemClock
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.util.Log
import hu.gc.jegyzokonyv.data.repo.DraftRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import javax.inject.Inject
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

class ExportPdfUseCase @Inject constructor(
    private val draftRepository: DraftRepository,
) {

    suspend operator fun invoke(draftId: String): File {
        val draft = draftRepository.getDraft(draftId)
            ?: error("Draft not found: $draftId")
        val html = draftRepository.loadHtml(draftId)
        val draftDir = draftRepository.draftDir(draftId)
        val documentName = sanitizeFileName(draft.title.ifBlank { "jegyzokonyv" })
        val output = draftRepository.exportPdfTarget(draftId, "$documentName.pdf")
        val tempOutput = File(output.parentFile ?: draftDir, "${output.name}.tmp")
        val startedAt = SystemClock.elapsedRealtime()

        return withTimeout(PDF_TIMEOUT_MS) {
            withContext(Dispatchers.IO) {
                val document = parseExportDocument(html, draftDir)
                val stats = renderNativePdf(document, tempOutput)
                replacePdf(tempOutput, output)
                deleteOtherExportedPdfs(draftDir, output)
                deleteLegacyExportImageCache(draftDir)

                val elapsedMs = SystemClock.elapsedRealtime() - startedAt
                Log.i(
                    TAG,
                    "PDF export finished in ${elapsedMs}ms, " +
                        "pages=${stats.pageCount}, blocks=${document.blocks.size}, " +
                        "images=${stats.imageCount}",
                )
                output
            }
        }
    }

    private fun parseExportDocument(html: String, draftDir: File): ExportDocument {
        val doc = Jsoup.parse(html)
        val title = doc.selectFirst("h1")?.wholeText()?.trim()
            ?: doc.title().trim()
        val meta = doc.selectFirst(".meta")?.wholeText()?.trim()?.ifBlank { null }
        val content = doc.getElementById(CONTENT_ID) ?: doc.body()
        val blocks = buildList {
            content.children().forEach { element ->
                when {
                    element.hasClass("photo-block") -> {
                        val image = element.selectFirst("img[src]")
                        val source = image?.attr("src").orEmpty()
                        add(
                            ExportBlock.Photo(
                                image = resolveDraftImage(draftDir, source)?.takeIf { it.isFile },
                                source = source,
                                caption = element.selectFirst("p")
                                    ?.wholeText()
                                    ?.trim()
                                    ?.ifBlank { null },
                            )
                        )
                    }
                    element.hasClass("date-block") -> {
                        blockText(element)?.let {
                            add(ExportBlock.Text(text = it, style = TextBlockStyle.Date))
                        }
                    }
                    element.hasClass("text-block") -> {
                        blockText(element)?.let {
                            add(ExportBlock.Text(text = it, style = TextBlockStyle.Body))
                        }
                    }
                }
            }
        }

        return ExportDocument(
            title = title.ifBlank { DEFAULT_TITLE },
            meta = meta,
            blocks = blocks,
        )
    }

    private fun blockText(element: Element): String? {
        val paragraphs = element.select("p")
        val text = if (paragraphs.isNotEmpty()) {
            paragraphs.joinToString("\n") { it.wholeText().trim() }
        } else {
            element.wholeText().trim()
        }
        return text.ifBlank { null }
    }

    private fun resolveDraftImage(draftDir: File, src: String): File? {
        val normalizedSrc = src
            .substringBefore('#')
            .substringBefore('?')
            .trim()

        if (normalizedSrc.isBlank() ||
            normalizedSrc.startsWith("/") ||
            normalizedSrc.startsWith("//") ||
            normalizedSrc.contains(":")
        ) {
            return null
        }

        return runCatching {
            val root = draftDir.canonicalFile
            val file = File(root, Uri.decode(normalizedSrc)).canonicalFile
            val rootPath = root.path + File.separator
            file.takeIf { it == root || it.path.startsWith(rootPath) }
        }.getOrNull()
    }

    private fun renderNativePdf(document: ExportDocument, output: File): ExportStats {
        output.parentFile?.mkdirs()
        if (output.exists()) output.delete()

        val renderer = NativePdfRenderer(document)
        return try {
            val stats = renderer.render()
            FileOutputStream(output).use { renderer.writeTo(it) }
            stats
        } catch (error: Throwable) {
            output.delete()
            throw error
        } finally {
            renderer.close()
        }
    }

    private fun replacePdf(tempOutput: File, output: File) {
        output.parentFile?.mkdirs()
        Files.move(
            tempOutput.toPath(),
            output.toPath(),
            StandardCopyOption.REPLACE_EXISTING,
        )
    }

    private fun deleteOtherExportedPdfs(draftDir: File, keep: File) {
        val keepCanonical = runCatching { keep.canonicalFile }.getOrDefault(keep)
        draftDir.listFiles { _, name -> name.endsWith(".pdf", ignoreCase = true) }
            ?.forEach { file ->
                val canonical = runCatching { file.canonicalFile }.getOrDefault(file)
                if (canonical != keepCanonical) file.delete()
            }
    }

    private fun deleteLegacyExportImageCache(draftDir: File) {
        File(draftDir, LEGACY_EXPORT_IMAGES_DIR).deleteRecursively()
    }

    private fun sanitizeFileName(value: String): String =
        value.replace(Regex("[^\\p{L}\\p{N}_-]+"), "_").take(64).ifBlank { "jegyzokonyv" }

    private class NativePdfRenderer(
        private val document: ExportDocument,
    ) {
        private val pdf = PdfDocument()
        private var page: PdfDocument.Page? = null
        private var pageNumber = 0
        private var y = PAGE_MARGIN_PT
        private var imageCount = 0

        private val titlePaint = textPaint(
            size = 17f,
            color = Color.rgb(26, 26, 26),
            typeface = Typeface.DEFAULT_BOLD,
        )
        private val metaPaint = textPaint(size = 9f, color = Color.rgb(102, 102, 102))
        private val bodyPaint = textPaint(size = 10.5f, color = Color.rgb(26, 26, 26))
        private val datePaint = textPaint(
            size = 10.5f,
            color = Color.rgb(26, 26, 26),
            typeface = Typeface.DEFAULT_BOLD,
        )
        private val captionPaint = textPaint(
            size = 9.75f,
            color = Color.rgb(51, 51, 51),
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.ITALIC),
        )
        private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(46, 82, 102)
            strokeWidth = 1.5f
        }
        private val imagePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            isFilterBitmap = true
            isDither = true
        }
        private val placeholderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(160, 160, 160)
            style = Paint.Style.STROKE
            strokeWidth = 1f
        }
        private val placeholderTextPaint = textPaint(size = 9.5f, color = Color.rgb(90, 90, 90))

        fun render(): ExportStats {
            startPage()
            drawWrappedText(document.title, titlePaint, spacingAfter = 6f)
            canvas.drawLine(PAGE_MARGIN_PT, y, PAGE_WIDTH_PT - PAGE_MARGIN_PT, y, linePaint)
            y += 14f

            document.meta?.let {
                drawWrappedText(it, metaPaint, spacingAfter = 12f)
            }

            document.blocks.forEach { block ->
                when (block) {
                    is ExportBlock.Text -> drawTextBlock(block)
                    is ExportBlock.Photo -> drawPhotoBlock(block)
                }
            }
            finishCurrentPage()
            return ExportStats(pageCount = pageNumber, imageCount = imageCount)
        }

        fun writeTo(output: FileOutputStream) {
            pdf.writeTo(output)
        }

        fun close() {
            runCatching { finishCurrentPage() }
            pdf.close()
        }

        private fun drawTextBlock(block: ExportBlock.Text) {
            val paint = when (block.style) {
                TextBlockStyle.Body -> bodyPaint
                TextBlockStyle.Date -> datePaint
            }
            drawWrappedText(block.text, paint, spacingAfter = 10f)
        }

        private fun drawPhotoBlock(block: ExportBlock.Photo) {
            val image = block.image
            if (image == null) {
                drawMissingImage(block.source)
                block.caption?.let { drawWrappedText(it, captionPaint, spacingAfter = 12f) }
                return
            }

            val bounds = decodeImageBounds(image)
            if (bounds == null) {
                drawMissingImage(block.source.ifBlank { image.name })
                block.caption?.let { drawWrappedText(it, captionPaint, spacingAfter = 12f) }
                return
            }

            val orientation = readOrientation(image)
            val displaySize = bounds.displaySize(orientation)
            val captionLayout = block.caption?.let {
                staticLayout(it, captionPaint, CONTENT_WIDTH_PT.roundToInt())
            }
            val captionHeight = captionLayout?.height?.toFloat() ?: 0f
            val captionGap = if (captionLayout == null) 0f else 5f

            var drawWidth = CONTENT_WIDTH_PT
            var drawHeight = drawWidth * displaySize.height.toFloat() / displaySize.width.toFloat()
            val maxImageHeight = USABLE_HEIGHT_PT - captionGap - captionHeight
            if (drawHeight > maxImageHeight) {
                val scale = maxImageHeight / drawHeight
                drawWidth *= scale
                drawHeight *= scale
            }

            val blockHeight = drawHeight + captionGap + captionHeight + 12f
            if (y + blockHeight > PAGE_BOTTOM_PT && y > PAGE_MARGIN_PT) {
                startPage()
            }

            val left = PAGE_MARGIN_PT + ((CONTENT_WIDTH_PT - drawWidth) / 2f)
            val top = y
            val target = RectF(left, top, left + drawWidth, top + drawHeight)

            if (!drawBitmap(image, bounds, orientation, target)) {
                drawMissingImage(block.source.ifBlank { image.name })
                block.caption?.let { drawWrappedText(it, captionPaint, spacingAfter = 12f) }
                return
            }
            y += drawHeight
            imageCount += 1

            if (captionLayout != null) {
                y += captionGap
                drawStaticLayout(captionLayout, PAGE_MARGIN_PT, y)
                y += captionHeight
            }
            y += 12f
        }

        private fun drawMissingImage(source: String) {
            val placeholderHeight = 96f
            if (y + placeholderHeight > PAGE_BOTTOM_PT && y > PAGE_MARGIN_PT) {
                startPage()
            }
            val rect = RectF(PAGE_MARGIN_PT, y, PAGE_WIDTH_PT - PAGE_MARGIN_PT, y + placeholderHeight)
            canvas.drawRect(rect, placeholderPaint)
            y += 12f
            drawWrappedText("Hiányzó kép: ${source.ifBlank { "ismeretlen" }}", placeholderTextPaint, spacingAfter = 12f)
            y = max(y, rect.bottom + 10f)
        }

        private fun drawBitmap(
            image: File,
            bounds: ImageBounds,
            orientation: Int,
            target: RectF,
        ): Boolean {
            val targetPixels = targetPixelSize(target.width(), target.height(), orientation)
            val options = BitmapFactory.Options().apply {
                inSampleSize = calculateInSampleSize(
                    width = bounds.width,
                    height = bounds.height,
                    targetWidth = targetPixels.width,
                    targetHeight = targetPixels.height,
                )
            }

            val decoded = BitmapFactory.decodeFile(image.absolutePath, options)
            if (decoded == null) {
                Log.w(TAG, "Unable to decode image: ${image.absolutePath}")
                return false
            }
            var oriented: Bitmap? = null
            var scaled: Bitmap? = null

            return try {
                oriented = applyOrientation(decoded, orientation)
                scaled = scaleForPdf(oriented, targetPixels)
                canvas.drawBitmap(scaled, null, target, imagePaint)
                true
            } catch (error: RuntimeException) {
                Log.w(TAG, "Unable to draw image: ${image.absolutePath}", error)
                false
            } finally {
                if (scaled != null && scaled !== oriented) scaled.recycle()
                if (oriented != null && oriented !== decoded) oriented.recycle()
                decoded.recycle()
            }
        }

        private fun targetPixelSize(widthPt: Float, heightPt: Float, orientation: Int): ImageSize {
            val displayWidth = (widthPt * IMAGE_DPI / POINTS_PER_INCH)
                .roundToInt()
                .coerceAtLeast(1)
            val displayHeight = (heightPt * IMAGE_DPI / POINTS_PER_INCH)
                .roundToInt()
                .coerceAtLeast(1)
            return if (swapsAxes(orientation)) {
                ImageSize(width = displayHeight, height = displayWidth)
            } else {
                ImageSize(width = displayWidth, height = displayHeight)
            }
        }

        private fun scaleForPdf(bitmap: Bitmap, target: ImageSize): Bitmap {
            val scale = min(
                target.width.toFloat() / bitmap.width.toFloat(),
                target.height.toFloat() / bitmap.height.toFloat(),
            )
            if (scale >= 0.95f) return bitmap

            val width = (bitmap.width * scale).roundToInt().coerceAtLeast(1)
            val height = (bitmap.height * scale).roundToInt().coerceAtLeast(1)
            return Bitmap.createScaledBitmap(bitmap, width, height, true)
        }

        private fun drawWrappedText(text: String, paint: TextPaint, spacingAfter: Float) {
            val layout = staticLayout(text, paint, CONTENT_WIDTH_PT.roundToInt())
            var line = 0
            while (line < layout.lineCount) {
                if (y >= PAGE_BOTTOM_PT) {
                    startPage()
                }

                val chunkTop = layout.getLineTop(line)
                var endLine = line
                val available = PAGE_BOTTOM_PT - y
                while (endLine < layout.lineCount &&
                    layout.getLineBottom(endLine) - chunkTop <= available
                ) {
                    endLine += 1
                }

                if (endLine == line) {
                    startPage()
                    continue
                }

                val chunkBottom = layout.getLineBottom(endLine - 1)
                canvas.save()
                canvas.translate(PAGE_MARGIN_PT, y - chunkTop)
                canvas.clipRect(
                    0f,
                    chunkTop.toFloat(),
                    CONTENT_WIDTH_PT,
                    chunkBottom.toFloat(),
                )
                layout.draw(canvas)
                canvas.restore()

                y += (chunkBottom - chunkTop).toFloat()
                line = endLine

                if (line < layout.lineCount) {
                    startPage()
                }
            }
            y += spacingAfter
        }

        private fun drawStaticLayout(layout: StaticLayout, left: Float, top: Float) {
            canvas.save()
            canvas.translate(left, top)
            layout.draw(canvas)
            canvas.restore()
        }

        private fun startPage() {
            finishCurrentPage()
            pageNumber += 1
            page = pdf.startPage(
                PdfDocument.PageInfo
                    .Builder(PAGE_WIDTH_PT.roundToInt(), PAGE_HEIGHT_PT.roundToInt(), pageNumber)
                    .create()
            )
            y = PAGE_MARGIN_PT
        }

        private fun finishCurrentPage() {
            page?.let {
                pdf.finishPage(it)
                page = null
            }
        }

        private val canvas: Canvas
            get() = requireNotNull(page).canvas
    }

    private data class ExportDocument(
        val title: String,
        val meta: String?,
        val blocks: List<ExportBlock>,
    )

    private sealed class ExportBlock {
        data class Text(
            val text: String,
            val style: TextBlockStyle,
        ) : ExportBlock()

        data class Photo(
            val image: File?,
            val source: String,
            val caption: String?,
        ) : ExportBlock()
    }

    private enum class TextBlockStyle {
        Body,
        Date,
    }

    private data class ExportStats(
        val pageCount: Int,
        val imageCount: Int,
    )

    private data class ImageBounds(
        val width: Int,
        val height: Int,
    ) {
        fun displaySize(orientation: Int): ImageSize =
            if (swapsAxes(orientation)) {
                ImageSize(width = height, height = width)
            } else {
                ImageSize(width = width, height = height)
            }
    }

    private data class ImageSize(
        val width: Int,
        val height: Int,
    )

    private companion object {
        const val TAG = "ExportPdf"
        const val PDF_TIMEOUT_MS = 30_000L
        const val CONTENT_ID = "content"
        const val DEFAULT_TITLE = "Jegyzőkönyv"
        const val LEGACY_EXPORT_IMAGES_DIR = ".pdf_images"

        const val POINTS_PER_INCH = 72f
        const val IMAGE_DPI = 180f
        const val PAGE_WIDTH_PT = 595f
        const val PAGE_HEIGHT_PT = 842f
        const val PAGE_MARGIN_PT = 42f
        const val CONTENT_WIDTH_PT = PAGE_WIDTH_PT - (PAGE_MARGIN_PT * 2f)
        const val USABLE_HEIGHT_PT = PAGE_HEIGHT_PT - (PAGE_MARGIN_PT * 2f)
        const val PAGE_BOTTOM_PT = PAGE_HEIGHT_PT - PAGE_MARGIN_PT

        fun textPaint(
            size: Float,
            color: Int,
            typeface: Typeface = Typeface.DEFAULT,
        ): TextPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color
            textSize = size
            this.typeface = typeface
        }

        fun staticLayout(text: String, paint: TextPaint, width: Int): StaticLayout =
            StaticLayout.Builder
                .obtain(text, 0, text.length, paint, width)
                .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                .setLineSpacing(0f, 1f)
                .setIncludePad(false)
                .build()

        fun decodeImageBounds(file: File): ImageBounds? {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(file.absolutePath, bounds)
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
            return ImageBounds(width = bounds.outWidth, height = bounds.outHeight)
        }

        fun readOrientation(file: File): Int =
            runCatching {
                ExifInterface(file.absolutePath).getAttributeInt(
                    ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_NORMAL,
                )
            }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)

        fun applyOrientation(bitmap: Bitmap, orientation: Int): Bitmap {
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
                ExifInterface.ORIENTATION_ROTATE_270 -> matrix.setRotate(270f)
                else -> return bitmap
            }
            return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        }

        fun calculateInSampleSize(
            width: Int,
            height: Int,
            targetWidth: Int,
            targetHeight: Int,
        ): Int {
            var inSampleSize = 1
            while (width / (inSampleSize * 2) >= targetWidth &&
                height / (inSampleSize * 2) >= targetHeight
            ) {
                inSampleSize *= 2
            }
            return inSampleSize
        }

        fun swapsAxes(orientation: Int): Boolean =
            orientation == ExifInterface.ORIENTATION_TRANSPOSE ||
                orientation == ExifInterface.ORIENTATION_ROTATE_90 ||
                orientation == ExifInterface.ORIENTATION_TRANSVERSE ||
                orientation == ExifInterface.ORIENTATION_ROTATE_270
    }
}
