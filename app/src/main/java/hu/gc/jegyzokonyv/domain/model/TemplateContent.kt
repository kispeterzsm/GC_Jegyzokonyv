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
        val cells: List<List<String>> = emptyList(),
        val rowSettings: List<TableAxisSettings> = emptyList(),
        val columnSettings: List<TableAxisSettings> = emptyList(),
        val cellSettings: List<List<TableCellSettings>> = emptyList(),
    ) : TemplateBlock()
    data class Signature(override val id: String) : TemplateBlock()
    data class Stamp(override val id: String) : TemplateBlock()
    data class Images(override val id: String) : TemplateBlock()
    data class PageBreak(override val id: String) : TemplateBlock()
    data class Html(override val id: String, val html: String) : TemplateBlock()
}

data class TableAxisSettings(
    val backgroundColor: String = "",
    val textColor: String = "",
    val textAlign: String = "left",
    val tickXBackgroundColor: String = "",
    val tickXTextColor: String = "",
    val tickCheckedBackgroundColor: String = "",
    val tickCheckedTextColor: String = "",
    val editable: Boolean? = null,
    val hideIfEmpty: Boolean = false,
    val mergeAll: Boolean = false,
)

data class TableCellSettings(
    val backgroundColor: String = "",
    val textColor: String = "",
    val textAlign: String = "left",
    val tickXBackgroundColor: String = "",
    val tickXTextColor: String = "",
    val tickCheckedBackgroundColor: String = "",
    val tickCheckedTextColor: String = "",
    val editable: Boolean? = null,
    val hideIfEmpty: Boolean = false,
    val toggleCheck: Boolean = false,
    val mergeRight: Boolean = false,
)
