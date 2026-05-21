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
import hu.gc.jegyzokonyv.data.profile.MissingProfileInfoException
import hu.gc.jegyzokonyv.data.profile.ProfileRepository
import hu.gc.jegyzokonyv.data.profile.UserProfile
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
import java.util.Locale
import javax.inject.Inject
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

class ExportPdfUseCase @Inject constructor(
    private val draftRepository: DraftRepository,
    private val profileRepository: ProfileRepository,
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
                val profile = profileRepository.profile.value
                val safetyDocument = parseSafetyDocument(html, draftDir, profile)
                val stats = if (safetyDocument != null) {
                    renderSafetyPdf(safetyDocument, tempOutput)
                } else {
                    val document = parseExportDocument(html, draftDir)
                    renderNativePdf(document, tempOutput)
                }
                replacePdf(tempOutput, output)
                deleteOtherExportedPdfs(draftDir, output)
                deleteLegacyExportImageCache(draftDir)

                val elapsedMs = SystemClock.elapsedRealtime() - startedAt
                Log.i(
                    TAG,
                    "PDF export finished in ${elapsedMs}ms, " +
                        "pages=${stats.pageCount}, " +
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
        val headerBlocks = buildList {
            content.children().filter { it.hasClass("repeat-header") }.forEach { header ->
                header.children().forEach { addExportBlocksFromElement(it, draftDir) }
            }
        }
        val footerBlocks = buildList {
            content.children().filter { it.hasClass("repeat-footer") }.forEach { footer ->
                footer.children().forEach { addExportBlocksFromElement(it, draftDir) }
            }
        }
        val blocks = buildList {
            content.children()
                .filterNot { it.hasClass("repeat-header") || it.hasClass("repeat-footer") }
                .forEach { element -> addExportBlocksFromElement(element, draftDir) }
        }

        return ExportDocument(
            title = title.ifBlank { DEFAULT_TITLE },
            meta = meta,
            headerBlocks = headerBlocks,
            footerBlocks = footerBlocks,
            blocks = blocks,
        )
    }

    private fun MutableList<ExportBlock>.addExportBlocksFromElement(element: Element, draftDir: File) {
        when {
            element.hasClass("photo-block") -> addPhotoExportBlock(element, draftDir)
            element.hasClass("images-block") -> element.children().filterNot { it.hasClass("image-page-template") }.forEach { addExportBlocksFromElement(it, draftDir) }
            element.hasClass("date-block") -> blockText(element)?.let { add(element.toExportTextBlock(it, TextBlockStyle.Date)) }
            element.hasClass("text-block") -> blockText(element)?.let { add(element.toExportTextBlock(it, TextBlockStyle.Body)) }
            element.hasClass("editable-table") || element.normalName() == "table" -> {
                val rows = exportTableRows(element)
                if (rows.isNotEmpty()) add(ExportBlock.Table(rows = rows))
            }
            element.hasClass("signature-block") -> {
                val image = element.selectFirst("img[src]")?.attr("src").orEmpty()
                add(ExportBlock.ProfileImage(resolveAnyImage(draftDir, image)?.takeIf { it.isFile }, element.selectFirst(".signature-name")?.wholeText()?.trim().orEmpty(), true))
            }
            element.hasClass("stamp-block") -> {
                val image = element.selectFirst("img[src]")?.attr("src").orEmpty()
                add(ExportBlock.ProfileImage(resolveAnyImage(draftDir, image)?.takeIf { it.isFile }, "", false))
            }
            element.hasClass("template-page-break") -> add(ExportBlock.PageBreak)
            element.hasClass("page-number") -> add(element.toExportPageNumber())
            element.children().isNotEmpty() -> element.children().forEach { addExportBlocksFromElement(it, draftDir) }
            element.wholeText().trim().isNotBlank() -> add(element.toExportTextBlock(element.wholeText().trim(), TextBlockStyle.Body))
        }
    }

    private fun Element.toExportTextBlock(text: String, styleKind: TextBlockStyle): ExportBlock.Text {
        val style = parseStyle(attr("style"))
        return ExportBlock.Text(
            text = text,
            style = styleKind,
            backgroundColor = parseCssColor(style["background"] ?: style["background-color"]),
            textColor = parseCssColor(style["color"]),
            textAlign = style.toTextAlign(),
        )
    }

    private fun Element.toExportPageNumber(): ExportBlock.PageNumber {
        val style = parseStyle(attr("style"))
        return ExportBlock.PageNumber(
            backgroundColor = parseCssColor(style["background"] ?: style["background-color"]),
            textColor = parseCssColor(style["color"]),
            textAlign = style.toTextAlign(),
        )
    }

    private fun Map<String, String>.toTextAlign(): TextAlign = when (this["text-align"]?.lowercase(Locale.ROOT)) {
        "center" -> TextAlign.Center
        "right", "end" -> TextAlign.Right
        else -> TextAlign.Left
    }

    private fun exportTableRows(table: Element): List<List<ExportTableCell>> {
        val rawRows = table.select("tr").map { row ->
            row to row.select("th,td").map { cell -> cell to cell.wholeText().trim() }
        }.filterNot { (row, cells) ->
            row.attr("data-hide-if-empty") == "true" &&
                cells.any { (cell, _) -> cell.attr("contenteditable") == "true" } &&
                cells.filter { (cell, _) -> cell.attr("contenteditable") == "true" }.all { (_, text) -> text.isBlank() }
        }
        val hiddenColumns = rawRows.flatMap { (_, cells) ->
            cells.mapIndexedNotNull { index, (cell, _) -> index.takeIf { cell.attr("data-hide-column-if-empty") == "true" } }
        }.toSet().filter { index ->
            rawRows.all { (_, cells) -> cells.getOrNull(index)?.second.orEmpty().isBlank() }
        }.toSet()
        return rawRows.map { (_, cells) ->
            cells.mapIndexedNotNull { index, (cell, text) ->
                if (index in hiddenColumns || (cell.attr("data-hide-if-empty") == "true" && text.isBlank())) {
                    null
                } else {
                    cell.toExportTableCell(text)
                }
            }
        }.filter { it.isNotEmpty() }
    }

    private fun Element.toExportTableCell(text: String): ExportTableCell {
        val style = parseStyle(attr("style"))
        return ExportTableCell(
            text = text,
            colSpan = attr("colspan").toIntOrNull()?.coerceAtLeast(1) ?: 1,
            rowSpan = attr("rowspan").toIntOrNull()?.coerceAtLeast(1) ?: 1,
            backgroundColor = parseCssColor(style["background"] ?: style["background-color"]),
            textColor = parseCssColor(style["color"]),
            textAlign = when (style["text-align"]?.lowercase(Locale.ROOT)) {
                "center" -> TextAlign.Center
                "right", "end" -> TextAlign.Right
                else -> TextAlign.Left
            },
            bold = normalName() == "th" || style["font-weight"]?.contains("bold", ignoreCase = true) == true,
        )
    }

    private fun parseStyle(style: String): Map<String, String> =
        style.split(';')
            .mapNotNull { declaration ->
                val name = declaration.substringBefore(':').trim().lowercase(Locale.ROOT)
                val value = declaration.substringAfter(':', "").trim()
                if (name.isBlank() || value.isBlank()) null else name to value
            }
            .toMap()

    private fun parseCssColor(value: String?): Int? {
        val color = value?.trim()?.lowercase(Locale.ROOT)?.takeIf { it.isNotBlank() } ?: return null
        return runCatching {
            when {
                color.startsWith("#") -> Color.parseColor(color)
                color.startsWith("rgb") -> {
                    val parts = color.substringAfter('(').substringBefore(')').split(',').mapNotNull { it.trim().toFloatOrNull()?.roundToInt() }
                    if (parts.size >= 3) Color.rgb(parts[0].coerceIn(0, 255), parts[1].coerceIn(0, 255), parts[2].coerceIn(0, 255)) else null
                }
                color == "red" -> Color.RED
                color == "blue" -> Color.BLUE
                color == "green" -> Color.GREEN
                color == "yellow" -> Color.YELLOW
                color == "black" -> Color.BLACK
                color == "white" -> Color.WHITE
                color == "gray" || color == "grey" -> Color.GRAY
                else -> Color.parseColor(color)
            }
        }.getOrNull()
    }

    private fun MutableList<ExportBlock>.addPhotoExportBlock(element: Element, draftDir: File) {
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

    private fun blockText(element: Element): String? {
        val paragraphs = element.select("p")
        val text = if (paragraphs.isNotEmpty()) {
            paragraphs.joinToString("\n") { it.wholeText().trim() }
        } else {
            element.wholeText().trim()
        }
        return text.ifBlank { null }
    }

    private fun resolveAnyImage(draftDir: File, src: String): File? {
        if (src.startsWith("/")) return File(src)
        return resolveDraftImage(draftDir, src)
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

    private fun parseSafetyDocument(html: String, draftDir: File, profile: UserProfile): SafetyDocument? {
        val doc = Jsoup.parse(html)
        if (!doc.body().hasClass(SAFETY_CLASS)) return null
        val missingProfileInfo = profile.missingForSafetyTemplate()
        if (missingProfileInfo.isNotEmpty()) throw MissingProfileInfoException(missingProfileInfo)

        val dateText = doc.selectFirst(".inspection-row")?.wholeText()
            ?.substringAfter("Dátum:", "")
            ?.trim()
            ?.ifBlank { null }
            ?: ""
        val cooperationActions = doc.select(".cooperation-actions tr").mapNotNull { row ->
            val cells = row.select("td")
            if (cells.size < 2) return@mapNotNull null
            SafetyCheckItem(
                mark = cells[0].wholeText().trim().ifBlank { "X" },
                text = cells[1].wholeText().trim(),
            )
        }.ifEmpty {
            doc.select(".intro-body li").mapNotNull { item ->
                val whole = item.wholeText().trim()
                if (whole.isBlank()) return@mapNotNull null
                val mark = if (whole.endsWith("✓")) "✓" else "X"
                SafetyCheckItem(
                    mark = mark,
                    text = whole.removeSuffix("✓").removeSuffix("X").trim(),
                )
            }
        }.ifEmpty { defaultCooperationActions() }
        val checklistPages = doc.select(".safety-checklist-page").mapIndexed { pageIndex, page ->
            val rawRows = page.select(".checklist-table tr.checklist-item").mapNotNull { row ->
                val label = row.selectFirst(".checklist-label")?.wholeText()?.trim().orEmpty()
                if (label.isBlank()) return@mapNotNull null
                SafetyChecklistRow(
                    label = label,
                    value = row.selectFirst(".checklist-value")?.wholeText()?.trim().orEmpty(),
                    indented = row.hasClass("checklist-indent"),
                    group = row.hasClass("checklist-group"),
                )
            }
            SafetyChecklistPage(index = page.attr("data-checklist-page").toIntOrNull() ?: pageIndex + 1, rows = filterEmptyChecklistRows(rawRows))
        }
        val observations = doc.select(".safety-observation").mapIndexed { index, element ->
            val table = element.selectFirst(".observation-table")
            fun field(name: String): String =
                table?.selectFirst("[data-field=$name]")?.wholeText()?.trim().orEmpty()
            val source = element.selectFirst("img[src]")?.attr("src").orEmpty()
            SafetyObservation(
                index = element.attr("data-index").toIntOrNull() ?: index + 1,
                image = resolveDraftImage(draftDir, source)?.takeIf { it.isFile },
                source = source,
                riskScore = field("risk_score"),
                riskLevel = field("risk_level"),
                hazardSource = field("hazard_source"),
                hazardSituation = field("hazard_situation"),
                prevention = field("prevention"),
                deadline = field("deadline"),
                responsible = field("responsible"),
                sanction = field("sanction"),
                followUp = field("follow_up"),
            )
        }
        return SafetyDocument(
            dateText = dateText,
            profile = profile,
            signature = profileRepository.imageFile(profile.signaturePath),
            stamp = profileRepository.imageFile(profile.stampPath),
            checklistPages = checklistPages,
            cooperationActions = cooperationActions,
            observations = observations,
        )
    }

    private fun defaultCooperationActions(): List<SafetyCheckItem> = listOf(
        SafetyCheckItem("X", "beléptetés megoldása"),
        SafetyCheckItem("X", "generálkivitelező FMV a helyszínen"),
        SafetyCheckItem("X", "BEK bejárás heti 1 alkalommal"),
        SafetyCheckItem("X", "ellenőrzési tapasztalatok megküldése és a megelőzés számonkérése"),
        SafetyCheckItem("X", "különböző munkáltatók tevékenységének összehangolása építésvezetői közreműködéssel"),
        SafetyCheckItem("X", "napi tájékoztatók"),
    )

    private fun renderSafetyPdf(document: SafetyDocument, output: File): ExportStats {
        output.parentFile?.mkdirs()
        if (output.exists()) output.delete()

        val renderer = SafetyPdfRenderer(document)
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

    private fun filterEmptyChecklistRows(rows: List<SafetyChecklistRow>): List<SafetyChecklistRow> = buildList {
        var index = 0
        while (index < rows.size) {
            val row = rows[index]
            if (row.group) {
                val childRows = mutableListOf<SafetyChecklistRow>()
                var childIndex = index + 1
                while (childIndex < rows.size && rows[childIndex].indented) {
                    val child = rows[childIndex]
                    if (child.value.isNotBlank()) childRows.add(child)
                    childIndex++
                }
                if (childRows.isNotEmpty()) {
                    add(row)
                    addAll(childRows)
                }
                index = childIndex
            } else {
                if (row.value.isNotBlank()) add(row)
                index++
            }
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

    private class SafetyPdfRenderer(
        private val document: SafetyDocument,
    ) {
        private val pdf = PdfDocument()
        private var page: PdfDocument.Page? = null
        private var pageNumber = 0
        private var imageCount = 0
        private var y = SAFETY_MARGIN_PT

        private val textPaint = textPaint(size = 12f, color = Color.BLACK)
        private val boldPaint = textPaint(size = 12f, color = Color.BLACK, typeface = Typeface.DEFAULT_BOLD)
        private val headerPaint = textPaint(size = 16f, color = Color.BLACK, typeface = Typeface.DEFAULT_BOLD)
        private val smallPaint = textPaint(size = 10.5f, color = Color.BLACK)
        private val redBoldPaint = textPaint(size = 12f, color = Color.RED, typeface = Typeface.DEFAULT_BOLD)
        private val orangeBoldPaint = textPaint(size = 12f, color = Color.rgb(245, 168, 0), typeface = Typeface.DEFAULT_BOLD)
        private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            style = Paint.Style.STROKE
            strokeWidth = 0.8f
        }
        private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
        private val imagePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            isFilterBitmap = true
            isDither = true
        }

        fun render(): ExportStats {
            startPage()
            drawSafetyHeader(null)
            drawIntro()
            finishCurrentPage()
            document.checklistPages.forEach { checklistPage ->
                startPage()
                drawSafetyHeader(null)
                drawChecklistPage(checklistPage)
                finishCurrentPage()
            }
            document.observations.forEach { observation ->
                startPage()
                drawSafetyHeader("${observation.index}.")
                drawObservation(observation)
                finishCurrentPage()
            }
            return ExportStats(pageCount = pageNumber, imageCount = imageCount)
        }

        fun writeTo(output: FileOutputStream) {
            pdf.writeTo(output)
        }

        fun close() {
            runCatching { finishCurrentPage() }
            pdf.close()
        }

        private fun drawChecklistPage(checklistPage: SafetyChecklistPage) {
            y += 4f
            drawChecklistRows(checklistPage.rows)
            drawPageNumber()
        }

        private fun drawChecklistRows(rows: List<SafetyChecklistRow>) {
            val left = SAFETY_MARGIN_PT + 22f
            val labelWidth = 380f
            val valueWidth = SAFETY_WIDTH_PT - 44f - labelWidth
            rows.forEach { row ->
                if (y + 22f > PAGE_BOTTOM_PT) return@forEach
                val height = when {
                    row.label.length > 100 -> 34f
                    row.label.length > 58 -> 28f
                    else -> 22f
                }
                if (row.group) {
                    drawCell(RectF(left, y, left + labelWidth + valueWidth, y + height), row.label, smallPaint, fill = Color.rgb(232, 232, 232))
                } else {
                    drawCell(RectF(left, y, left + labelWidth, y + height), row.label, smallPaint, textInset = if (row.indented) 24f else 4f)
                    drawCell(RectF(left + labelWidth, y, left + labelWidth + valueWidth, y + height), row.value, textPaint)
                }
                y += height
            }
        }

        private fun drawIntro() {
            y += 14f
            drawText("Generálkivitelező: ${document.profile.companyName}", boldPaint, SAFETY_MARGIN_PT + 22f, y, SAFETY_WIDTH_PT - 44f)
            y += 28f
            drawText("Az ellenőrzést végezte:", boldPaint, SAFETY_MARGIN_PT + 22f, y, 170f)
            drawText(document.profile.name, boldPaint, SAFETY_MARGIN_PT + 215f, y, 150f)
            drawText("Dátum: ${document.dateText}", boldPaint, SAFETY_MARGIN_PT + 395f, y, 150f)
            y += 30f
            drawText("Megfelelt: ✓        Nem felelt meg: X", textPaint, SAFETY_MARGIN_PT + 22f, y, SAFETY_WIDTH_PT - 44f)
            y += 28f
            drawText("Az együttműködés előmozdítása érdekében tett intézkedések", boldPaint, SAFETY_MARGIN_PT + 22f, y, SAFETY_WIDTH_PT - 44f)
            y += 26f
            drawCooperationActions()
            y += 12f
            drawText("Munkaterület állapota a mai napon", boldPaint, SAFETY_MARGIN_PT + 22f, y, SAFETY_WIDTH_PT - 44f)
            y += 28f
            drawText("Értékelés módja kockázat alapú:", textPaint, SAFETY_MARGIN_PT + 22f, y, SAFETY_WIDTH_PT - 44f)
            y += 24f
            drawRiskMatrix()
            y += 24f
            drawRiskLevels()
            y += 18f
            drawProfileImages()
            drawPageNumber()
        }

        private fun drawCooperationActions() {
            val left = SAFETY_MARGIN_PT + 40f
            val widths = floatArrayOf(42f, SAFETY_WIDTH_PT - 102f)
            val rowHeights = document.cooperationActions.map { item ->
                if (item.text.length > 70) 34f else 22f
            }
            document.cooperationActions.forEachIndexed { index, item ->
                var x = left
                val height = rowHeights[index]
                drawCell(RectF(x, y, x + widths[0], y + height), item.mark, boldPaint, center = true)
                x += widths[0]
                drawCell(RectF(x, y, x + widths[1], y + height), item.text, textPaint)
                y += height
            }
        }

        private fun drawRiskMatrix() {
            val left = SAFETY_MARGIN_PT + 22f
            val top = y
            val widths = floatArrayOf(88f, 88f, 88f, 88f, 88f, 88f)
            val heights = floatArrayOf(68f, 22f, 34f, 22f, 34f, 22f)
            val rows = listOf(
                listOf("következmény\n\nvalószínűség", "nincs hatás,\nvagy a hatás\njelentéktelen", "könnyebb\nsérülést okoz", "jelentős\nsérülést okoz", "súlyos sérülést\nokoz", "fokozottan\nsúlyos vagy\nhalálos\nkimenetelű"),
                listOf("valószínűtlen", "1", "2", "3", "4", "5"),
                listOf("inkább nem\nfordul elő", "2", "4", "6", "8", "10"),
                listOf("lehetséges", "3", "6", "9", "12", "15"),
                listOf("valószínűleg\nelőfordul", "4", "8", "12", "16", "20"),
                listOf("elkerülhetetlen", "5", "10", "15", "20", "25"),
            )
            rows.forEachIndexed { rowIndex, row ->
                var x = left
                row.forEachIndexed { col, value ->
                    val color = when {
                        rowIndex == 0 || col == 0 -> Color.WHITE
                        value.toIntOrNull() in 1..4 -> COLOR_LOW
                        value.toIntOrNull() in 5..10 -> COLOR_MEDIUM
                        else -> COLOR_HIGH
                    }
                    drawCell(RectF(x, y, x + widths[col], y + heights[rowIndex]), value, smallPaint, color, center = col > 0 && rowIndex > 0)
                    x += widths[col]
                }
                y += heights[rowIndex]
            }
            y = top + heights.sum()
        }

        private fun drawRiskLevels() {
            val left = SAFETY_MARGIN_PT + 22f
            val widths = floatArrayOf(92f, 128f, 306f)
            val rows = listOf(
                Triple("kockázati szint", "végrehajtási határidő", ""),
                Triple("magas", "azonnali", "intézkedésig munka nem végezhető"),
                Triple("közepes", "rövid határidő", "intézkedésig 1-3 nap türelmi idő engedélyezett, addig munka csak feltételekkel végezhető"),
                Triple("alacsony", "hosszabb határidő", "a munkavégzés megengedett, intézkedni a következő bejárás, karbantartás, javítás alkalmáig kell (1 hét)"),
            )
            rows.forEachIndexed { index, row ->
                val height = if (index >= 2) 34f else 20f
                var x = left
                val fill = when (index) {
                    1 -> COLOR_HIGH
                    2 -> COLOR_MEDIUM
                    3 -> COLOR_LOW
                    else -> Color.WHITE
                }
                drawCell(RectF(x, y, x + widths[0], y + height), row.first, if (index == 0) boldPaint else textPaint, fill)
                x += widths[0]
                drawCell(RectF(x, y, x + widths[1], y + height), row.second, if (index == 0) boldPaint else textPaint)
                x += widths[1]
                drawCell(RectF(x, y, x + widths[2], y + height), row.third, textPaint)
                y += height
            }
        }

        private fun drawProfileImages() {
            val signature = document.signature
            val stamp = document.stamp
            val top = y
            signature?.let { image ->
                decodeImageBounds(image)?.let { bounds ->
                    drawBitmap(image, bounds, readOrientation(image), RectF(SAFETY_MARGIN_PT + 60f, top, SAFETY_MARGIN_PT + 210f, top + 55f))
                }
            }
            stamp?.let { image ->
                decodeImageBounds(image)?.let { bounds ->
                    drawBitmap(image, bounds, readOrientation(image), RectF(SAFETY_MARGIN_PT + 330f, top, SAFETY_MARGIN_PT + 480f, top + 70f))
                }
            }
            y += 78f
        }

        private fun drawObservation(observation: SafetyObservation) {
            val tableHeight = OBSERVATION_TABLE_HEIGHT_PT
            val imageTop = y + 12f
            val maxImageHeight = (PAGE_BOTTOM_PT - imageTop - tableHeight - 10f).coerceAtLeast(110f)
            val imageBottom = drawObservationImage(observation, imageTop, maxImageHeight)
            y = imageBottom + 8f
            drawObservationTable(observation)
            drawPageNumber()
        }

        private fun drawObservationImage(observation: SafetyObservation, top: Float, maxHeight: Float): Float {
            val image = observation.image ?: return top
            val bounds = decodeImageBounds(image) ?: return top
            val orientation = readOrientation(image)
            val size = bounds.displaySize(orientation)
            var width = SAFETY_WIDTH_PT
            var height = width * size.height.toFloat() / size.width.toFloat()
            if (height > maxHeight) {
                val scale = maxHeight / height
                width *= scale
                height *= scale
            }
            val left = SAFETY_MARGIN_PT + (SAFETY_WIDTH_PT - width) / 2f
            val target = RectF(left, top, left + width, top + height)
            if (drawBitmap(image, bounds, orientation, target)) imageCount += 1
            return target.bottom
        }

        private fun drawObservationTable(observation: SafetyObservation) {
            val left = SAFETY_MARGIN_PT
            val labelWidth = 112f
            val valueWidth = SAFETY_WIDTH_PT - labelWidth
            fun row(label: String, value: String, height: Float) {
                drawCell(RectF(left, y, left + labelWidth, y + height), label, boldPaint)
                drawCell(RectF(left + labelWidth, y, left + labelWidth + valueWidth, y + height), value, textPaint)
                y += height
            }
            drawCell(RectF(left, y, left + labelWidth, y + 24f), "kockázati szint", boldPaint)
            drawCell(RectF(left + labelWidth, y, left + labelWidth + 175f, y + 24f), observation.riskScore, textPaint)
            val riskPaint = if (observation.riskLevel.equals("magas", true)) redBoldPaint else if (observation.riskLevel.equals("közepes", true)) orangeBoldPaint else boldPaint
            drawCell(RectF(left + labelWidth + 175f, y, left + labelWidth + valueWidth, y + 24f), observation.riskLevel, riskPaint)
            y += 24f
            row("veszély forrása", observation.hazardSource, 38f)
            row("veszélyhelyzet", observation.hazardSituation, 48f)
            row("megelőzés", observation.prevention, 90f)
            row("határidő", observation.deadline, 32f)
            row("felelős", observation.responsible, 32f)
            row("szankció", observation.sanction, 28f)
            row("visszaellenőrzés", observation.followUp, 44f)
        }

        private fun drawSafetyHeader(index: String?) {
            val widths = if (index == null) {
                floatArrayOf(148f, 280f, 167f)
            } else {
                floatArrayOf(140f, 265f, 140f, 50f)
            }
            val values = if (index == null) {
                listOf("Párta köz 4.\nBET", "MUNKAVÉDELMI BEJÁRÁS\nELLENŐRZÉSI jegyzőkönyv", "7/B melléklet")
            } else {
                listOf("Párta köz 4.\nBET", "MUNKAVÉDELMI BEJÁRÁS\nELLENŐRZÉSI jegyzőkönyv", "7/B melléklet", index)
            }
            var x = 0f
            values.forEachIndexed { i, value ->
                drawCell(RectF(x, SAFETY_MARGIN_PT, x + widths[i], SAFETY_MARGIN_PT + 42f), value, headerPaint, Color.rgb(232, 232, 232), center = true)
                x += widths[i]
            }
            y = SAFETY_MARGIN_PT + 58f
        }

        private fun drawCell(
            rect: RectF,
            text: String,
            paint: TextPaint,
            fill: Int = Color.WHITE,
            center: Boolean = false,
            textInset: Float = 4f,
        ) {
            fillPaint.color = fill
            canvas.drawRect(rect, fillPaint)
            canvas.drawRect(rect, borderPaint)
            val layout = staticLayout(text, paint, (rect.width() - textInset - 4f).roundToInt().coerceAtLeast(1))
            val textY = if (center) rect.top + ((rect.height() - layout.height) / 2f).coerceAtLeast(2f) else rect.top + 3f
            canvas.save()
            canvas.clipRect(rect)
            canvas.translate(rect.left + textInset, textY)
            layout.draw(canvas)
            canvas.restore()
        }

        private fun drawText(text: String, paint: TextPaint, left: Float, top: Float, width: Float) {
            val layout = staticLayout(text, paint, width.roundToInt())
            drawStaticLayout(layout, left, top)
        }

        private fun drawStaticLayout(layout: StaticLayout, left: Float, top: Float) {
            canvas.save()
            canvas.translate(left, top)
            layout.draw(canvas)
            canvas.restore()
        }

        private fun drawBitmap(image: File, bounds: ImageBounds, orientation: Int, target: RectF): Boolean {
            val targetPixels = targetPixelSize(target.width(), target.height(), orientation)
            val options = BitmapFactory.Options().apply {
                inSampleSize = calculateInSampleSize(bounds.width, bounds.height, targetPixels.width, targetPixels.height)
            }
            val decoded = BitmapFactory.decodeFile(image.absolutePath, options) ?: return false
            var oriented: Bitmap? = null
            var scaled: Bitmap? = null
            return try {
                oriented = applyOrientation(decoded, orientation)
                scaled = scaleForPdf(oriented, targetPixels)
                canvas.drawBitmap(scaled, null, target, imagePaint)
                true
            } finally {
                if (scaled != null && scaled !== oriented) scaled.recycle()
                if (oriented != null && oriented !== decoded) oriented.recycle()
                decoded.recycle()
            }
        }

        private fun targetPixelSize(widthPt: Float, heightPt: Float, orientation: Int): ImageSize {
            val displayWidth = (widthPt * IMAGE_DPI / POINTS_PER_INCH).roundToInt().coerceAtLeast(1)
            val displayHeight = (heightPt * IMAGE_DPI / POINTS_PER_INCH).roundToInt().coerceAtLeast(1)
            return if (swapsAxes(orientation)) ImageSize(displayHeight, displayWidth) else ImageSize(displayWidth, displayHeight)
        }

        private fun scaleForPdf(bitmap: Bitmap, target: ImageSize): Bitmap {
            val scale = min(target.width.toFloat() / bitmap.width.toFloat(), target.height.toFloat() / bitmap.height.toFloat())
            if (scale >= 0.95f) return bitmap
            return Bitmap.createScaledBitmap(
                bitmap,
                (bitmap.width * scale).roundToInt().coerceAtLeast(1),
                (bitmap.height * scale).roundToInt().coerceAtLeast(1),
                true,
            )
        }

        private fun drawPageNumber() {
            drawText(pageNumber.toString(), textPaint, PAGE_WIDTH_PT / 2f - 8f, PAGE_HEIGHT_PT - 34f, 24f)
        }

        private fun startPage() {
            finishCurrentPage()
            pageNumber += 1
            page = pdf.startPage(PdfDocument.PageInfo.Builder(PAGE_WIDTH_PT.roundToInt(), PAGE_HEIGHT_PT.roundToInt(), pageNumber).create())
            y = SAFETY_MARGIN_PT
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

    private class NativePdfRenderer(
        private val document: ExportDocument,
    ) {
        private val pdf = PdfDocument()
        private var page: PdfDocument.Page? = null
        private var pageNumber = 0
        private var y = PAGE_MARGIN_PT
        private val topContentY: Float get() = PAGE_MARGIN_PT + if (document.headerBlocks.isEmpty()) 0f else REPEAT_HEADER_HEIGHT_PT
        private val bottomContentY: Float get() = PAGE_BOTTOM_PT - if (document.footerBlocks.isEmpty()) 0f else REPEAT_FOOTER_HEIGHT_PT
        private var allowPagination = true
        private var repeatBottomY = PAGE_BOTTOM_PT
        private val currentBottomY: Float get() = if (allowPagination) bottomContentY else repeatBottomY
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
        private val tableFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
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
                    is ExportBlock.Table -> drawTableBlock(block)
                    is ExportBlock.ProfileImage -> drawProfileImageBlock(block)
                    is ExportBlock.PageBreak -> startPage()
                    is ExportBlock.PageNumber -> drawPageNumber(block, spacingAfter = 10f)
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
            val basePaint = when (block.style) {
                TextBlockStyle.Body -> bodyPaint
                TextBlockStyle.Date -> datePaint
            }
            val paint = TextPaint(basePaint).apply { block.textColor?.let { color = it } }
            drawWrappedText(block.text, paint, spacingAfter = 10f, backgroundColor = block.backgroundColor, textAlign = block.textAlign)
        }

        private fun drawPageNumber(block: ExportBlock.PageNumber, spacingAfter: Float) {
            val paint = TextPaint(bodyPaint).apply { block.textColor?.let { color = it } }
            drawWrappedText(pageNumber.toString(), paint, spacingAfter = spacingAfter, backgroundColor = block.backgroundColor, textAlign = block.textAlign)
        }

        private fun drawTableBlock(block: ExportBlock.Table) {
            val rows = block.rows.filter { it.isNotEmpty() }
            if (rows.isEmpty()) return
            val columns = rows.maxOf { row -> row.sumOf { it.colSpan } }.coerceAtLeast(1)
            val colWidth = CONTENT_WIDTH_PT / columns
            val rowHeight = 28f
            val rowSpanContinuations = IntArray(columns)
            rows.forEach { row ->
                if (y + rowHeight > currentBottomY) {
                    if (allowPagination) startPage() else return@forEach
                }
                var column = 0
                row.forEach { cell ->
                    while (column < columns && rowSpanContinuations[column] > 0) column++
                    if (column >= columns) return@forEach
                    val spanColumns = cell.colSpan.coerceAtLeast(1).coerceAtMost(columns - column)
                    val spanRows = cell.rowSpan.coerceAtLeast(1)
                    val rect = RectF(
                        PAGE_MARGIN_PT + column * colWidth,
                        y,
                        PAGE_MARGIN_PT + (column + spanColumns) * colWidth,
                        y + rowHeight * spanRows,
                    )
                    drawExportTableCell(rect, cell)
                    if (spanRows > 1) {
                        for (spanColumn in column until (column + spanColumns)) {
                            rowSpanContinuations[spanColumn] = max(rowSpanContinuations[spanColumn], spanRows - 1)
                        }
                    }
                    column += spanColumns
                }
                for (index in rowSpanContinuations.indices) {
                    if (rowSpanContinuations[index] > 0) rowSpanContinuations[index] -= 1
                }
                y += rowHeight
            }
            y += 12f
        }

        private fun drawExportTableCell(rect: RectF, cell: ExportTableCell) {
            tableFillPaint.color = cell.backgroundColor ?: Color.WHITE
            canvas.drawRect(rect, tableFillPaint)
            canvas.drawRect(rect, placeholderPaint)
            val paint = TextPaint(if (cell.bold) datePaint else bodyPaint).apply {
                color = cell.textColor ?: (if (cell.bold) datePaint.color else bodyPaint.color)
            }
            val alignment = when (cell.textAlign) {
                TextAlign.Center -> Layout.Alignment.ALIGN_CENTER
                TextAlign.Right -> Layout.Alignment.ALIGN_OPPOSITE
                TextAlign.Left -> Layout.Alignment.ALIGN_NORMAL
            }
            val layout = staticLayout(
                text = cell.text.replace(PAGE_NUMBER_TOKEN, pageNumber.toString()),
                paint = paint,
                width = (rect.width() - 8f).roundToInt().coerceAtLeast(1),
                alignment = alignment,
            )
            canvas.save()
            canvas.clipRect(rect)
            canvas.translate(rect.left + 4f, rect.top + 4f)
            layout.draw(canvas)
            canvas.restore()
        }

        private fun drawProfileImageBlock(block: ExportBlock.ProfileImage) {
            val image = block.image ?: run {
                if (block.label.isNotBlank()) drawWrappedText(block.label, bodyPaint, spacingAfter = 12f)
                return
            }
            val bounds = decodeImageBounds(image) ?: return
            val width = if (block.signature) 170f else 140f
            val height = if (block.signature) 70f else 95f
            if (y + height + 24f > currentBottomY) {
                if (allowPagination) startPage() else return
            }
            val left = PAGE_WIDTH_PT - PAGE_MARGIN_PT - width
            if (drawBitmap(image, bounds, readOrientation(image), RectF(left, y, left + width, y + height))) imageCount += 1
            y += height
            if (block.signature) {
                canvas.drawLine(left, y, left + width, y, linePaint)
                y += 4f
                if (block.label.isNotBlank()) drawWrappedText(block.label, bodyPaint, spacingAfter = 10f)
            } else {
                y += 12f
            }
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
            val maxImageHeight = (currentBottomY - topContentY) - captionGap - captionHeight
            if (drawHeight > maxImageHeight) {
                val scale = maxImageHeight / drawHeight
                drawWidth *= scale
                drawHeight *= scale
            }

            val blockHeight = drawHeight + captionGap + captionHeight + 12f
            if (y + blockHeight > currentBottomY && y > topContentY) {
                if (allowPagination) startPage() else return
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
            if (y + placeholderHeight > currentBottomY && y > topContentY) {
                if (allowPagination) startPage() else return
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

        private fun drawWrappedText(
            text: String,
            paint: TextPaint,
            spacingAfter: Float,
            backgroundColor: Int? = null,
            textAlign: TextAlign = TextAlign.Left,
        ) {
            val alignment = when (textAlign) {
                TextAlign.Center -> Layout.Alignment.ALIGN_CENTER
                TextAlign.Right -> Layout.Alignment.ALIGN_OPPOSITE
                TextAlign.Left -> Layout.Alignment.ALIGN_NORMAL
            }
            val layout = staticLayout(text, paint, CONTENT_WIDTH_PT.roundToInt(), alignment = alignment)
            var line = 0
            while (line < layout.lineCount) {
                if (y >= currentBottomY) {
                    if (allowPagination) startPage() else break
                }

                val chunkTop = layout.getLineTop(line)
                var endLine = line
                val available = currentBottomY - y
                while (endLine < layout.lineCount &&
                    layout.getLineBottom(endLine) - chunkTop <= available
                ) {
                    endLine += 1
                }

                if (endLine == line) {
                    if (allowPagination) {
                        startPage()
                        continue
                    } else {
                        break
                    }
                }

                val chunkBottom = layout.getLineBottom(endLine - 1)
                backgroundColor?.let { color ->
                    tableFillPaint.color = color
                    canvas.drawRect(PAGE_MARGIN_PT, y, PAGE_MARGIN_PT + CONTENT_WIDTH_PT, y + (chunkBottom - chunkTop).toFloat(), tableFillPaint)
                }
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
                    if (allowPagination) startPage() else break
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
            drawRepeatingBlocks(document.headerBlocks, PAGE_MARGIN_PT, topContentY, pageNumber)
            drawRepeatingBlocks(document.footerBlocks, PAGE_BOTTOM_PT - REPEAT_FOOTER_HEIGHT_PT + 8f, PAGE_BOTTOM_PT, pageNumber)
            y = topContentY
        }

        private fun drawRepeatingBlocks(blocks: List<ExportBlock>, top: Float, bottom: Float, currentPageNumber: Int) {
            if (blocks.isEmpty()) return
            val originalY = y
            val originalAllowPagination = allowPagination
            val originalRepeatBottomY = repeatBottomY
            y = top
            allowPagination = false
            repeatBottomY = bottom
            canvas.save()
            canvas.clipRect(PAGE_MARGIN_PT, top, PAGE_MARGIN_PT + CONTENT_WIDTH_PT, bottom)
            blocks.forEach { block ->
                when (block) {
                    is ExportBlock.Text -> drawTextBlock(block)
                    is ExportBlock.Table -> drawTableBlock(block)
                    is ExportBlock.PageNumber -> {
                        val paint = TextPaint(bodyPaint).apply { block.textColor?.let { color = it } }
                        drawWrappedText(currentPageNumber.toString(), paint, spacingAfter = 4f, backgroundColor = block.backgroundColor, textAlign = block.textAlign)
                    }
                    is ExportBlock.ProfileImage, is ExportBlock.Photo, is ExportBlock.PageBreak -> Unit
                }
            }
            canvas.restore()
            allowPagination = originalAllowPagination
            repeatBottomY = originalRepeatBottomY
            y = originalY
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
        val headerBlocks: List<ExportBlock>,
        val footerBlocks: List<ExportBlock>,
        val blocks: List<ExportBlock>,
    )

    private data class SafetyDocument(
        val dateText: String,
        val profile: UserProfile,
        val signature: File?,
        val stamp: File?,
        val checklistPages: List<SafetyChecklistPage>,
        val cooperationActions: List<SafetyCheckItem>,
        val observations: List<SafetyObservation>,
    )

    private data class SafetyChecklistPage(
        val index: Int,
        val rows: List<SafetyChecklistRow>,
    )

    private data class SafetyChecklistRow(
        val label: String,
        val value: String,
        val indented: Boolean,
        val group: Boolean,
    )

    private data class SafetyCheckItem(
        val mark: String,
        val text: String,
    )

    private data class SafetyObservation(
        val index: Int,
        val image: File?,
        val source: String,
        val riskScore: String,
        val riskLevel: String,
        val hazardSource: String,
        val hazardSituation: String,
        val prevention: String,
        val deadline: String,
        val responsible: String,
        val sanction: String,
        val followUp: String,
    )

    private sealed class ExportBlock {
        data class Text(
            val text: String,
            val style: TextBlockStyle,
            val backgroundColor: Int? = null,
            val textColor: Int? = null,
            val textAlign: TextAlign = TextAlign.Left,
        ) : ExportBlock()

        data class Photo(
            val image: File?,
            val source: String,
            val caption: String?,
        ) : ExportBlock()

        data class Table(val rows: List<List<ExportTableCell>>) : ExportBlock()

        data class ProfileImage(
            val image: File?,
            val label: String,
            val signature: Boolean,
        ) : ExportBlock()

        data object PageBreak : ExportBlock()
        data class PageNumber(
            val backgroundColor: Int? = null,
            val textColor: Int? = null,
            val textAlign: TextAlign = TextAlign.Left,
        ) : ExportBlock()
    }

    private data class ExportTableCell(
        val text: String,
        val colSpan: Int = 1,
        val rowSpan: Int = 1,
        val backgroundColor: Int? = null,
        val textColor: Int? = null,
        val textAlign: TextAlign = TextAlign.Left,
        val bold: Boolean = false,
    )

    private enum class TextAlign { Left, Center, Right }

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
        const val SAFETY_CLASS = "safety-walkthrough"
        const val DEFAULT_TITLE = "Jegyzőkönyv"
        const val LEGACY_EXPORT_IMAGES_DIR = ".pdf_images"
        const val PAGE_NUMBER_TOKEN = "{{oldalszam}}"

        const val POINTS_PER_INCH = 72f
        const val IMAGE_DPI = 180f
        const val PAGE_WIDTH_PT = 595f
        const val PAGE_HEIGHT_PT = 842f
        const val PAGE_MARGIN_PT = 42f
        const val CONTENT_WIDTH_PT = PAGE_WIDTH_PT - (PAGE_MARGIN_PT * 2f)
        const val USABLE_HEIGHT_PT = PAGE_HEIGHT_PT - (PAGE_MARGIN_PT * 2f)
        const val PAGE_BOTTOM_PT = PAGE_HEIGHT_PT - PAGE_MARGIN_PT
        const val REPEAT_HEADER_HEIGHT_PT = 56f
        const val REPEAT_FOOTER_HEIGHT_PT = 44f
        const val SAFETY_MARGIN_PT = 42f
        const val SAFETY_WIDTH_PT = PAGE_WIDTH_PT - (SAFETY_MARGIN_PT * 2f)
        const val OBSERVATION_TABLE_HEIGHT_PT = 310f
        const val COLOR_LOW = 0xFF92D050.toInt()
        const val COLOR_MEDIUM = 0xFFFFFF00.toInt()
        const val COLOR_HIGH = 0xFFFF0000.toInt()

        fun textPaint(
            size: Float,
            color: Int,
            typeface: Typeface = Typeface.DEFAULT,
        ): TextPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color
            textSize = size
            this.typeface = typeface
        }

        fun staticLayout(
            text: String,
            paint: TextPaint,
            width: Int,
            alignment: Layout.Alignment = Layout.Alignment.ALIGN_NORMAL,
        ): StaticLayout =
            StaticLayout.Builder
                .obtain(text, 0, text.length, paint, width)
                .setAlignment(alignment)
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
