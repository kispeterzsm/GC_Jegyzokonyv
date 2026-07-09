package hu.gc.jegyzokonyv.data.template

import com.google.common.truth.Truth.assertThat
import hu.gc.jegyzokonyv.domain.model.TemplateBlock
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.test.assertFailsWith

class DocxTemplateConverterTest {
    private val converter = DocxTemplateConverter()

    @Test
    fun convertsParagraphsTablesHeaderFooterPageBreaksAndImages() {
        val docx = docxBytes(
            "word/header1.xml" to wordDocument(
                """
                <w:p><w:r><w:t>Fejléc</w:t></w:r></w:p>
                """.trimIndent(),
            ),
            "word/document.xml" to wordDocument(
                """
                <w:p><w:r><w:t>Első bekezdés</w:t></w:r></w:p>
                <w:p><w:r><w:br w:type="page"/></w:r></w:p>
                <w:p><w:r><w:drawing/></w:r></w:p>
                <w:tbl>
                  <w:tr>
                    <w:tc><w:p><w:r><w:t>H1</w:t></w:r></w:p></w:tc>
                    <w:tc><w:p><w:r><w:t>H2</w:t></w:r></w:p></w:tc>
                  </w:tr>
                  <w:tr>
                    <w:tc><w:p><w:r><w:t>A</w:t></w:r></w:p></w:tc>
                    <w:tc><w:p><w:r><w:t>B</w:t></w:r></w:p></w:tc>
                  </w:tr>
                </w:tbl>
                """.trimIndent(),
            ),
            "word/footer1.xml" to wordDocument(
                """
                <w:p><w:r><w:t>Lábléc</w:t></w:r></w:p>
                """.trimIndent(),
            ),
        )

        val content = converter.convert(ByteArrayInputStream(docx), title = "Importált")

        assertThat(content.title).isEqualTo("Importált")
        assertThat(content.blocks.filterIsInstance<TemplateBlock.Header>().single().blocks.filterIsInstance<TemplateBlock.Text>().single().text)
            .isEqualTo("Fejléc")
        assertThat(content.blocks.any { it is TemplateBlock.PageBreak }).isTrue()
        assertThat(content.blocks.filterIsInstance<TemplateBlock.Text>().map { it.text })
            .containsAtLeast("Első bekezdés", "[Kép a DOCX dokumentumból]")
        val table = content.blocks.filterIsInstance<TemplateBlock.Table>().single()
        assertThat(table.rows).isEqualTo(2)
        assertThat(table.columns).isEqualTo(2)
        assertThat(table.cells).containsExactly(listOf("H1", "H2"), listOf("A", "B")).inOrder()
        assertThat(table.rowSettings.single().headerRow).isTrue()
        assertThat(content.blocks.filterIsInstance<TemplateBlock.Footer>().single().blocks.single())
            .isInstanceOf(TemplateBlock.Text::class.java)
        assertThat(content.blocks.any { it is TemplateBlock.Images }).isTrue()
    }

    @Test
    fun throwsWhenDocxHasNoDocumentXml() {
        val docx = docxBytes("word/header1.xml" to wordDocument("<w:p/>"))

        val error = assertFailsWith<IllegalStateException> {
            converter.convert(ByteArrayInputStream(docx), title = "Hibás")
        }

        assertThat(error).hasMessageThat().contains("nem tartalmaz dokumentumot")
    }

    @Test
    fun rejectsXmlExternalEntities() {
        val maliciousXml = """
            <!DOCTYPE w:document [
              <!ENTITY xxe SYSTEM "file:///etc/passwd">
            ]>
            <w:document xmlns:w="$WORD_NAMESPACE">
              <w:body><w:p><w:r><w:t>&xxe;</w:t></w:r></w:p></w:body>
            </w:document>
        """.trimIndent().trim()
        val docx = docxBytes("word/document.xml" to maliciousXml)

        assertFailsWith<Exception> {
            converter.convert(ByteArrayInputStream(docx), title = "XXE")
        }
    }

    @Test
    fun importsBulletsTabsAndLineBreaks() {
        val docx = docxBytes(
            "word/document.xml" to wordDocument(
                """
                <w:p>
                  <w:pPr><w:numPr/></w:pPr>
                  <w:r><w:t>Első</w:t></w:r>
                  <w:r><w:tab/></w:r>
                  <w:r><w:t>Második</w:t></w:r>
                  <w:r><w:br/></w:r>
                  <w:r><w:t>Harmadik</w:t></w:r>
                </w:p>
                """.trimIndent(),
            ),
        )

        val text = converter.convert(ByteArrayInputStream(docx), title = "Lista")
            .blocks
            .filterIsInstance<TemplateBlock.Text>()
            .single()
            .text

        assertThat(text).isEqualTo("• Első\tMásodik\nHarmadik")
    }

    private fun docxBytes(vararg entries: Pair<String, String>): ByteArray {
        val output = ByteArrayOutputStream()
        ZipOutputStream(output).use { zip ->
            entries.forEach { (name, content) ->
                zip.putNextEntry(ZipEntry(name))
                zip.write(content.toByteArray(Charsets.UTF_8))
                zip.closeEntry()
            }
        }
        return output.toByteArray()
    }

    private fun wordDocument(body: String): String = """
        <w:document xmlns:w="$WORD_NAMESPACE">
          <w:body>
            $body
          </w:body>
        </w:document>
    """.trimIndent().trim()

    private companion object {
        const val WORD_NAMESPACE = "http://schemas.openxmlformats.org/wordprocessingml/2006/main"
    }
}
