package hu.gc.jegyzokonyv.domain.html

import com.google.common.truth.Truth.assertThat
import hu.gc.jegyzokonyv.data.profile.UserProfile
import hu.gc.jegyzokonyv.domain.model.TableAxisSettings
import hu.gc.jegyzokonyv.domain.model.TableCellSettings
import hu.gc.jegyzokonyv.domain.model.TemplateBlock
import hu.gc.jegyzokonyv.domain.model.TemplateContent
import org.jsoup.Jsoup
import org.junit.Test

class JsoupHtmlEngineTest {
    private val engine = JsoupHtmlEngine()

    @Test
    fun renderTemplateReplacesTokensEscapesTextAndMarksEditableFields() {
        val profile = UserProfile(
            name = "Kiss <Péter>",
            companyName = "GC",
            phone = "+36",
            email = "mail@example.test",
        )
        val content = TemplateContent(
            title = "Cím",
            blocks = listOf(
                TemplateBlock.Text(
                    id = "intro",
                    text = "<b>{{datum}}</b> {{datum_hu}} {{profil_nev}}",
                    settings = TableCellSettings(
                        backgroundColor = "#ffeecc",
                        textColor = "#111111",
                        textAlign = "center",
                        editable = true,
                        bold = true,
                    ),
                ),
                TemplateBlock.Table(
                    id = "table",
                    rows = 2,
                    columns = 2,
                    hasHeaderColumn = true,
                    cells = listOf(
                        listOf("Név", "{{oldalszam}}"),
                        listOf("X", "✓"),
                    ),
                    rowSettings = listOf(TableAxisSettings(headerRow = true, bold = true)),
                    columnSettings = listOf(TableAxisSettings(editable = false)),
                    cellSettings = listOf(
                        listOf(TableCellSettings(), TableCellSettings(textAlign = "right")),
                        listOf(
                            TableCellSettings(toggleCheck = true, tickXBackgroundColor = "#990000", tickXTextColor = "#ffffff"),
                            TableCellSettings(toggleCheck = true, tickCheckedBackgroundColor = "#009900", tickCheckedTextColor = "#000000"),
                        ),
                    ),
                ),
            ),
        )

        val doc = Jsoup.parse(engine.renderTemplate(content, "Tesztdokumentum <script>", "2026-07-09", profile))

        assertThat(doc.title()).isEqualTo("Tesztdokumentum <script>")
        assertThat(doc.selectFirst("h1")!!.text()).isEqualTo("Tesztdokumentum <script>")
        assertThat(doc.selectFirst("[data-template-block-id=intro] p")!!.text())
            .isEqualTo("<b>2026-07-09</b> 2026. 07. 09. Kiss <Péter>")
        assertThat(doc.selectFirst("[data-template-block-id=intro] p")!!.attr("contenteditable")).isEqualTo("true")
        assertThat(doc.selectFirst("[data-template-block-id=intro] p")!!.attr("data-field")).isEqualTo("text_intro")
        assertThat(doc.selectFirst("[data-template-block-id=intro]")!!.attr("style")).contains("text-align:center")

        val cells = doc.select("table[data-template-block-id=table] th, table[data-template-block-id=table] td")
        assertThat(cells[0].normalName()).isEqualTo("th")
        assertThat(cells[0].hasAttr("contenteditable")).isFalse()
        assertThat(cells[1].attr("data-page-number-template")).isEqualTo("{{oldalszam}}")
        assertThat(cells[1].text()).isEqualTo("1")
        assertThat(cells[2].attr("data-toggle-check")).isEqualTo("true")
        assertThat(cells[2].text()).isEqualTo("X")
        assertThat(cells[2].attr("data-x-bg")).isEqualTo("#990000")
        assertThat(cells[3].text()).isEqualTo("✓")
        assertThat(cells[3].attr("data-check-bg")).isEqualTo("#009900")
    }

    @Test
    fun appendPhotoBlockUsesImageTemplateCaptionAndImageIndexToken() {
        val html = engine.renderTemplate(
            content = TemplateContent(
                title = "Fotók",
                blocks = listOf(
                    TemplateBlock.Images(
                        id = "images",
                        blocks = listOf(
                            TemplateBlock.Text("caption-template", "Fotó {{kep_sorszam}}"),
                            TemplateBlock.Image("image-component"),
                        ),
                    ),
                ),
            ),
            title = "Fotók",
            todayIso = "2026-07-09",
            profile = null,
        )

        val withFirst = engine.appendPhotoBlock(html, "images/img_001.jpg", "Első kép")
        val withSecond = engine.appendPhotoBlock(withFirst, "images/img_002.jpg", "Második kép")
        val doc = Jsoup.parse(withSecond)

        val pages = doc.select(".image-page")
        assertThat(pages).hasSize(2)
        assertThat(pages[0].selectFirst("[data-image-index=true]")!!.text()).isEqualTo("Fotó 1")
        assertThat(pages[1].selectFirst("[data-image-index=true]")!!.text()).isEqualTo("Fotó 2")
        assertThat(pages[0].selectFirst("img")!!.attr("src")).isEqualTo("images/img_001.jpg")
        assertThat(pages[1].selectFirst("img")!!.attr("src")).isEqualTo("images/img_002.jpg")
        assertThat(pages[0].selectFirst(".image-caption")!!.text()).isEqualTo("Első kép")
        assertThat(pages[1].selectFirst(".image-caption")!!.text()).isEqualTo("Második kép")
        assertThat(doc.select(".images-placeholder")).isEmpty()
        assertThat(doc.select(".image-page-template")).hasSize(1)
    }

    @Test
    fun appendPhotoBlockFallsBackToPhotoBlockAndOmitsBlankCaption() {
        val updated = engine.appendPhotoBlock(
            html = "<html><body><div id=\"content\"></div></body></html>",
            relativeImagePath = "images/img_001.jpg",
            caption = " ",
        )
        val block = Jsoup.parse(updated).selectFirst(".photo-block")!!

        assertThat(block.selectFirst("img")!!.attr("src")).isEqualTo("images/img_001.jpg")
        assertThat(block.select("p")).isEmpty()
    }

    @Test
    fun setTitleUpdatesDocumentTitleAndFirstHeading() {
        val updated = engine.setTitle(
            html = "<html><head><title>Régi</title></head><body><h1>Régi</h1><h1>Második</h1></body></html>",
            title = "Új cím",
        )
        val doc = Jsoup.parse(updated)

        assertThat(doc.title()).isEqualTo("Új cím")
        assertThat(doc.select("h1").map { it.text() }).containsExactly("Új cím", "Második").inOrder()
    }
}
