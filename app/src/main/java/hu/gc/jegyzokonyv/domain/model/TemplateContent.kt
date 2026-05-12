package hu.gc.jegyzokonyv.domain.model

data class TemplateContent(
    val title: String,
    val blocks: List<TemplateBlock>,
)

sealed class TemplateBlock {
    abstract val id: String

    data class Text(override val id: String, val text: String) : TemplateBlock()
    data class Date(override val id: String) : TemplateBlock()
}
