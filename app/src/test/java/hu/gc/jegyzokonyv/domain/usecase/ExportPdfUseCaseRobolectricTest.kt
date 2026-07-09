package hu.gc.jegyzokonyv.domain.usecase

import android.net.Uri
import com.google.common.truth.Truth.assertThat
import hu.gc.jegyzokonyv.data.profile.MissingProfileInfoException
import hu.gc.jegyzokonyv.data.profile.ProfileImageKind
import hu.gc.jegyzokonyv.data.profile.ProfileRepository
import hu.gc.jegyzokonyv.data.profile.UserProfile
import hu.gc.jegyzokonyv.data.repo.DraftRepository
import hu.gc.jegyzokonyv.domain.model.Draft
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.runTest
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.text.PDFTextStripper
import org.junit.Rule
import org.junit.Ignore
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode
import org.robolectric.RobolectricTestRunner
import java.io.File
import kotlin.test.assertFailsWith

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ExportPdfUseCaseRobolectricTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    @Ignore("Robolectric does not currently provide a usable android.graphics.pdf.PdfDocument native document.")
    fun exportsNativePdfWithExpectedPageCountAndMissingImagePlaceholder() = runTest {
        val draftDir = temporaryFolder.newFolder("draft")
        val repository = FakeDraftRepository(
            draftDir = draftDir,
            html = """
                <html><body>
                  <div id="content">
                    <div class="text-block"><p>Első oldal</p></div>
                    <div class="photo-block"><img src="images/missing.jpg"><p>Hiányzó fotó felirata</p></div>
                    <div class="template-page-break"></div>
                    <div class="text-block"><p>Második oldal</p></div>
                  </div>
                </body></html>
            """.trimIndent(),
        )
        val useCase = ExportPdfUseCase(repository, FakeProfileRepository())

        val pdf = useCase("draft")

        assertThat(pdf.isFile).isTrue()
        PDDocument.load(pdf).use { document ->
            assertThat(document.numberOfPages).isEqualTo(2)
            val text = PDFTextStripper().getText(document)
            assertThat(text).contains("Első oldal")
            assertThat(text).contains("Második oldal")
            assertThat(text).contains("Hiányzó kép: images/missing.jpg")
            assertThat(text).contains("Hiányzó fotó felirata")
        }
    }

    @Test
    fun exportParserKeepsRepeatBlocksPageBreaksAndMissingImages() {
        val draftDir = temporaryFolder.newFolder("parser-draft")
        val useCase = ExportPdfUseCase(FakeDraftRepository(draftDir, html = ""), FakeProfileRepository())
        val document = useCase.invokePrivateParser(
            """
            <html><body>
              <div id="content">
                <div class="repeat-header"><div class="text-block"><p>Fejléc</p></div></div>
                <div class="text-block"><p>Törzs</p></div>
                <div class="photo-block"><img src="images/missing.jpg"><p>Hiányzó fotó</p></div>
                <div class="template-page-break"></div>
                <div class="repeat-footer"><div class="text-block"><p>Lábléc</p></div></div>
              </div>
            </body></html>
            """.trimIndent(),
            draftDir,
        )

        val headerBlocks = document.privateField<List<Any>>("headerBlocks")
        val footerBlocks = document.privateField<List<Any>>("footerBlocks")
        val blocks = document.privateField<List<Any>>("blocks")
        val photo = blocks.single { it.javaClass.simpleName == "Photo" }

        assertThat(headerBlocks.map { it.javaClass.simpleName }).containsExactly("Text")
        assertThat(footerBlocks.map { it.javaClass.simpleName }).containsExactly("Text")
        assertThat(blocks.map { it.javaClass.simpleName }).containsExactly("Text", "Photo", "PageBreak").inOrder()
        assertThat(photo.privateField<File?>("image")).isNull()
        assertThat(photo.privateField<String>("source")).isEqualTo("images/missing.jpg")
        assertThat(photo.privateField<String?>("caption")).isEqualTo("Hiányzó fotó")
    }

    @Test
    fun safetyTemplateRejectsMissingProfileDataBeforeWritingPdf() = runTest {
        val draftDir = temporaryFolder.newFolder("safety-draft")
        val repository = FakeDraftRepository(
            draftDir = draftDir,
            html = """<html><body class="safety-walkthrough"></body></html>""",
        )
        val useCase = ExportPdfUseCase(repository, FakeProfileRepository(UserProfile(name = "Csak név")))

        val error = assertFailsWith<MissingProfileInfoException> {
            useCase("draft")
        }

        assertThat(error.missing).containsExactly("cégnév", "aláírás", "bélyegző").inOrder()
        assertThat(draftDir.listFiles { _, name -> name.endsWith(".pdf", ignoreCase = true) }).isEmpty()
    }

    private fun ExportPdfUseCase.invokePrivateParser(html: String, draftDir: File): Any {
        val method = ExportPdfUseCase::class.java.getDeclaredMethod("parseExportDocument", String::class.java, File::class.java)
        method.isAccessible = true
        return method.invoke(this, html, draftDir)
    }

    @Suppress("UNCHECKED_CAST")
    private fun <T> Any.privateField(name: String): T {
        val field = javaClass.getDeclaredField(name)
        field.isAccessible = true
        return field.get(this) as T
    }

    private class FakeDraftRepository(
        private val draftDir: File,
        private val html: String,
    ) : DraftRepository {
        override fun observeDrafts(): Flow<List<Draft>> = emptyFlow()
        override fun observeDraft(draftId: String): Flow<Draft?> = emptyFlow()
        override suspend fun getDraft(draftId: String): Draft? =
            Draft(id = draftId, title = "Teszt jegyzőkönyv", templateId = "template", createdAt = 1L, updatedAt = 1L)

        override suspend fun createDraftFromTemplate(templateId: String): String = error("unused")
        override suspend fun loadHtml(draftId: String): String = html
        override suspend fun saveHtml(draftId: String, html: String) = error("unused")
        override suspend fun setTitle(draftId: String, title: String) = error("unused")
        override suspend fun appendPhotoBlock(draftId: String, relativeImagePath: String, caption: String?) = error("unused")
        override suspend fun deleteDraft(draftId: String) = error("unused")
        override fun draftDir(draftId: String): File = draftDir
        override fun newImageFile(draftId: String): File = error("unused")
        override fun exportPdfTarget(draftId: String, filename: String): File = File(draftDir, filename)
        override fun latestExportedPdf(draftId: String): File? = null
        override fun deleteExportedPdfs(draftId: String) = Unit
    }

    private class FakeProfileRepository(profile: UserProfile = UserProfile()) : ProfileRepository {
        override val profile = MutableStateFlow(profile)
        override suspend fun save(profile: UserProfile) {
            this.profile.value = profile
        }
        override suspend fun saveImage(uri: Uri, kind: ProfileImageKind): Pair<String, String> = error("unused")
        override suspend fun editImage(kind: ProfileImageKind, tolerance: Float): String = error("unused")
        override fun imageFile(path: String): File? = path.takeIf { it.isNotBlank() }?.let(::File)?.takeIf(File::isFile)
    }
}
