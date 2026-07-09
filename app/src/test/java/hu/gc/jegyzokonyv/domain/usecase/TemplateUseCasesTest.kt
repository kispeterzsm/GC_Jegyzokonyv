package hu.gc.jegyzokonyv.domain.usecase

import com.google.common.truth.Truth.assertThat
import hu.gc.jegyzokonyv.data.repo.DraftRepository
import hu.gc.jegyzokonyv.data.repo.TemplateRepository
import hu.gc.jegyzokonyv.domain.model.Draft
import hu.gc.jegyzokonyv.domain.model.Template
import hu.gc.jegyzokonyv.domain.model.TemplateBlock
import hu.gc.jegyzokonyv.domain.model.TemplateContent
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Test
import java.io.File
import kotlin.test.assertFailsWith

class TemplateUseCasesTest {
    @Test
    fun saveTemplateCreatesWithFallbackNameAndTrimmedTitle() = runTest {
        val repository = FakeTemplateRepository()
        val useCase = SaveTemplateUseCase(repository)

        val id = useCase(
            existingId = null,
            name = "   ",
            content = TemplateContent(title = "  Cím  ", blocks = listOf(TemplateBlock.Text("text", "body"))),
            fallbackName = "Alapértelmezett",
        )

        assertThat(id).isEqualTo("created-1")
        assertThat(repository.createdName).isEqualTo("Alapértelmezett")
        assertThat(repository.createdContent!!.title).isEqualTo("Cím")
    }

    @Test
    fun saveTemplateUpdatesExistingAndReturnsExistingId() = runTest {
        val repository = FakeTemplateRepository()
        val useCase = SaveTemplateUseCase(repository)

        val id = useCase(
            existingId = "existing",
            name = " Mentett ",
            content = TemplateContent(title = "  Cím  ", blocks = emptyList()),
            fallbackName = "Fallback",
        )

        assertThat(id).isEqualTo("existing")
        assertThat(repository.updatedId).isEqualTo("existing")
        assertThat(repository.updatedName).isEqualTo("Mentett")
        assertThat(repository.updatedContent!!.title).isEqualTo("Cím")
    }

    @Test
    fun duplicateTemplateCopiesContentResetsTitleAndRebuildsAllBlockIds() = runTest {
        val originalContent = TemplateContent(
            title = "Eredeti cím",
            blocks = listOf(
                TemplateBlock.Text("text", "body"),
                TemplateBlock.Header("header", listOf(TemplateBlock.Text("header-text", "header"))),
                TemplateBlock.Images("images", listOf(TemplateBlock.Image("image"))),
                TemplateBlock.Html("html", "<b>html</b>"),
            ),
        )
        val repository = FakeTemplateRepository(
            templates = mapOf("source" to Template("source", "Sablon", "Cím", isBuiltIn = false)),
            contents = mapOf("source" to originalContent),
        )

        val id = DuplicateTemplateUseCase(repository)("source", "másolat")

        assertThat(id).isEqualTo("created-1")
        assertThat(repository.createdName).isEqualTo("Sablon másolat")
        assertThat(repository.createdContent!!.title).isEmpty()
        assertThat(repository.createdContent!!.blocks.map { it::class }).containsExactlyElementsIn(originalContent.blocks.map { it::class }).inOrder()
        assertThat(repository.createdContent!!.blocks.recursiveIds()).containsNoneIn(originalContent.blocks.recursiveIds())
        assertThat(repository.createdContent!!.blocks.filterIsInstance<TemplateBlock.Text>().single().text).isEqualTo("body")
        assertThat(repository.createdContent!!.blocks.filterIsInstance<TemplateBlock.Html>().single().html).isEqualTo("<b>html</b>")
    }

    @Test
    fun duplicateTemplateFailsWhenSourceOrContentIsMissing() = runTest {
        val repository = FakeTemplateRepository()

        val error = assertFailsWith<IllegalStateException> {
            DuplicateTemplateUseCase(repository)("missing", "copy")
        }

        assertThat(error).hasMessageThat().contains("Template not found")
    }

    @Test
    fun deleteTemplateDelegatesToRepository() = runTest {
        val repository = FakeTemplateRepository()

        DeleteTemplateUseCase(repository)("template-id")

        assertThat(repository.deletedIds).containsExactly("template-id")
    }

    @Test
    fun createDraftFromTemplateDelegatesAndReturnsDraftId() = runTest {
        val repository = FakeDraftRepository(createResult = "draft-id")

        val draftId = CreateDraftFromTemplateUseCase(repository)("template-id")

        assertThat(draftId).isEqualTo("draft-id")
        assertThat(repository.createdFromTemplateIds).containsExactly("template-id")
    }

    private fun List<TemplateBlock>.recursiveIds(): List<String> = flatMap { block ->
        val nested = when (block) {
            is TemplateBlock.Header -> block.blocks.recursiveIds()
            is TemplateBlock.Footer -> block.blocks.recursiveIds()
            is TemplateBlock.Images -> block.blocks.recursiveIds()
            else -> emptyList()
        }
        listOf(block.id) + nested
    }

    private class FakeTemplateRepository(
        private val templates: Map<String, Template> = emptyMap(),
        private val contents: Map<String, TemplateContent> = emptyMap(),
    ) : TemplateRepository {
        var createdName: String? = null
        var createdContent: TemplateContent? = null
        var updatedId: String? = null
        var updatedName: String? = null
        var updatedContent: TemplateContent? = null
        val deletedIds = mutableListOf<String>()

        override fun observeTemplates(): Flow<List<Template>> = flowOf(templates.values.toList())
        override suspend fun getTemplate(id: String): Template? = templates[id]
        override suspend fun loadContent(templateId: String): TemplateContent? = contents[templateId]
        override suspend fun starterContent(): TemplateContent = TemplateContent(title = "", blocks = emptyList())
        override suspend fun createUserTemplate(name: String, content: TemplateContent): String {
            createdName = name
            createdContent = content
            return "created-1"
        }
        override suspend fun updateUserTemplate(id: String, name: String, content: TemplateContent) {
            updatedId = id
            updatedName = name
            updatedContent = content
        }
        override suspend fun deleteUserTemplate(id: String) {
            deletedIds += id
        }
    }

    private class FakeDraftRepository(
        private val createResult: String,
    ) : DraftRepository {
        val createdFromTemplateIds = mutableListOf<String>()

        override fun observeDrafts(): Flow<List<Draft>> = emptyFlow()
        override fun observeDraft(draftId: String): Flow<Draft?> = emptyFlow()
        override suspend fun getDraft(draftId: String): Draft? = null
        override suspend fun createDraftFromTemplate(templateId: String): String {
            createdFromTemplateIds += templateId
            return createResult
        }
        override suspend fun loadHtml(draftId: String): String = ""
        override suspend fun saveHtml(draftId: String, html: String) = Unit
        override suspend fun setTitle(draftId: String, title: String) = Unit
        override suspend fun appendPhotoBlock(draftId: String, relativeImagePath: String, caption: String?) = Unit
        override suspend fun deleteDraft(draftId: String) = Unit
        override fun draftDir(draftId: String): File = error("unused")
        override fun newImageFile(draftId: String): File = error("unused")
        override fun exportPdfTarget(draftId: String, filename: String): File = error("unused")
        override fun latestExportedPdf(draftId: String): File? = null
        override fun deleteExportedPdfs(draftId: String) = Unit
    }
}
