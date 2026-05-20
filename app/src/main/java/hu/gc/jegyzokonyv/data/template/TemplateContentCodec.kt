package hu.gc.jegyzokonyv.data.template

import hu.gc.jegyzokonyv.domain.model.TableAxisSettings
import hu.gc.jegyzokonyv.domain.model.TableCellSettings
import hu.gc.jegyzokonyv.domain.model.TemplateBlock
import hu.gc.jegyzokonyv.domain.model.TemplateContent
import hu.gc.jegyzokonyv.domain.model.TemplateKind
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

object TemplateContentCodec {

    fun encode(content: TemplateContent): String {
        val blocksArray = JSONArray()
        content.blocks.forEach { block ->
            val obj = JSONObject()
            obj.put("id", block.id)
            when (block) {
                is TemplateBlock.Text -> {
                    obj.put("type", "text")
                    obj.put("text", block.text)
                }
                is TemplateBlock.Date -> {
                    obj.put("type", "date")
                }
                is TemplateBlock.Table -> {
                    obj.put("type", "table")
                    obj.put("rows", block.rows)
                    obj.put("columns", block.columns)
                    obj.put("hasHeaderColumn", block.hasHeaderColumn)
                    obj.put("cells", JSONArray(block.cells.map { row -> JSONArray(row) }))
                    obj.put("rowSettings", JSONArray(block.rowSettings.map { it.toJson() }))
                    obj.put("columnSettings", JSONArray(block.columnSettings.map { it.toJson() }))
                    obj.put("cellSettings", JSONArray(block.cellSettings.map { row -> JSONArray(row.map { it.toJson() }) }))
                }
                is TemplateBlock.Signature -> {
                    obj.put("type", "signature")
                }
                is TemplateBlock.Stamp -> {
                    obj.put("type", "stamp")
                }
                is TemplateBlock.Images -> {
                    obj.put("type", "images")
                }
                is TemplateBlock.PageBreak -> {
                    obj.put("type", "page_break")
                }
                is TemplateBlock.Html -> {
                    obj.put("type", "html")
                    obj.put("html", block.html)
                }
            }
            blocksArray.put(obj)
        }
        val root = JSONObject()
            .put("title", content.title)
            .put("kind", content.kind.jsonValue)
            .put("blocks", blocksArray)
            .put("version", 1)
        return root.toString(2)
    }

    fun decode(json: String): TemplateContent {
        val root = JSONObject(json)
        val title = root.optString("title", "")
        val kind = TemplateKind.fromJson(root.optString("kind", TemplateKind.Standard.jsonValue))
        val blocksJson = root.optJSONArray("blocks") ?: JSONArray()
        val blocks = buildList {
            for (i in 0 until blocksJson.length()) {
                val obj = blocksJson.optJSONObject(i) ?: continue
                val id = obj.optString("id").ifBlank { UUID.randomUUID().toString() }
                when (obj.optString("type")) {
                    "text" -> add(TemplateBlock.Text(id = id, text = obj.optString("text", "")))
                    "date" -> add(TemplateBlock.Date(id = id))
                    "table" -> add(
                        TemplateBlock.Table(
                            id = id,
                            rows = obj.optInt("rows", 2).coerceIn(1, 50),
                            columns = obj.optInt("columns", 2).coerceIn(1, 20),
                            hasHeaderColumn = obj.optBoolean("hasHeaderColumn", false),
                            cells = obj.optJSONArray("cells")?.let { cellsJson ->
                                buildList {
                                    for (rowIndex in 0 until cellsJson.length()) {
                                        val rowJson = cellsJson.optJSONArray(rowIndex) ?: JSONArray()
                                        add(buildList {
                                            for (colIndex in 0 until rowJson.length()) add(rowJson.optString(colIndex, ""))
                                        })
                                    }
                                }
                            } ?: emptyList(),
                            rowSettings = obj.optJSONArray("rowSettings")?.let { settingsJson ->
                                buildList {
                                    for (index in 0 until settingsJson.length()) add(settingsJson.optJSONObject(index).toAxisSettings())
                                }
                            } ?: emptyList(),
                            columnSettings = obj.optJSONArray("columnSettings")?.let { settingsJson ->
                                buildList {
                                    for (index in 0 until settingsJson.length()) add(settingsJson.optJSONObject(index).toAxisSettings())
                                }
                            } ?: emptyList(),
                            cellSettings = obj.optJSONArray("cellSettings")?.let { settingsJson ->
                                buildList {
                                    for (rowIndex in 0 until settingsJson.length()) {
                                        val rowJson = settingsJson.optJSONArray(rowIndex) ?: JSONArray()
                                        add(buildList {
                                            for (colIndex in 0 until rowJson.length()) add(rowJson.optJSONObject(colIndex).toCellSettings())
                                        })
                                    }
                                }
                            } ?: emptyList(),
                        )
                    )
                    "signature" -> add(TemplateBlock.Signature(id = id))
                    "stamp" -> add(TemplateBlock.Stamp(id = id))
                    "images" -> add(TemplateBlock.Images(id = id))
                    "page_break" -> add(TemplateBlock.PageBreak(id = id))
                    "html" -> add(TemplateBlock.Html(id = id, html = obj.optString("html", "")))
                }
            }
        }
        return TemplateContent(title = title, kind = kind, blocks = blocks)
    }

