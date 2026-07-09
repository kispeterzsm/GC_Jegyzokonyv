package hu.gc.jegyzokonyv.data.template

import com.google.common.truth.Truth.assertThat
import hu.gc.jegyzokonyv.domain.model.ProfileDataField
import hu.gc.jegyzokonyv.domain.model.TableAxisSettings
import hu.gc.jegyzokonyv.domain.model.TableCellSettings
import hu.gc.jegyzokonyv.domain.model.TemplateBlock
import hu.gc.jegyzokonyv.domain.model.TemplateContent
import hu.gc.jegyzokonyv.domain.model.TemplateKind
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class TemplateContentCodecTest {
    @Test
    fun roundTripAllBlockTypesPreservesContentAndStyles() {
        val cellSettings = TableCellSettings(
            backgroundColor = "#112233",
            textColor = "#445566",
            textAlign = "center",
            tickXBackgroundColor = "#770000",
            tickXTextColor = "#ffffff",
            tickCheckedBackgroundColor = "#007700",
            tickCheckedTextColor = "#000000",
            editable = false,
            bold = true,
            italic = true,
            hideIfEmpty = true,
            toggleCheck = true,
            mergeRight = true,
        )
        val rowSettings = TableAxisSettings(
            backgroundColor = "#eeeeee",
            textColor = "#111111",
            textAlign = "right",
            editable = true,
            bold = true,
            italic = true,
            hideIfEmpty = true,
            mergeAll = true,
            headerRow = false,
        )
        val content = TemplateContent(
            title = "Munkavédelmi sablon",
            kind = TemplateKind.SafetyWalkthrough,
            blocks = listOf(
                TemplateBlock.Text("text", "Szöveg", cellSettings.copy(toggleCheck = false, mergeRight = false)),
                TemplateBlock.Date("date", cellSettings.copy(toggleCheck = false, mergeRight = false)),
                TemplateBlock.Table(
                    id = "table",
                    rows = 2,
                    columns = 2,
                    hasHeaderColumn = true,
                    cells = listOf(listOf("A", "B"), listOf("C", "D")),
                    rowSettings = listOf(rowSettings),
                    columnSettings = listOf(rowSettings.copy(textAlign = "left")),
                    cellSettings = listOf(
                        listOf(cellSettings.copy(toggleCheck = false), cellSettings),
                        listOf(TableCellSettings(), cellSettings.copy(mergeRight = false)),
                    ),
                ),
                TemplateBlock.Signature("signature"),
                TemplateBlock.Stamp("stamp"),
                TemplateBlock.ProfileData("profile", ProfileDataField.Email),
                TemplateBlock.Images(
                    id = "images",
                    blocks = listOf(
                        TemplateBlock.Text("image-caption", "Fotó {{kep_sorszam}}"),
                        TemplateBlock.Image("image"),
                    ),
                ),
                TemplateBlock.PageBreak("break"),
                TemplateBlock.PageNumber("page-number", TableCellSettings(textAlign = "center")),
                TemplateBlock.Header("header", listOf(TemplateBlock.Text("header-text", "Fejléc"))),
                TemplateBlock.Footer("footer", listOf(TemplateBlock.Text("footer-text", "Lábléc"))),
                TemplateBlock.Html("html", "<section>{{datum}}</section>"),
            ),
        )

        val decoded = TemplateContentCodec.decode(TemplateContentCodec.encode(content))

        assertThat(decoded).isEqualTo(content)
    }

    @Test
    fun decodeClampsInvalidTableDimensionsAndSkipsUnknownBlocks() {
        val json = JSONObject()
            .put("title", "Legacy")
            .put("kind", "unknown-kind")
            .put(
                "blocks",
                JSONArray()
                    .put(JSONObject().put("type", "unknown").put("id", "ignored"))
                    .put(
                        JSONObject()
                            .put("type", "table")
                            .put("rows", 999)
                            .put("columns", -3)
                            .put("cells", JSONArray().put(JSONArray().put("kept").put("ignored"))),
                    ),
            )
            .toString()

        val decoded = TemplateContentCodec.decode(json)
        val table = decoded.blocks.single() as TemplateBlock.Table

        assertThat(decoded.kind).isEqualTo(TemplateKind.Standard)
        assertThat(table.id).isNotEmpty()
        assertThat(table.rows).isEqualTo(50)
        assertThat(table.columns).isEqualTo(1)
        assertThat(table.cells).containsExactly(listOf("kept", "ignored"))
    }

    @Test
    fun decodeToleratesMissingFieldsAndLegacyImagesBlock() {
        val json = JSONObject()
            .put(
                "blocks",
                JSONArray()
                    .put(JSONObject().put("type", "text"))
                    .put(JSONObject().put("type", "images")),
            )
            .toString()

        val decoded = TemplateContentCodec.decode(json)

        assertThat(decoded.title).isEmpty()
        assertThat(decoded.kind).isEqualTo(TemplateKind.Standard)
        assertThat((decoded.blocks[0] as TemplateBlock.Text).text).isEmpty()
        assertThat((decoded.blocks[1] as TemplateBlock.Images).blocks)
            .containsExactly(TemplateBlock.Image(id = "image-component"))
    }
}
