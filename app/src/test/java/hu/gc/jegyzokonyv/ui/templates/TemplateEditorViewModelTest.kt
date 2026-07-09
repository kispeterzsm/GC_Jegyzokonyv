package hu.gc.jegyzokonyv.ui.templates

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import com.google.common.truth.Truth.assertThat
import hu.gc.jegyzokonyv.data.profile.ProfileImageKind
import hu.gc.jegyzokonyv.data.profile.ProfileRepository
import hu.gc.jegyzokonyv.data.profile.UserProfile
import hu.gc.jegyzokonyv.data.repo.TemplateRepository
import hu.gc.jegyzokonyv.domain.html.HtmlEngine
import hu.gc.jegyzokonyv.domain.model.Template
import hu.gc.jegyzokonyv.domain.model.TemplateBlock
import hu.gc.jegyzokonyv.domain.model.TemplateContent
import hu.gc.jegyzokonyv.domain.usecase.SaveTemplateUseCase
import hu.gc.jegyzokonyv.testing.MainDispatcherRule
import hu.gc.jegyzokonyv.ui.nav.Routes
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import java.io.File

class TemplateEditorViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun builtInTemplateIsReadOnlyAndMutationsAreIgnored() = runTest {
        val repository = FakeTemplateRepository(
            templates = mapOf("built-in" to Template("built-in", "Beépített", "Cím", isBuiltIn = true)),
            contents = mapOf("built-in" to TemplateContent("Cím", blocks = listOf(TemplateBlock.Text("text", "Eredeti")))),
        )
        val viewModel = viewModel(repository, templateId = "built-in")
        val before = viewModel.state.value

        viewModel.onNameChange("Módosított")
        viewModel.onTitleChange("Más cím")
        viewModel.onTextBlockChange("text", "Új")
        viewModel.addTableBlock(rows = 2, columns = 2, hasHeaderColumn = false)
        viewModel.removeBlock("text")
        viewModel.save(fallbackName = "Fallback") {}