    private fun TableAxisSettings.toJson() = JSONObject().apply {
        put("backgroundColor", backgroundColor)
        put("textColor", textColor)
        put("textAlign", textAlign)
        put("tickXBackgroundColor", tickXBackgroundColor)
        put("tickXTextColor", tickXTextColor)
        put("tickCheckedBackgroundColor", tickCheckedBackgroundColor)
        put("tickCheckedTextColor", tickCheckedTextColor)
        editable?.let { put("editable", it) }
        put("hideIfEmpty", hideIfEmpty)
        put("mergeAll", mergeAll)
    }

    private fun TableCellSettings.toJson() = JSONObject().apply {
        put("backgroundColor", backgroundColor)
        put("textColor", textColor)
        put("textAlign", textAlign)
        put("tickXBackgroundColor", tickXBackgroundColor)
        put("tickXTextColor", tickXTextColor)
        put("tickCheckedBackgroundColor", tickCheckedBackgroundColor)
        put("tickCheckedTextColor", tickCheckedTextColor)
        editable?.let { put("editable", it) }
        put("hideIfEmpty", hideIfEmpty)
        put("toggleCheck", toggleCheck)
        put("mergeRight", mergeRight)
    }

    private fun JSONObject?.toAxisSettings(): TableAxisSettings {
        if (this == null) return TableAxisSettings()
        return TableAxisSettings(
            backgroundColor = optString("backgroundColor", ""),
            textColor = optString("textColor", ""),
            textAlign = optString("textAlign", "left"),
            tickXBackgroundColor = optString("tickXBackgroundColor", ""),
            tickXTextColor = optString("tickXTextColor", ""),
            tickCheckedBackgroundColor = optString("tickCheckedBackgroundColor", ""),
            tickCheckedTextColor = optString("tickCheckedTextColor", ""),
            editable = if (has("editable")) optBoolean("editable") else null,
            hideIfEmpty = optBoolean("hideIfEmpty", false),
            mergeAll = optBoolean("mergeAll", false),
        )
    }

    private fun JSONObject?.toCellSettings(): TableCellSettings {
        if (this == null) return TableCellSettings()
        return TableCellSettings(
            backgroundColor = optString("backgroundColor", ""),
            textColor = optString("textColor", ""),
            textAlign = optString("textAlign", "left"),
            tickXBackgroundColor = optString("tickXBackgroundColor", ""),
            tickXTextColor = optString("tickXTextColor", ""),
            tickCheckedBackgroundColor = optString("tickCheckedBackgroundColor", ""),
            tickCheckedTextColor = optString("tickCheckedTextColor", ""),
            editable = if (has("editable")) optBoolean("editable") else null,
            hideIfEmpty = optBoolean("hideIfEmpty", false),
            toggleCheck = optBoolean("toggleCheck", false),
            mergeRight = optBoolean("mergeRight", false),
        )
    }
}
