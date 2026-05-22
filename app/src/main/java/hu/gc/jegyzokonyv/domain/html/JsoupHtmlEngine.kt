package hu.gc.jegyzokonyv.domain.html

import hu.gc.jegyzokonyv.data.profile.UserProfile
import hu.gc.jegyzokonyv.domain.model.TableAxisSettings
import hu.gc.jegyzokonyv.domain.model.TableCellSettings
import hu.gc.jegyzokonyv.domain.model.TemplateBlock
import hu.gc.jegyzokonyv.domain.model.TemplateContent
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
        val content = ensureImagesContainer(doc) ?: ensureContentContainer(doc)
        val template = content.selectFirst(".image-page-template[data-image-page-template=true]")
        if (template != null) {
            val page = template.clone()
                .removeClass("image-page-template")
                .addClass("image-page")
                .removeAttr("data-image-page-template")
                .removeAttr("style")
            val imageIndex = content.select(".image-page").size + 1
            page.select("[data-image-component=true]").forEach { imageBlock ->
                imageBlock.selectFirst("img")?.attr("src", relativeImagePath)
                val captionElement = imageBlock.selectFirst(".image-caption") ?: imageBlock.selectFirst("p")
                if (!caption.isNullOrBlank()) captionElement?.text(caption) else captionElement?.remove()
            }
            page.select("*").forEach { element ->
                element.textNodes().forEach { textNode ->
                    if (textNode.text().contains(IMAGE_INDEX_TOKEN)) {
                        textNode.text(textNode.text().replace(IMAGE_INDEX_TOKEN, imageIndex.toString()))
                    }
                }
                element.attributes().forEach { attr ->
                    if (attr.value.contains(IMAGE_INDEX_TOKEN)) {
                        element.attr(attr.key, attr.value.replace(IMAGE_INDEX_TOKEN, imageIndex.toString()))
                    }
                }
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
            is TemplateBlock.Text -> appendTextTemplateBlock(parent, block, todayIso, profile)
            is TemplateBlock.Date -> {
                val div = parent.appendElement("div").addClass("date-block").text(todayIso)
                applyBlockStyle(div, block.settings)
            }
            is TemplateBlock.Table -> appendEditableTable(parent, block, todayIso, profile)
            is TemplateBlock.Signature -> appendSignature(parent, profile)
            is TemplateBlock.Stamp -> appendStamp(parent, profile)
            is TemplateBlock.ProfileData -> appendProfileData(parent, block, profile)
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
            is TemplateBlock.Html -> appendHtmlBlock(parent, block, todayIso, profile)
        }
    }

    private fun appendTextTemplateBlock(parent: org.jsoup.nodes.Element, block: TemplateBlock.Text, todayIso: String, profile: UserProfile?) {
        val div = parent.appendElement("div")
            .addClass("text-block")
            .attr("data-template-block-id", block.id)
        applyBlockStyle(div, block.settings)
        val paragraph = div.appendElement("p").text(replaceSpecialTokens(block.text, "1", todayIso, profile))
        if (block.text.contains(PAGE_NUMBER_TOKEN)) {
            paragraph.attr("data-page-number-template", replaceSpecialTokens(block.text, PAGE_NUMBER_TOKEN, todayIso, profile))
        }
        if (block.settings.editable == true) {
            paragraph.attr("contenteditable", "true")
                .attr("data-field", "text_${block.id}")
        }
    }

    private fun applyBlockStyle(element: org.jsoup.nodes.Element, settings: TableCellSettings, forceBlock: Boolean = false) {
        val styles = buildList {
            if (forceBlock) add("display:block")
            if (settings.backgroundColor.isNotBlank()) add("background:${settings.backgroundColor}")
            if (settings.textColor.isNotBlank()) add("color:${settings.textColor}")
            if (settings.textAlign.isNotBlank()) add("text-align:${settings.textAlign}")
            if (settings.bold) add("font-weight:bold")
            if (settings.italic) add("font-style:italic")
        }
        if (styles.isNotEmpty()) element.attr("style", styles.joinToString(";"))
    }

    private fun appendEditableTable(parent: org.jsoup.nodes.Element, block: TemplateBlock.Table, todayIso: String, profile: UserProfile?) {
        val table = parent.appendElement("table")
            .addClass("editable-table")
            .attr("data-template-block-id", block.id)
        val rows = block.rows.coerceIn(1, 50)
        val columns = block.columns.coerceIn(1, 20)
        val skipped = mutableSetOf<Pair<Int, Int>>()
        repeat(rows) { rowIndex ->
            val row = table.appendElement("tr")
            val rowSettings = block.rowSettings.getOrNull(rowIndex) ?: TableAxisSettings()
            if (rowSettings.hideIfEmpty && !rowSettings.headerRow) row.attr("data-hide-if-empty", "true")
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
                val cell = if (rowSettings.headerRow || (block.hasHeaderColumn && colIndex == 0)) row.appendElement("th") else row.appendElement("td")
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
                val bold = cellSettings.bold || rowSettings.bold || columnSettings.bold
                val italic = cellSettings.italic || rowSettings.italic || columnSettings.italic
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
                    if (bold || cellSettings.toggleCheck) add("font-weight:bold")
                    if (italic) add("font-style:italic")
                    if (cellSettings.toggleCheck) add("width:32px")
                }.joinToString(";")
                if (style.isNotBlank()) cell.attr("style", style)
                if (cellSettings.hideIfEmpty) cell.attr("data-hide-if-empty", "true")
                if (columnSettings.hideIfEmpty) cell.attr("data-hide-column-if-empty", "true")
                val cellText = block.cells.getOrNull(rowIndex)?.getOrNull(colIndex).orEmpty()
                if (cellText.contains(PAGE_NUMBER_TOKEN)) {
                    cell.attr("data-page-number-template", replaceSpecialTokens(cellText, PAGE_NUMBER_TOKEN, todayIso, profile))
                }
                cell.text(replaceSpecialTokens(cellText, "1", todayIso, profile))
            }
        }
    }

    private fun TemplateBlock.Table.tableCellSettings(row: Int, column: Int): TableCellSettings =
        cellSettings.getOrNull(row)?.getOrNull(column) ?: TableCellSettings()

    private fun replaceSpecialTokens(text: String, pageNumber: String, todayIso: String, profile: UserProfile?): String =
        text.replace(PAGE_NUMBER_TOKEN, pageNumber)
            .replace(DATE_TOKEN, todayIso)
            .replace(DATE_HU_TOKEN, formatHungarianDate(todayIso))
            .replace(PROFILE_NAME_TOKEN, profile?.name.orEmpty())
            .replace(PROFILE_COMPANY_TOKEN, profile?.companyName.orEmpty())
            .replace(PROFILE_PHONE_TOKEN, profile?.phone.orEmpty())
            .replace(PROFILE_EMAIL_TOKEN, profile?.email.orEmpty())

    private fun formatHungarianDate(todayIso: String): String {
        val parts = todayIso.split("-")
        return if (parts.size == 3) "${parts[0]}. ${parts[1]}. ${parts[2]}." else todayIso
    }

    private fun appendProfileData(parent: org.jsoup.nodes.Element, block: TemplateBlock.ProfileData, profile: UserProfile?) {
        parent.appendElement("div")
            .addClass("text-block")
            .addClass("profile-data-block")
            .attr("data-profile-field", block.field.jsonValue)
            .appendElement("p")
            .text(block.field.valueFrom(profile))
    }

    private fun hu.gc.jegyzokonyv.domain.model.ProfileDataField.valueFrom(profile: UserProfile?): String = when (this) {
        hu.gc.jegyzokonyv.domain.model.ProfileDataField.Name -> profile?.name.orEmpty()
        hu.gc.jegyzokonyv.domain.model.ProfileDataField.CompanyName -> profile?.companyName.orEmpty()
        hu.gc.jegyzokonyv.domain.model.ProfileDataField.Phone -> profile?.phone.orEmpty()
        hu.gc.jegyzokonyv.domain.model.ProfileDataField.Email -> profile?.email.orEmpty()
    }

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

    private fun appendHtmlBlock(parent: org.jsoup.nodes.Element, block: TemplateBlock.Html, todayIso: String, profile: UserProfile?) {
        parent.appendElement("div")
            .addClass("html-block")
            .attr("data-template-block-id", block.id)
            .html(replaceSpecialTokens(block.html, "1", todayIso, profile))
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
              .editable-table th, .editable-table td { border: 1px solid #333; min-height: 28px; padding: 6px; vertical-align: top; white-space: pre-wrap; }
              .editable-table th { background: #eeeeee; font-weight: 600; }
              .signature-block { width: 220px; margin: 28px 0 12px auto; text-align: center; page-break-inside: avoid; }
              .signature-image { max-width: 200px; max-height: 70px; object-fit: contain; display: block; margin: 0 auto -6px; }
              .signature-line { border-top: 1px solid #111; height: 1px; margin: 0 12px 4px; }
              .signature-name { font-size: 13px; }
              .stamp-block { margin: 16px 0; text-align: right; page-break-inside: avoid; }
              .stamp-image { max-width: 160px; max-height: 100px; object-fit: contain; }
              .images-block { margin: 12px 0; min-height: 36px; }
              .images-placeholder { margin: 0; padding: 10px; border: 1px dashed #777; color: #666; text-align: center; }
              .image-page { break-inside: avoid; page-break-inside: avoid; }
              .template-page-break { break-after: page; page-break-after: always; height: 1px; margin: 18px 0; border-top: 1px dashed #999; position: relative; }
              @media screen { .template-page-break::after { content: "Oldaltörés"; position: relative; top: -9px; left: 50%; transform: translateX(-50%); display: inline-block; background: #fff; color: #666; font-size: 11px; padding: 0 6px; } .repeat-header, .repeat-footer { display: block; margin: 10px 0; padding: 8px; border: 1px dashed #6b7280; background: #f8fafc; } .repeat-header::before, .repeat-footer::before { display: block; margin-bottom: 6px; color: #475569; font-size: 11px; font-weight: 600; } .repeat-header::before { content: "Ismétlődő fejléc"; } .repeat-footer::before { content: "Ismétlődő lábléc"; } }
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

    private companion object {
        const val CONTENT_ID = "content"
        const val PAGE_NUMBER_TOKEN = "{{oldalszam}}"
        const val IMAGE_INDEX_TOKEN = "{{kep_sorszam}}"
        const val DATE_TOKEN = "{{datum}}"
        const val DATE_HU_TOKEN = "{{datum_hu}}"
        const val PROFILE_NAME_TOKEN = "{{profil_nev}}"
        const val PROFILE_COMPANY_TOKEN = "{{profil_cegnev}}"
        const val PROFILE_PHONE_TOKEN = "{{profil_telefon}}"
        const val PROFILE_EMAIL_TOKEN = "{{profil_email}}"

    }
}