        assertThat(before.isReadOnly).isTrue()
        assertThat(before.blocks.any { it is TemplateBlock.Images }).isTrue()
        assertThat(viewModel.state.value).isEqualTo(before)
        assertThat(repository.updatedContent).isNull()
        assertThat(repository.createdContent).isNull()
    }

    @Test
    fun imageContainerAndImageComponentCannotBeRemoved() = runTest {
        val repository = FakeTemplateRepository(
            starter = TemplateContent(title = "", blocks = listOf(TemplateBlock.Text("text", "Body"))),
        )
        val viewModel = viewModel(repository)
        val images = viewModel.state.value.blocks.filterIsInstance<TemplateBlock.Images>().single()

        viewModel.removeBlock(images.id)
        viewModel.removeNestedBlock(images.id, "image-component")

        val updatedImages = viewModel.state.value.blocks.filterIsInstance<TemplateBlock.Images>().single()
        assertThat(updatedImages.id).isEqualTo(images.id)
        assertThat(updatedImages.blocks).contains(TemplateBlock.Image("image-component"))
    }

    @Test
    fun previewHtmlChangedExtractsTableHtmlAndEditableTextValues() = runTest {
        val repository = FakeTemplateRepository(
            starter = TemplateContent(
                title = "Sablon",
                blocks = listOf(
                    TemplateBlock.Text("text", "Régi"),
                    TemplateBlock.Table("table", rows = 1, columns = 2, hasHeaderColumn = false, cells = listOf(listOf("A", "B"))),
                    TemplateBlock.Html("html", "<b>régi</b>"),
                    TemplateBlock.Header("header", listOf(TemplateBlock.Text("header-text", "Régi fejléc"))),
                    TemplateBlock.Images("images", listOf(TemplateBlock.Text("image-text", "Régi képszöveg"), TemplateBlock.Image("image-component"))),
                ),
            ),
        )
        val viewModel = viewModel(repository)

        viewModel.onPreviewHtmlChanged(
            """
            <html><body>
              <div class="text-block" data-template-block-id="text"><p contenteditable="true">Új szöveg</p></div>
              <table class="editable-table" data-template-block-id="table">
                <tr><td>Új A</td><td>Új B</td></tr>
              </table>
              <div class="html-block" data-template-block-id="html"><span>új html</span></div>
              <div class="text-block" data-template-block-id="header-text"><p contenteditable="true">Új fejléc</p></div>
              <div class="text-block" data-template-block-id="image-text"><p contenteditable="true">Új képszöveg</p></div>
            </body></html>
            """.trimIndent(),
        )

        val blocks = viewModel.state.value.blocks
        assertThat(blocks.filterIsInstance<TemplateBlock.Text>().single().text).isEqualTo("Új szöveg")
        assertThat(blocks.filterIsInstance<TemplateBlock.Table>().single().cells).containsExactly(listOf("Új A", "Új B"))
        assertThat(blocks.filterIsInstance<TemplateBlock.Html>().single().html).isEqualTo("<span>új html</span>")
        assertThat(blocks.filterIsInstance<TemplateBlock.Header>().single().blocks.filterIsInstance<TemplateBlock.Text>().single().text)
            .isEqualTo("Új fejléc")
        assertThat(blocks.filterIsInstance<TemplateBlock.Images>().single().blocks.filterIsInstance<TemplateBlock.Text>().single().text)
            .isEqualTo("Új képszöveg")
    }

    @Test
    fun savePersistsEditableContentAndClearsTemplateTitle() = runTest {
        val repository = FakeTemplateRepository(
            starter = TemplateContent(title = "Visible title", blocks = listOf(TemplateBlock.Text("text", "Body"))),
        )
        val viewModel = viewModel(repository)

        viewModel.onNameChange("Mentett sablon")
        viewModel.save(fallbackName = "Fallback") {}

        assertThat(repository.createdName).isEqualTo("Mentett sablon")
        assertThat(repository.createdContent!!.title).isEmpty()
        assertThat(repository.createdContent!!.blocks).contains(TemplateBlock.Text("text", "Body"))
        assertThat(viewModel.state.value.isSaving).isFalse()
    }

    private fun viewModel(
        repository: FakeTemplateRepository,
        templateId: String? = null,
    ): TemplateEditorViewModel {
        val savedStateHandle = SavedStateHandle(
            if (templateId == null) emptyMap() else mapOf(Routes.ARG_TEMPLATE_ID to templateId),
        )
        return TemplateEditorViewModel(
            savedStateHandle = savedStateHandle,
            templateRepository = repository,
            saveTemplate = SaveTemplateUseCase(repository),
            htmlEngine = FakeHtmlEngine(),
            profileRepository = FakeProfileRepository(),
        )
    }

    private class FakeTemplateRepository(
        private val templates: Map<String, Template> = emptyMap(),
        private val contents: Map<String, TemplateContent> = emptyMap(),
        private val starter: TemplateContent = TemplateContent(title = "", blocks = emptyList()),
    ) : TemplateRepository {
        var createdName: String? = null
        var createdContent: TemplateContent? = null
        var updatedContent: TemplateContent? = null

        override fun observeTemplates(): Flow<List<Template>> = flowOf(templates.values.toList())
        override suspend fun getTemplate(id: String): Template? = templates[id]
        override suspend fun loadContent(templateId: String): TemplateContent? = contents[templateId]
        override suspend fun starterContent(): TemplateContent = starter
        override suspend fun createUserTemplate(name: String, content: TemplateContent): String {
            createdName = name
            createdContent = content
            return "created"
        }
        override suspend fun updateUserTemplate(id: String, name: String, content: TemplateContent) {
            updatedContent = content
        }
        override suspend fun deleteUserTemplate(id: String) = Unit
    }

    private class FakeHtmlEngine : HtmlEngine {
        override fun renderTemplate(content: TemplateContent, title: String, todayIso: String, profile: UserProfile?): String =
            "<html><body>$title $todayIso ${content.blocks.size}</body></html>"

        override fun setTitle(html: String, title: String): String = html
        override fun appendPhotoBlock(html: String, relativeImagePath: String, caption: String?): String = html
    }

    private class FakeProfileRepository : ProfileRepository {
        override val profile = MutableStateFlow(UserProfile())
        override suspend fun save(profile: UserProfile) {
            this.profile.value = profile
        }
        override suspend fun saveImage(uri: Uri, kind: ProfileImageKind): Pair<String, String> = error("unused")
        override suspend fun editImage(kind: ProfileImageKind, tolerance: Float): String = error("unused")
        override fun imageFile(path: String): File? = null
    }
}
