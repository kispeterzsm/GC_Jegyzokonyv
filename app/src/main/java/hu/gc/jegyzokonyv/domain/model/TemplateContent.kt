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

    data class Text(override val id: String, val text: String, val settings: TableCellSettings = TableCellSettings()) : TemplateBlock()
    data class Date(override val id: String, val settings: TableCellSettings = TableCellSettings()) : TemplateBlock()
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
    data class ProfileData(override val id: String, val field: ProfileDataField) : TemplateBlock()
    data class Images(override val id: String, val blocks: List<TemplateBlock> = listOf(Image(id = "image-component"))) : TemplateBlock()
    data class Image(override val id: String) : TemplateBlock()
    data class PageBreak(override val id: String) : TemplateBlock()
    data class PageNumber(override val id: String, val settings: TableCellSettings = TableCellSettings()) : TemplateBlock()
    data class Header(override val id: String, val blocks: List<TemplateBlock> = emptyList()) : TemplateBlock()
    data class Footer(override val id: String, val blocks: List<TemplateBlock> = emptyList()) : TemplateBlock()
    data class Html(override val id: String, val html: String) : TemplateBlock()
}

enum class ProfileDataField(val jsonValue: String) {
    Name("name"),
    CompanyName("company_name"),
    Phone("phone"),
    Email("email");

    companion object {
        fun fromJson(value: String): ProfileDataField =
            entries.firstOrNull { it.jsonValue == value } ?: Name
    }
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
    val bold: Boolean = false,
    val italic: Boolean = false,
    val hideIfEmpty: Boolean = false,
    val mergeAll: Boolean = false,
    val headerRow: Boolean = false,
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
    val bold: Boolean = false,
    val italic: Boolean = false,
    val hideIfEmpty: Boolean = false,
    val toggleCheck: Boolean = false,
    val mergeRight: Boolean = false,
)
