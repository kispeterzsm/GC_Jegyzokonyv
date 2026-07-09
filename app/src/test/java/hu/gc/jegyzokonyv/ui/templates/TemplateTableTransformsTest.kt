package hu.gc.jegyzokonyv.ui.templates

import com.google.common.truth.Truth.assertThat
import hu.gc.jegyzokonyv.domain.model.TableAxisSettings
import hu.gc.jegyzokonyv.domain.model.TableCellSettings
import hu.gc.jegyzokonyv.domain.model.TemplateBlock
import org.junit.Test

class TemplateTableTransformsTest {
    @Test
    fun insertRowBelowNormalizesCellsAndSettings() {
        val table = table(
            rows = 2,
            columns = 3,
            cells = listOf(listOf("A"), listOf("B", "C")),
            rowSettings = listOf(TableAxisSettings(backgroundColor = "#111111")),
            cellSettings = listOf(listOf(TableCellSettings(textColor = "#222222"))),
        )

        val updated = TemplateTableTransforms.insertRowBelow(table, row = 0)

        assertThat(updated.rows).isEqualTo(3)
        assertThat(updated.cells).containsExactly(
            listOf("A", "", ""),
            listOf("", "", ""),
            listOf("B", "C", ""),
        ).inOrder()
        assertThat(updated.rowSettings).hasSize(3)
        assertThat(updated.rowSettings[1]).isEqualTo(TableAxisSettings())
        assertThat(updated.cellSettings).hasSize(3)
        assertThat(updated.cellSettings[1]).containsExactly(TableCellSettings(), TableCellSettings(), TableCellSettings()).inOrder()
    }

    @Test
    fun insertColumnRightNormalizesRowsAndSettings() {
        val table = table(
            rows = 2,
            columns = 2,
            cells = listOf(listOf("A", "B"), listOf("C")),
            columnSettings = listOf(TableAxisSettings(textAlign = "right")),
            cellSettings = listOf(listOf(TableCellSettings(bold = true)), emptyList()),
        )

        val updated = TemplateTableTransforms.insertColumnRight(table, column = 0)

        assertThat(updated.columns).isEqualTo(3)
        assertThat(updated.cells).containsExactly(
            listOf("A", "", "B"),
            listOf("C", "", ""),
        ).inOrder()
        assertThat(updated.columnSettings).containsExactly(
            TableAxisSettings(textAlign = "right"),
            TableAxisSettings(),
            TableAxisSettings(),
        ).inOrder()
        assertThat(updated.cellSettings[0]).containsExactly(
            TableCellSettings(bold = true),
            TableCellSettings(),
            TableCellSettings(),
        ).inOrder()
    }

    @Test
    fun deleteRowAndColumnRemoveMatchingSettings() {
        val table = table(
            rows = 3,
            columns = 3,
            cells = listOf(listOf("A", "B", "C"), listOf("D", "E", "F"), listOf("G", "H", "I")),
            rowSettings = listOf(
                TableAxisSettings(backgroundColor = "#1"),
                TableAxisSettings(backgroundColor = "#2"),
                TableAxisSettings(backgroundColor = "#3"),
            ),
            columnSettings = listOf(
                TableAxisSettings(textColor = "#1"),
                TableAxisSettings(textColor = "#2"),
                TableAxisSettings(textColor = "#3"),
            ),
            cellSettings = List(3) { row -> List(3) { column -> TableCellSettings(textAlign = "$row-$column") } },
        )

        val withoutRow = TemplateTableTransforms.deleteRow(table, row = 1)
        val withoutColumn = TemplateTableTransforms.deleteColumn(withoutRow, column = 1)

        assertThat(withoutColumn.rows).isEqualTo(2)
        assertThat(withoutColumn.columns).isEqualTo(2)
        assertThat(withoutColumn.cells).containsExactly(listOf("A", "C"), listOf("G", "I")).inOrder()
        assertThat(withoutColumn.rowSettings.map { it.backgroundColor }).containsExactly("#1", "#3").inOrder()
        assertThat(withoutColumn.columnSettings.map { it.textColor }).containsExactly("#1", "#3").inOrder()
        assertThat(withoutColumn.cellSettings[1][1].textAlign).isEqualTo("2-2")
    }

