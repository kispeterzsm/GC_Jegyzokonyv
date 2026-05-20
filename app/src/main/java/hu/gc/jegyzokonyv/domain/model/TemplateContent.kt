package hu.gc.jegyzokonyv.domain.model

data class TemplateContent(
    val title: String,
    val kind: TemplateKind = TemplateKind.Standard,
    val blocks: List<TemplateBlock>,
)

enum class TemplateKind(val jsonValue: String) {
    Standard("standard"),
    SafetyWalkthrough("safety_walkthrough");

    companion object {
        fun fromJson(value: String): TemplateKind =
            entries.firstOrNull { it.jsonValue == value } ?: Standard
    }
}

sealed class TemplateBlock {
    abstract val id: String

    data class Text(override val id: String, val text: String) : TemplateBlock()
    data class Date(override val id: String) : TemplateBlock()
    data class Table(
        override val id: String,
        val rows: Int,
        val columns: Int,
        val hasHeaderColumn: Boolean,
    ) : TemplateBlock()
    data class Signature(override val id: String) : TemplateBlock()
    data class Stamp(override val id: String) : TemplateBlock()
}
