package hu.gc.jegyzokonyv.domain.html

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
    ): String {
        if (content.kind == TemplateKind.SafetyWalkthrough) {
            return renderSafetyWalkthrough(title, todayIso)
        }

        val doc = parse(wrapperHtml(title))
        val container = ensureContentContainer(doc)
        content.blocks.forEach { block ->
            when (block) {
                is TemplateBlock.Text -> {
                    val div = container.appendElement("div").addClass("text-block")
                    div.appendElement("p").text(block.text)
                }
                is TemplateBlock.Date -> {
                    container.appendElement("div").addClass("date-block").text(todayIso)
                }
            }
        }
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

        val content = ensureContentContainer(doc)
        val block = content.appendElement("div").addClass("photo-block")
        block.appendElement("img").attr("src", relativeImagePath)
        if (!caption.isNullOrBlank()) {
            block.appendElement("p").text(caption)
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

    private fun renderSafetyWalkthrough(title: String, todayIso: String): String {
        val doc = parse(safetyWrapperHtml(title))
        val content = ensureContentContainer(doc)
        val page = content.appendElement("section").addClass("safety-page").addClass("safety-intro")
        appendSafetyHeader(page, null)
        page.append(
            """
            <div class="intro-body">
              <p><strong>Generálkivitelező: Gépész Centrál Kft.</strong></p>
              <p class="inspection-row"><strong>Az ellenőrzést végezte:</strong> <strong>Kispéter Ákosné</strong> <strong>Dátum: ${formatHungarianDate(todayIso)}</strong></p>
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
        return render(doc)
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

    private fun wrapperHtml(title: String): String {
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
            </style>
            </head>
            <body>
            <h1>$safeTitle</h1>
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
              [contenteditable="true"] { min-height: 20px; outline: 1px dashed transparent; scroll-margin: 96px 0 24px; }
              [contenteditable="true"]:focus { outline-color: #555; background: #fffde7; }
              @media screen {
                body.safety-walkthrough { font-size: 10px; }
                .safety-page { min-height: auto; padding: 12px 8px 18px; }
                .safety-header { margin-bottom: 8px; }
                .safety-header th { font-size: 13px; padding: 3px 4px; }
                .intro-body { margin: 0 16px; font-size: 14px; }
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

    private companion object {
        const val CONTENT_ID = "content"
        const val SAFETY_CLASS = "safety-walkthrough"
    }
}