    @Test
    fun deleteColumnDoesNotRemoveTickColumnsOrLastColumn() {
        val tickColumn = table(
            rows = 2,
            columns = 2,
            cellSettings = listOf(
                listOf(TableCellSettings(), TableCellSettings(toggleCheck = true)),
                listOf(TableCellSettings(), TableCellSettings(toggleCheck = true)),
            ),
        )
        val singleColumn = table(rows = 2, columns = 1)

        assertThat(TemplateTableTransforms.deleteColumn(tickColumn, column = 1)).isEqualTo(tickColumn)
        assertThat(TemplateTableTransforms.deleteColumn(singleColumn, column = 0)).isEqualTo(singleColumn)
    }

    @Test
    fun maxRowsAndColumnsAreNotExpanded() {
        val maxRows = table(rows = 50, columns = 2)
        val maxColumns = table(rows = 2, columns = 20)

        assertThat(TemplateTableTransforms.insertRowBelow(maxRows, row = 0)).isEqualTo(maxRows)
        assertThat(TemplateTableTransforms.insertColumnRight(maxColumns, column = 0)).isEqualTo(maxColumns)
    }

    @Test
    fun rowAndColumnSettingsPropagateToCells() {
        val table = table(
            rows = 2,
            columns = 2,
            rowSettings = listOf(TableAxisSettings(headerRow = true)),
        )
        val rowSettings = TableAxisSettings(
            backgroundColor = "#eeeeee",
            textColor = "#111111",
            textAlign = "center",
            editable = false,
            hideIfEmpty = true,
            headerRow = true,
        )
        val columnSettings = TableAxisSettings(
            backgroundColor = "#222222",
            textColor = "#ffffff",
            textAlign = "right",
            editable = true,
            hideIfEmpty = true,
        )

        val withRow = TemplateTableTransforms.applyRowSettings(table, row = 0, settings = rowSettings)
        val withColumn = TemplateTableTransforms.applyColumnSettings(withRow, column = 1, settings = columnSettings)

        assertThat(withRow.rowSettings[0].hideIfEmpty).isFalse()
        assertThat(withRow.cellSettings[0][0].backgroundColor).isEqualTo("#eeeeee")
        assertThat(withRow.cellSettings[0][0].hideIfEmpty).isFalse()
        assertThat(withColumn.columnSettings[1]).isEqualTo(columnSettings)
        assertThat(withColumn.cellSettings[0][1].hideIfEmpty).isFalse()
        assertThat(withColumn.cellSettings[1][1].hideIfEmpty).isTrue()
        assertThat(withColumn.cellSettings[1][1].textAlign).isEqualTo("right")
    }

    @Test
    fun cellTextAndSettingsUpdateSingleNormalizedCell() {
        val table = table(rows = 2, columns = 2)

        val withText = TemplateTableTransforms.updateCellText(table, row = 1, column = 1, text = "Új")
        val withSettings = TemplateTableTransforms.updateCellSettings(
            withText,
            row = 1,
            column = 1,
            settings = TableCellSettings(italic = true),
        )

        assertThat(withSettings.cells).containsExactly(listOf("", ""), listOf("", "Új")).inOrder()
        assertThat(withSettings.cellSettings[1][1].italic).isTrue()
        assertThat(withSettings.cellSettings[0][0]).isEqualTo(TableCellSettings())
    }

    private fun table(
        rows: Int,
        columns: Int,
        cells: List<List<String>> = emptyList(),
        rowSettings: List<TableAxisSettings> = emptyList(),
        columnSettings: List<TableAxisSettings> = emptyList(),
        cellSettings: List<List<TableCellSettings>> = emptyList(),
    ) = TemplateBlock.Table(
        id = "table",
        rows = rows,
        columns = columns,
        hasHeaderColumn = false,
        cells = cells,
        rowSettings = rowSettings,
        columnSettings = columnSettings,
        cellSettings = cellSettings,
    )
}
