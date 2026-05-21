package hu.gc.jegyzokonyv.domain.usecase

import hu.gc.jegyzokonyv.data.repo.TemplateRepository
import hu.gc.jegyzokonyv.domain.model.TemplateBlock
import java.util.UUID
import javax.inject.Inject

class DuplicateTemplateUseCase @Inject constructor(
    private val templateRepository: TemplateRepository,
) {
    suspend operator fun invoke(sourceId: String, copySuffix: String): String {
        val source = templateRepository.getTemplate(sourceId)
            ?: error("Template not found: $sourceId")
        val content = templateRepository.loadContent(sourceId)
            ?: error("Template content not found: $sourceId")
        val rebuiltBlocks = content.blocks.map { it.rebuildIds() }
        val newName = "${source.name} $copySuffix".trim()
        return templateRepository.createUserTemplate(
            name = newName,
            content = content.copy(title = "", blocks = rebuiltBlocks),
        )
    }

    private fun TemplateBlock.rebuildIds(): TemplateBlock {
        val newId = UUID.randomUUID().toString()
        return when (this) {
            is TemplateBlock.Text -> TemplateBlock.Text(id = newId, text = text, settings = settings)
            is TemplateBlock.Date -> TemplateBlock.Date(id = newId, settings = settings)
            is TemplateBlock.Table -> copy(id = newId)
            is TemplateBlock.Signature -> TemplateBlock.Signature(id = newId)
            is TemplateBlock.Stamp -> TemplateBlock.Stamp(id = newId)
            is TemplateBlock.Images -> TemplateBlock.Images(id = newId, blocks = blocks.map { it.rebuildIds() })
            is TemplateBlock.Image -> TemplateBlock.Image(id = newId)
            is TemplateBlock.PageBreak -> TemplateBlock.PageBreak(id = newId)
            is TemplateBlock.PageNumber -> TemplateBlock.PageNumber(id = newId, settings = settings)
            is TemplateBlock.Header -> TemplateBlock.Header(id = newId, blocks = blocks.map { it.rebuildIds() })
            is TemplateBlock.Footer -> TemplateBlock.Footer(id = newId, blocks = blocks.map { it.rebuildIds() })
            is TemplateBlock.Html -> copy(id = newId)
        }
    }
}
