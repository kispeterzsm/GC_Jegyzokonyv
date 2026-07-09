package hu.gc.jegyzokonyv.domain.usecase

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
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
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import kotlin.test.assertFailsWith

@RunWith(AndroidJUnit4::class)
class ExportPdfUseCaseInstrumentedTest {
    private lateinit var draftDir: File

    @Before
    fun setUp() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        draftDir = File(context.cacheDir, "pdf-export-test-${System.nanoTime()}").apply { mkdirs() }
    }

    @After
    fun tearDown() {
        draftDir.deleteRecursively()
    }

    @Test
    fun exportsNativePdfFromFixedHtmlWithExpectedPagesAndRenderableContent() = runBlocking {
        val useCase = ExportPdfUseCase(
            draftRepository = FakeDraftRepository(
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
            ),
            profileRepository = FakeProfileRepository(),
        )

        val pdf = useCase("draft")

        assertThat(pdf.isFile).isTrue()
        assertThat(pdf.length()).isGreaterThan(1_000L)
        ParcelFileDescriptor.open(pdf, ParcelFileDescriptor.MODE_READ_ONLY).use { descriptor ->
            PdfRenderer(descriptor).use { renderer ->
                assertThat(renderer.pageCount).isEqualTo(2)
                assertThat(renderer.nonWhitePixels(pageIndex = 0)).isGreaterThan(1_000)
                assertThat(renderer.nonWhitePixels(pageIndex = 1)).isGreaterThan(200)
            }
        }
    }

    @Test
    fun safetyTemplateRejectsMissingProfileDataBeforeWritingPdf() = runBlocking {
        val useCase = ExportPdfUseCase(
            draftRepository = FakeDraftRepository(
                draftDir = draftDir,
                html = """<html><body class="safety-walkthrough"></body></html>""",
            ),
            profileRepository = FakeProfileRepository(UserProfile(name = "Csak név")),
        )

        val error = assertFailsWith<MissingProfileInfoException> {
            useCase("draft")
        }

        assertThat(error.missing).containsExactly("cégnév", "aláírás", "bélyegző").inOrder()
        assertThat(draftDir.listFiles { _, name -> name.endsWith(".pdf", ignoreCase = true) }).isEmpty()
    }

    private fun PdfRenderer.nonWhitePixels(pageIndex: Int): Int {
        openPage(pageIndex).use { page ->
            val bitmap = Bitmap.createBitmap(page.width, page.height, Bitmap.Config.ARGB_8888)
            return try {
                bitmap.eraseColor(Color.WHITE)
                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                val pixels = IntArray(bitmap.width * bitmap.height)
                bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
                pixels.count { color -> Color.alpha(color) != 0 && color != Color.WHITE }
            } finally {
                bitmap.recycle()
            }
        }
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
