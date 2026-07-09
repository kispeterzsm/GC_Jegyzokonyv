package hu.gc.jegyzokonyv.ui.templates

import hu.gc.jegyzokonyv.domain.model.TableAxisSettings
import hu.gc.jegyzokonyv.domain.model.TableCellSettings
import hu.gc.jegyzokonyv.domain.model.TemplateBlock

internal object TemplateTableTransforms {
    fun updateCellText(table: TemplateBlock.Table, row: Int, column: Int, text: String): TemplateBlock.Table =
        table.copy(cells = table.normalizedCells().mapIndexed { rowIndex, values ->
            if (rowIndex == row) values.mapIndexed { columnIndex, value -> if (columnIndex == column) text else value } else values
        })

    fun updateCellSettings(table: TemplateBlock.Table, row: Int, column: Int, settings: TableCellSettings): TemplateBlock.Table =
        table.copy(cellSettings = table.normalizedCellSettings().mapIndexed { rowIndex, values ->
            if (rowIndex == row) values.mapIndexed { columnIndex, value -> if (columnIndex == column) settings else value } else values
        })

    fun insertRowBelow(table: TemplateBlock.Table, row: Int): TemplateBlock.Table {
        if (table.rows >= MAX_ROWS) return table
        val insertAt = (row + 1).coerceIn(0, table.rows.coerceIn(1, MAX_ROWS))
        val columns = table.columns.coerceIn(1, MAX_COLUMNS)
        return table.copy(
            rows = table.rows + 1,
            cells = table.normalizedCells().toMutableList().apply { add(insertAt, List(columns) { "" }) },
            rowSettings = table.normalizedRowSettings().toMutableList().apply { add(insertAt, TableAxisSettings()) },
            cellSettings = table.normalizedCellSettings().toMutableList().apply { add(insertAt, List(columns) { TableCellSettings() }) },
        )
    }

    fun insertColumnRight(table: TemplateBlock.Table, column: Int): TemplateBlock.Table {
        if (table.columns >= MAX_COLUMNS) return table
        val rows = table.rows.coerceIn(1, MAX_ROWS)
        val insertAt = (column + 1).coerceIn(0, table.columns.coerceIn(1, MAX_COLUMNS))
        return table.copy(
            columns = table.columns + 1,
            cells = table.normalizedCells().map { row -> row.toMutableList().apply { add(insertAt, "") } },
            columnSettings = table.normalizedColumnSettings().toMutableList().apply { add(insertAt, TableAxisSettings()) },
            cellSettings = table.normalizedCellSettings().map { row -> row.toMutableList().apply { add(insertAt, TableCellSettings()) } }.take(rows),
        )
    }

    fun deleteRow(table: TemplateBlock.Table, row: Int): TemplateBlock.Table {
        if (table.rows <= 1) return table
        val removeAt = row.coerceIn(0, table.rows - 1)
        return table.copy(
            rows = table.rows - 1,
            cells = table.normalizedCells().filterIndexed { index, _ -> index != removeAt },
            rowSettings = table.normalizedRowSettings().filterIndexed { index, _ -> index != removeAt },
            cellSettings = table.normalizedCellSettings().filterIndexed { index, _ -> index != removeAt },
        )
    }

    fun deleteColumn(table: TemplateBlock.Table, column: Int): TemplateBlock.Table {
        if (table.columns <= 1) return table
        val removeAt = column.coerceIn(0, table.columns - 1)
        val hasTickCells = table.normalizedCellSettings().any { row -> row.getOrNull(removeAt)?.toggleCheck == true }
        if (hasTickCells) return table
        return table.copy(
            columns = table.columns - 1,
            cells = table.normalizedCells().map { row -> row.filterIndexed { index, _ -> index != removeAt } },
            columnSettings = table.normalizedColumnSettings().filterIndexed { index, _ -> index != removeAt },
            cellSettings = table.normalizedCellSettings().map { row -> row.filterIndexed { index, _ -> index != removeAt } },
        )
    }

    fun applyRowSettings(table: TemplateBlock.Table, row: Int, settings: TableAxisSettings): TemplateBlock.Table {
        val safeRow = row.coerceIn(0, table.rows.coerceIn(1, MAX_ROWS) - 1)
        val safeSettings = if (settings.headerRow) settings.copy(hideIfEmpty = false) else settings
        return table.copy(
            rowSettings = table.normalizedRowSettings().mapIndexed { index, value -> if (index == safeRow) safeSettings else value },
            cellSettings = table.normalizedCellSettings().mapIndexed { rowIndex, values ->
                if (rowIndex == safeRow) values.map { it.withAxisSettings(safeSettings) } else values
            },
        )
    }

    fun applyColumnSettings(table: TemplateBlock.Table, column: Int, settings: TableAxisSettings): TemplateBlock.Table {
        val safeColumn = column.coerceIn(0, table.columns.coerceIn(1, MAX_COLUMNS) - 1)
        return table.copy(
            columnSettings = table.normalizedColumnSettings().mapIndexed { index, value -> if (index == safeColumn) settings else value },
            cellSettings = table.normalizedCellSettings().mapIndexed { rowIndex, values ->
                val isHeaderRow = table.rowSettings.getOrNull(rowIndex)?.headerRow == true
                values.mapIndexed { columnIndex, value ->
                    if (columnIndex == safeColumn) value.withAxisSettings(settings, keepVisible = isHeaderRow) else value
                }
            },
        )
    }

    private fun TemplateBlock.Table.normalizedCells(): List<List<String>> =
        List(rows.coerceIn(1, MAX_ROWS)) { row -> List(columns.coerceIn(1, MAX_COLUMNS)) { column -> cells.getOrNull(row)?.getOrNull(column).orEmpty() } }

    private fun TemplateBlock.Table.normalizedRowSettings(): List<TableAxisSettings> =
        List(rows.coerceIn(1, MAX_ROWS)) { row -> rowSettings.getOrNull(row) ?: TableAxisSettings() }

    private fun TemplateBlock.Table.normalizedColumnSettings(): List<TableAxisSettings> =
        List(columns.coerceIn(1, MAX_COLUMNS)) { column -> columnSettings.getOrNull(column) ?: TableAxisSettings() }

    private fun TemplateBlock.Table.normalizedCellSettings(): List<List<TableCellSettings>> =
        List(rows.coerceIn(1, MAX_ROWS)) { row -> List(columns.coerceIn(1, MAX_COLUMNS)) { column -> cellSettings.getOrNull(row)?.getOrNull(column) ?: TableCellSettings() } }

    private fun TableCellSettings.withAxisSettings(settings: TableAxisSettings, keepVisible: Boolean = false): TableCellSettings = copy(
        backgroundColor = settings.backgroundColor,
        textColor = settings.textColor,
        textAlign = settings.textAlign,
        tickXBackgroundColor = settings.tickXBackgroundColor,
        tickXTextColor = settings.tickXTextColor,
        tickCheckedBackgroundColor = settings.tickCheckedBackgroundColor,
        tickCheckedTextColor = settings.tickCheckedTextColor,
        editable = settings.editable,
        hideIfEmpty = settings.hideIfEmpty && !settings.headerRow && !keepVisible,
    )

    private const val MAX_ROWS = 50
    private const val MAX_COLUMNS = 20
}
