package hu.gc.jegyzokonyv.domain.html

import hu.gc.jegyzokonyv.data.profile.UserProfile
import hu.gc.jegyzokonyv.domain.model.TableAxisSettings
import hu.gc.jegyzokonyv.domain.model.TableCellSettings
import hu.gc.jegyzokonyv.domain.model.TemplateBlock
import hu.gc.jegyzokonyv.domain.model.TemplateContent
import hu.gc.jegyzokonyv.domain.model.TemplateKind
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Entities
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class JsoupHtmlEngine @Inject constructor() : HtmlEngine {

    override fun renderTemplate(
        content: TemplateContent,
        title: String,
        todayIso: String,
        profile: UserProfile?,
    ): String {
        if (content.kind == TemplateKind.SafetyWalkthrough) {
            return renderSafetyWalkthrough(content, title, todayIso, profile)
        }

        val doc = parse(wrapperHtml(title, showHeading = content.title.isNotBlank()))
        val container = ensureContentContainer(doc)
        content.blocks.forEach { block -> appendTemplateBlock(container, block, todayIso, profile) }
        return render(doc)
    }

    override fun setTitle(html: String, title: String): String {
        val doc = parse(html)
        doc.selectFirst("h1")?.text(title)
        doc.selectFirst("title")?.text(title)
        return render(doc)
    }

    override fun appendPhotoBlock(html: String, relativeImagePath: String, caption: String?): String {
        val doc = parse(html)
        if (doc.body().hasClass(SAFETY_CLASS)) {
            appendSafetyObservation(doc, relativeImagePath)
            return render(doc)
        }

        val content = ensureImagesContainer(doc) ?: ensureContentContainer(doc)
        val template = content.selectFirst(".image-page-template[data-image-page-template=true]")
        if (template != null) {
            val page = template.clone()
                .removeClass("image-page-template")
                .addClass("image-page")
                .removeAttr("data-image-page-template")
                .removeAttr("style")
            page.select("[data-image-component=true]").forEach { imageBlock ->
                imageBlock.selectFirst("img")?.attr("src", relativeImagePath)
                val captionElement = imageBlock.selectFirst(".image-caption") ?: imageBlock.selectFirst("p")
                if (!caption.isNullOrBlank()) captionElement?.text(caption) else captionElement?.remove()
            }
            content.appendChild(page)
        } else {
            val block = content.appendElement("div").addClass("photo-block")
            block.appendElement("img").attr("src", relativeImagePath)
            if (!caption.isNullOrBlank()) {
                block.appendElement("p").text(caption)
            }
        }
        return render(doc)
    }

    override fun appendTextBlock(html: String, text: String): String {
        val doc = parse(html)
        val content = ensureContentContainer(doc)
        val block = content.appendElement("div").addClass("text-block")
        block.appendElement("p").text(text)
        return render(doc)
    }

    private fun appendTemplateBlock(parent: org.jsoup.nodes.Element, block: TemplateBlock, todayIso: String, profile: UserProfile?) {
        when (block) {
            is TemplateBlock.Text -> {
                val div = parent.appendElement("div").addClass("text-block")
                applyBlockStyle(div, block.settings)
                div.appendElement("p").text(block.text)
            }
            is TemplateBlock.Date -> {
                val div = parent.appendElement("div").addClass("date-block").text(todayIso)
                applyBlockStyle(div, block.settings)
            }
            is TemplateBlock.Table -> appendEditableTable(parent, block)
            is TemplateBlock.Signature -> appendSignature(parent, profile)
            is TemplateBlock.Stamp -> appendStamp(parent, profile)
            is TemplateBlock.Images -> appendImagesAnchor(parent, block, todayIso, profile)
            is TemplateBlock.Image -> appendImageComponent(parent)
            is TemplateBlock.PageBreak -> appendPageBreak(parent)
            is TemplateBlock.PageNumber -> {
                val span = parent.appendElement("span").addClass("page-number").text("1")
                applyBlockStyle(span, block.settings, forceBlock = true)
            }
            is TemplateBlock.Header -> {
                val div = parent.appendElement("div").addClass("repeat-header")
                block.blocks.forEach { appendTemplateBlock(div, it, todayIso, profile) }
            }
            is TemplateBlock.Footer -> {
                val div = parent.appendElement("div").addClass("repeat-footer")
                block.blocks.forEach { appendTemplateBlock(div, it, todayIso, profile) }
            }
            is TemplateBlock.Html -> appendHtmlBlock(parent, block)
        }
    }

    private fun applyBlockStyle(element: org.jsoup.nodes.Element, settings: TableCellSettings, forceBlock: Boolean = false) {
        val styles = buildList {
            if (forceBlock) add("display:block")
            if (settings.backgroundColor.isNotBlank()) add("background:${settings.backgroundColor}")
            if (settings.textColor.isNotBlank()) add("color:${settings.textColor}")
            if (settings.textAlign.isNotBlank()) add("text-align:${settings.textAlign}")
        }
        if (styles.isNotEmpty()) element.attr("style", styles.joinToString(";"))
    }

    private fun appendEditableTable(parent: org.jsoup.nodes.Element, block: TemplateBlock.Table) {
        val table = parent.appendElement("table")
            .addClass("editable-table")
            .attr("data-template-block-id", block.id)
        val rows = block.rows.coerceIn(1, 50)
        val columns = block.columns.coerceIn(1, 20)
        val skipped = mutableSetOf<Pair<Int, Int>>()
        repeat(rows) { rowIndex ->
            val row = table.appendElement("tr")
            val rowSettings = block.rowSettings.getOrNull(rowIndex) ?: TableAxisSettings()
            if (rowSettings.hideIfEmpty) row.attr("data-hide-if-empty", "true")
            val rowMergeColumns = if (rowSettings.mergeAll) (0 until columns).filter { col -> !block.tableCellSettings(rowIndex, col).toggleCheck } else emptyList()
            repeat(columns) { colIndex ->
                if (rowIndex to colIndex in skipped) return@repeat
                val columnSettings = block.columnSettings.getOrNull(colIndex) ?: TableAxisSettings()
                val cellSettings = block.tableCellSettings(rowIndex, colIndex)
                if (rowMergeColumns.isNotEmpty() && colIndex in rowMergeColumns && colIndex != rowMergeColumns.first()) return@repeat
                var colSpan = 1
                var rowSpan = 1
                when {
                    rowMergeColumns.isNotEmpty() && colIndex == rowMergeColumns.first() -> {
                        colSpan = rowMergeColumns.size
                        rowMergeColumns.drop(1).forEach { skipped += rowIndex to it }
                    }
                    columnSettings.mergeAll && !cellSettings.toggleCheck && rowIndex == 0 && (0 until rows).none { block.tableCellSettings(it, colIndex).toggleCheck } -> {
                        rowSpan = rows
                        (1 until rows).forEach { skipped += it to colIndex }
                    }
                    cellSettings.mergeRight && !cellSettings.toggleCheck && colIndex + 1 < columns && !block.tableCellSettings(rowIndex, colIndex + 1).toggleCheck -> {
                        colSpan = 2
                        skipped += rowIndex to (colIndex + 1)
                    }
                }
                val cell = if (block.hasHeaderColumn && colIndex == 0) row.appendElement("th") else row.appendElement("td")
                if (colSpan > 1) cell.attr("colspan", colSpan.toString())
                if (rowSpan > 1) cell.attr("rowspan", rowSpan.toString())
                val editable = !cellSettings.toggleCheck && (cellSettings.editable ?: rowSettings.editable ?: columnSettings.editable ?: true)
                var background = cellSettings.backgroundColor.ifBlank { rowSettings.backgroundColor.ifBlank { columnSettings.backgroundColor } }
                var textColor = cellSettings.textColor.ifBlank { rowSettings.textColor.ifBlank { columnSettings.textColor } }
                val tickXBackground = cellSettings.tickXBackgroundColor.ifBlank { columnSettings.tickXBackgroundColor }
                val tickXTextColor = cellSettings.tickXTextColor.ifBlank { columnSettings.tickXTextColor }
                val tickCheckedBackground = cellSettings.tickCheckedBackgroundColor.ifBlank { columnSettings.tickCheckedBackgroundColor }
                val tickCheckedTextColor = cellSettings.tickCheckedTextColor.ifBlank { columnSettings.tickCheckedTextColor }
                if (cellSettings.toggleCheck) {
                    val isChecked = block.cells.getOrNull(rowIndex)?.getOrNull(colIndex).orEmpty().trim() == "✓"
                    background = if (isChecked) tickCheckedBackground.ifBlank { background } else tickXBackground.ifBlank { background }
                    textColor = if (isChecked) tickCheckedTextColor.ifBlank { textColor } else tickXTextColor.ifBlank { textColor }
                }
                val textAlign = cellSettings.textAlign.takeIf { it != "left" }
                    ?: rowSettings.textAlign.takeIf { it != "left" }
                    ?: columnSettings.textAlign.takeIf { it != "left" }
                    ?: "left"
                if (cellSettings.toggleCheck) {
                    cell.attr("data-toggle-check", "true")
                    if (tickXBackground.isNotBlank()) cell.attr("data-x-bg", tickXBackground)
                    if (tickXTextColor.isNotBlank()) cell.attr("data-x-color", tickXTextColor)
                    if (tickCheckedBackground.isNotBlank()) cell.attr("data-check-bg", tickCheckedBackground)
                    if (tickCheckedTextColor.isNotBlank()) cell.attr("data-check-color", tickCheckedTextColor)
                } else if (editable) {
                    cell.attr("contenteditable", "true")
                        .attr("data-field", "table_${block.id}_${rowIndex}_${colIndex}")
                }
                val style = buildList {
                    if (background.isNotBlank()) add("background:$background")
                    if (textColor.isNotBlank()) add("color:$textColor")
                    add("text-align:${if (cellSettings.toggleCheck) "center" else textAlign}")
                    if (cellSettings.toggleCheck) add("width:32px;font-weight:bold")
                }.joinToString(";")
                if (style.isNotBlank()) cell.attr("style", style)
                if (cellSettings.hideIfEmpty) cell.attr("data-hide-if-empty", "true")
                if (columnSettings.hideIfEmpty) cell.attr("data-hide-column-if-empty", "true")
                cell.text(block.cells.getOrNull(rowIndex)?.getOrNull(colIndex).orEmpty().replace(PAGE_NUMBER_TOKEN, "1"))
            }
        }
    }

    private fun TemplateBlock.Table.tableCellSettings(row: Int, column: Int): TableCellSettings =
        cellSettings.getOrNull(row)?.getOrNull(column) ?: TableCellSettings()

    private fun appendSignature(parent: org.jsoup.nodes.Element, profile: UserProfile?) {
        val block = parent.appendElement("div").addClass("signature-block")
        profile?.signaturePath?.takeIf { it.isNotBlank() }?.let { path ->
            block.appendElement("img").addClass("signature-image").attr("src", path)
        }
        block.appendElement("div").addClass("signature-line")
        block.appendElement("div").addClass("signature-name").text(profile?.name.orEmpty())
    }

    private fun appendStamp(parent: org.jsoup.nodes.Element, profile: UserProfile?) {
        val block = parent.appendElement("div").addClass("stamp-block")
        profile?.stampPath?.takeIf { it.isNotBlank() }?.let { path ->
            block.appendElement("img").addClass("stamp-image").attr("src", path)
        }
    }

    private fun appendImagesAnchor(parent: org.jsoup.nodes.Element, block: TemplateBlock.Images, todayIso: String, profile: UserProfile?) {
        val anchor = parent.appendElement("div")
            .addClass("images-block")
            .attr("data-images-anchor", "true")
        anchor.appendElement("p")
            .addClass("images-placeholder")
            .text("Ide kerülnek a fotók")
        val template = anchor.appendElement("div")
            .addClass("image-page-template")
            .attr("data-image-page-template", "true")
            .attr("style", "display:none")
        block.blocks.ifEmpty { listOf(TemplateBlock.Image(id = "image-component")) }
            .forEach { appendTemplateBlock(template, it, todayIso, profile) }
    }

    private fun appendImageComponent(parent: org.jsoup.nodes.Element) {
        val block = parent.appendElement("div").addClass("photo-block").attr("data-image-component", "true")
        block.appendElement("img").attr("src", "")
        block.appendElement("p").addClass("image-caption")
    }

    private fun appendPageBreak(parent: org.jsoup.nodes.Element) {
        parent.appendElement("div").addClass("template-page-break")
    }

    private fun appendHtmlBlock(parent: org.jsoup.nodes.Element, block: TemplateBlock.Html) {
        parent.appendElement("div")
            .addClass("html-block")
            .attr("data-template-block-id", block.id)
            .html(block.html)
    }

    private fun ensureImagesContainer(doc: Document): org.jsoup.nodes.Element? {
        val anchor = doc.selectFirst(".images-block[data-images-anchor=true]") ?: return null
        anchor.selectFirst(".images-placeholder")?.remove()
        return anchor
    }

    private fun parse(html: String): Document {
        val doc = Jsoup.parse(html)
        doc.outputSettings()
            .prettyPrint(false)
            .escapeMode(Entities.EscapeMode.xhtml)
            .charset("UTF-8")
        return doc
    }

    private fun ensureContentContainer(doc: Document) =
        doc.getElementById(CONTENT_ID)
            ?: doc.body().appendElement("div").attr("id", CONTENT_ID)

    private fun render(doc: Document): String = doc.outerHtml()

    private fun renderSafetyWalkthrough(contentTemplate: TemplateContent, title: String, todayIso: String, profile: UserProfile?): String {
        val doc = parse(safetyWrapperHtml(title))
        val content = ensureContentContainer(doc)
        val page = content.appendElement("section").addClass("safety-page").addClass("safety-intro")
        appendSafetyHeader(page, null)
        page.append(
            """
            <div class="intro-body">
              <p><strong>Generálkivitelező: ${escapeHtml(profile?.companyName.orEmpty())}</strong></p>
              <p class="inspection-row"><strong>Az ellenőrzést végezte:</strong> <strong>${escapeHtml(profile?.name.orEmpty())}</strong> <strong>Dátum: ${formatHungarianDate(todayIso)}</strong></p>
              <p>Megfelelt: ✓ <span>Nem felelt meg: X</span></p>
              <p><strong>Az együttműködés előmozdítása érdekében tett intézkedések</strong></p>
              <table class="cooperation-actions">
                <tr><td class="check-cell" data-toggle-check="true">X</td><td>beléptetés megoldása</td></tr>
                <tr><td class="check-cell" data-toggle-check="true">X</td><td>generálkivitelező FMV a helyszínen</td></tr>
                <tr><td class="check-cell" data-toggle-check="true">X</td><td>BEK bejárás heti 1 alkalommal</td></tr>
                <tr><td class="check-cell" data-toggle-check="true">X</td><td>ellenőrzési tapasztalatok megküldése és a megelőzés számonkérése</td></tr>
                <tr><td class="check-cell" data-toggle-check="true">X</td><td>különböző munkáltatók tevékenységének összehangolása építésvezetői közreműködéssel</td></tr>
                <tr><td class="check-cell" data-toggle-check="true">X</td><td>napi tájékoztatók</td></tr>
              </table>
              <p><strong>Munkaterület állapota a mai napon</strong></p>
              <p>Értékelés módja kockázat alapú:</p>
            </div>
            """.trimIndent()
        )
        page.appendElement("table").addClass("risk-matrix").append(
            """
            <tr><th class="axis">következmény<br><br>valószínűség</th><th>nincs hatás,<br>vagy a hatás<br>jelentéktelen</th><th>könnyebb<br>sérülést okoz</th><th>jelentős<br>sérülést okoz</th><th>súlyos sérülést<br>okoz</th><th>fokozottan<br>súlyos vagy<br>halálos<br>kimenetelű</th></tr>
            <tr><th>valószínűtlen</th><td class="low">1</td><td class="low">2</td><td class="low">3</td><td class="low">4</td><td class="medium">5</td></tr>
            <tr><th>inkább nem<br>fordul elő</th><td class="low">2</td><td class="low">4</td><td class="medium">6</td><td class="medium">8</td><td class="medium">10</td></tr>
            <tr><th>lehetséges</th><td class="low">3</td><td class="medium">6</td><td class="medium">9</td><td class="high">12</td><td class="high">15</td></tr>
            <tr><th>valószínűleg<br>előfordul</th><td class="low">4</td><td class="medium">8</td><td class="high">12</td><td class="high">16</td><td class="high">20</td></tr>
            <tr><th>elkerülhetetlen</th><td class="medium">5</td><td class="medium">10</td><td class="high">15</td><td class="high">20</td><td class="high">25</td></tr>
            """.trimIndent()
        )
        page.appendElement("table").addClass("risk-levels").append(
            """
            <tr><th>kockázati szint</th><th>végrehajtási határidő</th><th></th></tr>
            <tr><td class="high">magas</td><td>azonnali</td><td>intézkedésig munka nem végezhető</td></tr>
            <tr><td class="medium">közepes</td><td>rövid határidő</td><td>intézkedésig 1-3 nap türelmi idő engedélyezett, addig munka csak feltételekkel végezhető</td></tr>
            <tr><td class="low">alacsony</td><td>hosszabb határidő</td><td>a munkavégzés megengedett, intézkedni a következő bejárás, karbantartás, javítás alkalmáig kell (1 hét)</td></tr>
            """.trimIndent()
        )
        page.appendElement("div").addClass("after-risk-page-break")
        appendSafetyExtraTemplateBlocks(page, contentTemplate, todayIso, profile)
        appendSafetyChecklistPages(content)
        return render(doc)
    }

    private fun appendSafetyExtraTemplateBlocks(parent: org.jsoup.nodes.Element, content: TemplateContent, todayIso: String, profile: UserProfile?) {
        content.blocks.filterNot { it is TemplateBlock.Date }.forEach { block ->
            when (block) {
                is TemplateBlock.Text -> {
                    val div = parent.appendElement("div").addClass("text-block")
                    applyBlockStyle(div, block.settings)
                    div.appendElement("p").text(block.text)
                }
                is TemplateBlock.Table -> appendEditableTable(parent, block)
                is TemplateBlock.Signature -> appendSignature(parent, profile)
                is TemplateBlock.Stamp -> appendStamp(parent, profile)
                is TemplateBlock.Images -> appendImagesAnchor(parent, block, todayIso, profile)
                is TemplateBlock.Image -> appendImageComponent(parent)
                is TemplateBlock.PageBreak -> appendPageBreak(parent)
                is TemplateBlock.PageNumber -> {
                    val span = parent.appendElement("span").addClass("page-number").text("1")
                    applyBlockStyle(span, block.settings, forceBlock = true)
                }
                is TemplateBlock.Header -> block.blocks.forEach { appendTemplateBlock(parent, it, todayIso, profile) }
                is TemplateBlock.Footer -> block.blocks.forEach { appendTemplateBlock(parent, it, todayIso, profile) }
                is TemplateBlock.Html -> appendHtmlBlock(parent, block)
                is TemplateBlock.Date -> parent.appendElement("div").addClass("date-block").text(todayIso)
            }
        }
    }

    private fun appendSafetyChecklistPages(content: org.jsoup.nodes.Element) {
        CHECKLIST_PAGES.forEachIndexed { pageIndex, categories ->
            val page = content.appendElement("section").addClass("safety-page").addClass("safety-checklist-page")
            page.attr("data-checklist-page", (pageIndex + 1).toString())
            appendSafetyHeader(page, null)
            val body = page.appendElement("div").addClass("checklist-body")
            categories.forEach { category -> appendChecklistCategory(body, category) }
        }
    }

    private fun appendChecklistCategory(parent: org.jsoup.nodes.Element, category: ChecklistCategory) {
        val table = parent.appendElement("table").addClass("checklist-table")
        table.appendElement("tr").addClass("checklist-category")
            .appendElement("th").attr("colspan", "2").text(category.title)
        category.items.forEachIndexed { index, item ->
            val isIndented = isIndentedChecklistItem(category.id, item)
            val isGroupRow = !isIndented && category.items.getOrNull(index + 1)?.let { next ->
                isIndentedChecklistItem(category.id, next)
            } == true
            val row = table.appendElement("tr").addClass("checklist-item")
            when {
                isGroupRow -> {
                    row.addClass("checklist-group")
                    row.appendElement("td")
                        .addClass("checklist-label")
                        .attr("colspan", "2")
                        .text(item)
                }
                else -> {
                    if (isIndented) row.addClass("checklist-indent")
                    row.appendElement("td").addClass("checklist-label").text(item)
                    row.appendElement("td")
                        .addClass("checklist-value")
                        .attr("contenteditable", "true")
                        .attr("data-field", "${category.id}_$index")
                }
            }
        }
    }

    private fun appendSafetyObservation(doc: Document, relativeImagePath: String) {
        val content = ensureContentContainer(doc)
        val index = content.select(".safety-observation").size + 1
        val page = content.appendElement("section").addClass("safety-page").addClass("safety-observation")
        page.attr("data-index", index.toString())
        appendSafetyHeader(page, "$index.")
        val body = page.appendElement("div").addClass("observation-body")
        body.appendElement("img").addClass("observation-image").attr("src", relativeImagePath)
        val table = body.appendElement("table").addClass("observation-table")
        table.append(
            """
            <tr><th>kockázati szint</th><td contenteditable="true" data-field="risk_score"></td><td contenteditable="true" data-field="risk_level"></td></tr>
            <tr><th>veszély forrása</th><td colspan="2" contenteditable="true" data-field="hazard_source"></td></tr>
            <tr><th>veszélyhelyzet</th><td colspan="2" contenteditable="true" data-field="hazard_situation"></td></tr>
            <tr><th>megelőzés</th><td colspan="2" contenteditable="true" data-field="prevention"></td></tr>
            <tr><th>határidő</th><td colspan="2" contenteditable="true" data-field="deadline"></td></tr>
            <tr><th>felelős</th><td colspan="2" contenteditable="true" data-field="responsible"></td></tr>
            <tr><th>szankció</th><td colspan="2" contenteditable="true" data-field="sanction"></td></tr>
            <tr><th>visszaellenőrzés</th><td colspan="2" contenteditable="true" data-field="follow_up"></td></tr>
            """.trimIndent()
        )
    }

    private fun appendSafetyHeader(parent: org.jsoup.nodes.Element, index: String?) {
        val table = parent.appendElement("table").addClass("safety-header")
        val row = table.appendElement("tr")
        row.appendElement("th").html("Párta köz 4.<br>BET")
        row.appendElement("th").html("MUNKAVÉDELMI BEJÁRÁS<br>ELLENŐRZÉSI jegyzőkönyv")
        row.appendElement("th").text("7/B melléklet")
        if (index != null) row.appendElement("th").addClass("page-index").text(index)
    }

    private fun formatHungarianDate(todayIso: String): String {
        val parts = todayIso.split("-")
        return if (parts.size == 3) "${parts[0]}. ${parts[1]}. ${parts[2]}." else todayIso
    }

    private fun escapeHtml(value: String): String = value
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")

    private fun wrapperHtml(title: String, showHeading: Boolean): String {
        val safeTitle = title
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
        val heading = if (showHeading) "<h1>$safeTitle</h1>" else ""
        return """
            <!DOCTYPE html>
            <html lang="hu">
            <head>
            <meta charset="utf-8" />
            <title>$safeTitle</title>
            <style>
              * { box-sizing: border-box; }
              body {
                font-family: -apple-system, "Roboto", sans-serif;
                padding: 24px;
                margin: 0;
                background-color: #ffffff;
                color: #1a1a1a;
                line-height: 1.45;
                font-size: 14px;
              }
              h1 {
                font-size: 22px;
                margin: 0 0 12px;
                border-bottom: 2px solid #2e5266;
                padding-bottom: 6px;
              }
              #content { margin-top: 8px; }
              .photo-block {
                margin: 14px 0;
                page-break-inside: avoid;
              }
              .photo-block img {
                width: 100%;
                max-width: 100%;
                height: auto;
                border-radius: 6px;
                display: block;
              }
              .photo-block p {
                margin: 6px 0 0;
                font-size: 13px;
                color: #333;
                font-style: italic;
              }
              .text-block {
                margin: 12px 0;
                white-space: pre-wrap;
              }
              .text-block p {
                margin: 0;
              }
              .date-block {
                margin: 12px 0;
                font-weight: 600;
              }
              .editable-table { width: 100%; border-collapse: collapse; margin: 12px 0; table-layout: fixed; }
              .editable-table th, .editable-table td { border: 1px solid #333; min-height: 28px; padding: 6px; vertical-align: top; }
              .editable-table th { background: #eeeeee; font-weight: 600; }
              .signature-block { width: 220px; margin: 28px 0 12px auto; text-align: center; page-break-inside: avoid; }
              .signature-image { max-width: 200px; max-height: 70px; object-fit: contain; display: block; margin: 0 auto -6px; }
              .signature-line { border-top: 1px solid #111; height: 1px; margin: 0 12px 4px; }
              .signature-name { font-size: 13px; }
              .stamp-block { margin: 16px 0; text-align: right; page-break-inside: avoid; }
              .stamp-image { max-width: 160px; max-height: 100px; object-fit: contain; }
              .images-block { margin: 12px 0; min-height: 36px; }
              .images-placeholder { margin: 0; padding: 10px; border: 1px dashed #777; color: #666; text-align: center; }
              .template-page-break { break-after: page; page-break-after: always; height: 1px; margin: 18px 0; border-top: 1px dashed #999; }
              [contenteditable="true"] { min-height: 20px; outline: 1px dashed transparent; }
              [contenteditable="true"]:focus { outline-color: #555; background: #fffde7; }
            </style>
            </head>
            <body>
            $heading
            <div id="content"></div>
            </body>
            </html>
        """.trimIndent()
    }

    private fun safetyWrapperHtml(title: String): String {
        val safeTitle = title
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
        return """
            <!DOCTYPE html>
            <html lang="hu">
            <head>
            <meta charset="utf-8" />
            <title>$safeTitle</title>
            <style>
              * { box-sizing: border-box; }
              body.safety-walkthrough { margin: 0; background: #fff; color: #000; font-family: serif; font-size: 14px; }
              #content { width: 100%; }
              .safety-page { width: 100%; min-height: 100vh; padding: 24px; page-break-after: always; }
              .safety-header { width: 100%; border-collapse: collapse; margin: 0 0 14px; table-layout: fixed; }
              .safety-header th { border: 1px solid #000; background: #e9e9e9; font-size: 18px; line-height: 1.2; text-align: center; padding: 4px 6px; }
              .safety-header th:first-child { width: 25%; }
              .safety-header th:nth-child(2) { width: 47%; }
              .safety-header th:nth-child(3) { width: 22%; }
              .safety-header .page-index { width: 6%; }
              .intro-body { margin: 0 36px; font-size: 18px; line-height: 1.25; }
              .inspection-row { display: flex; justify-content: space-between; gap: 24px; }
              .cooperation-actions { width: 100%; margin: 8px 0 12px; border-collapse: collapse; table-layout: fixed; }
              .cooperation-actions td { border: 1px solid #000; padding: 3px 6px; vertical-align: top; }
              .cooperation-actions .check-cell { width: 42px; text-align: center; font-weight: bold; cursor: pointer; user-select: none; }
              .checklist-body { margin: 0 22px; font-size: 12px; line-height: 1.15; }
              .checklist-meta, .checklist-table { width: 100%; border-collapse: collapse; table-layout: fixed; margin: 0 0 6px; }
              .checklist-meta th, .checklist-meta td, .checklist-table th, .checklist-table td { border: 1px solid #000; padding: 2px 5px; vertical-align: top; }
              .checklist-meta th { width: 22%; text-align: left; }
              .checklist-meta td { width: 28%; min-height: 20px; }
              .checklist-legend { margin: 4px 0 8px; font-weight: bold; }
              .checklist-category th { background: #e9e9e9; text-align: left; font-weight: bold; }
              .checklist-label { width: 72%; }
              .checklist-group .checklist-label { width: 100%; background: #e9e9e9; font-weight: normal; }
              .checklist-indent .checklist-label { padding-left: 22px; }
              .checklist-value { width: 28%; min-height: 18px; white-space: pre-wrap; overflow-wrap: anywhere; }
              .risk-matrix, .risk-levels, .observation-table { width: 92%; margin: 12px auto 0; border-collapse: collapse; table-layout: fixed; }
              .risk-matrix th, .risk-matrix td, .risk-levels th, .risk-levels td, .observation-table th, .observation-table td { border: 1px solid #000; padding: 2px 6px; vertical-align: top; }
              .risk-matrix th { text-align: left; font-weight: normal; }
              .risk-matrix td { text-align: center; }
              .risk-levels th, .observation-table th { text-align: left; font-weight: bold; }
              .low { background: #92d050; }
              .medium { background: #ffff00; }
              .high { background: #ff0000; color: #000; }
              .after-risk-page-break { page-break-after: always; break-after: page; height: 0; }
              .observation-body { width: 92%; margin: 0 auto; }
              .observation-image { width: 100%; height: auto; max-height: 34vh; object-fit: contain; display: block; margin: 0 auto 8px; border: 1px solid #000; background: #f3f3f3; }
              .observation-image-preview { height: 34vh; min-height: 180px; background-size: contain; background-repeat: no-repeat; background-position: center; }
              .observation-table { width: 100%; margin-top: 0; font-size: 14px; }
              .observation-table th { width: 26%; }
              .observation-table td { min-height: 24px; white-space: pre-wrap; overflow-wrap: anywhere; }
              .images-block { margin: 12px 0; min-height: 36px; }
              .images-placeholder { margin: 0; padding: 10px; border: 1px dashed #777; color: #666; text-align: center; }
              .template-page-break { break-after: page; page-break-after: always; height: 1px; margin: 18px 0; border-top: 1px dashed #999; }
              [contenteditable="true"] { min-height: 20px; outline: 1px dashed transparent; scroll-margin: 96px 0 24px; }
              [contenteditable="true"]:focus { outline-color: #555; background: #fffde7; }
              @media screen {
                body.safety-walkthrough { font-size: 10px; }
                .safety-page { min-height: auto; padding: 12px 8px 18px; }
                .safety-header { margin-bottom: 8px; }
                .safety-header th { font-size: 13px; padding: 3px 4px; }
                .intro-body { margin: 0 16px; font-size: 14px; }
                .checklist-body { margin: 0 6px; font-size: 10px; }
                .checklist-meta th, .checklist-meta td, .checklist-table th, .checklist-table td { padding: 2px 3px; }
                .risk-matrix, .risk-levels, .observation-body { width: 100%; }
                .observation-image { max-height: 26vh; margin-bottom: 6px; }
                .observation-image-preview { height: 26vh; min-height: 140px; }
                .observation-table { font-size: 12px; }
                .observation-table th { width: 25%; }
                .observation-table th, .observation-table td { padding: 2px 4px; }
              }
            </style>
            </head>
            <body class="safety-walkthrough">
            <h1 style="display:none">$safeTitle</h1>
            <div id="content"></div>
            </body>
            </html>
        """.trimIndent()
    }

    private fun isIndentedChecklistItem(_categoryId: String, item: String): Boolean =
        item.trim() in INDENTED_CHECKLIST_ITEMS

    private data class ChecklistCategory(
        val id: String,
        val title: String,
        val items: List<String>,
    )

    private companion object {
        const val CONTENT_ID = "content"
        const val SAFETY_CLASS = "safety-walkthrough"
        const val PAGE_NUMBER_TOKEN = "{{oldalszam}}"

        val INDENTED_CHECKLIST_ITEMS = setOf(
            "hegesztés, nyílt láng, szikra",
            "földmunka",
            "szűk-zárt tér",
            "visszabontás",
            "munkagödör omlásbiztosítása",
            "szűk-zárt térben való munkavégzés",
            "épületszerkezet visszabontása",
            "zsaluzatok bontása",
            "több emelőgéppel végzett együttes emelés",
            "veszélyes munkaeszköz",
            "emelőgép",
            "alpin-technika",
            "használatba vételi eljárás",
            "ideiglenes energiaelosztó hálózat",
            "össze- és szétszerelhető munkaeszközök",
            "állvány",
            "állvány építése és bontása",
            "személyek emelésére alkalmas emelőgép használata",
            "zuhanásgátló heveder alkalmazása",
            "emelőgép kezelése",
            "daruirányítás, teherkötözés",
            "hegesztés fokozottan veszélyes körülmények között",
            "technológiai művelet",
            "ideiglenes munkairányítás",
        )

        val CHECKLIST_PAGES = listOf(
            listOf(
                ChecklistCategory(
                    id = "documentation",
                    title = "Dokumentációk megléte",
                    items = listOf(
                        "kockázatértékelés",
                        "engedély",
                        "hegesztés, nyílt láng, szikra",
                        "földmunka",
                        "szűk-zárt tér",
                        "visszabontás",
                        "Biztonsági terv: ",
                        "munkagödör omlásbiztosítása",
                        "szűk-zárt térben való munkavégzés",
                        "épületszerkezet visszabontása",
                        "zsaluzatok bontása",
                        "több emelőgéppel végzett együttes emelés",
                        "munkavédelmi üzembe helyezés",
                        "veszélyes munkaeszköz",
                        "emelőgép",
                        "alpin-technika",
                        "használatba vételi eljárás",
                        "ideiglenes energiaelosztó hálózat",
                        "össze- és szétszerelhető munkaeszközök",
                        "állvány",
                        "felülvizsgálatok",
                        "elektromos kéziszerszám szerelői ellenőrzése",
                        "ÁVK havonta",
                        "tűzoltó készülék üzemeltetési napló",
                        "homlokzati állványzat, akna állványzata",
                        "mobil állvány",
                        "létra",
                        "egyéb ideiglenes felépítmények",
                        "nyilvántartások: EVE átvétel, területre vonatkozó oktatási napló, veszélyes anyagok",
                        "emelő- és munkagépek gépnaplója, felülvizsgálati jegyzőkönyvek",
                        "technológiai utasítás",
                    ),
                ),
                ChecklistCategory(
                    id = "personnel",
                    title = "Személyi feltételek ellenőrzése",
                    items = listOf(
                        "képzettség (bizonyítvány, igazolvány, jogosítvány, tűzvédelmi szakvizsga)",
                        "orvosi alkalmasság",
                        "fizikai, gyakorlati alkalmasság, megfelelő létszám",
                        "kockázatok ismerete, oktatás a megelőzésről",
                        "soron kívüli oktatás",
                        "állvány építése és bontása",
                        "személyek emelésére alkalmas emelőgép használata",
                        "zuhanásgátló heveder alkalmazása",
                        "emelőgép kezelése",
                        "daruirányítás, teherkötözés",
                        "hegesztés fokozottan veszélyes körülmények között",
                        "technológiai művelet",
                        "ideiglenes munkairányítás",
                        "szűk-zárt tér",
                        "munkairányító kinevezése",
                    ),
                ),
            ),
            listOf(
                ChecklistCategory(
                    id = "equipment",
                    title = "Tárgyi feltételek",
                    items = listOf(
                        "kollektív és megfelelő egyéni védőeszközök",
                        "ép szerszámok és eszközök",
                        "szabványos állványok és létrák, feljárók, átjárók állapota",
                        "dúcolatok",
                        "függesztékek állapota",
                        "elsősegély doboz a helyszínen",
                        "tűzoltó készülék",
                        "munkakörnyezeti tényezők: megvilágítás, fűtés, szellőzés, hozzáférés, villamos biztonság, közlekedés, veszélyes anyagok kezelése, zaj, rezgés, por, stabilitás",
                    ),
                ),
                ChecklistCategory(
                    id = "organization",
                    title = "Szervezési feltételek",
                    items = listOf(
                        "munkaterület átvétele, lehatárolása",
                        "közlekedési útvonal biztosítása",
                        "anyagmozgatás irányítása",
                        "rend, fegyelem, tisztaság",
                        "dohányzás",
                        "tűzvédelmi előírások betartása",
                        "konzultáció",
                        "védőital, pihenőidő",
                        "anyag és hulladéktárolás kialakítása",
                        "veszélyes anyagok, gázpalackok kezelése",
                    ),
                ),
            ),
        )
    }
}
