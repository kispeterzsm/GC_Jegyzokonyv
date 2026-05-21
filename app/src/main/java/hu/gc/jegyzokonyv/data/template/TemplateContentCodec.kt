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
        val blocksArray = JSONArray(content.blocks.map { it.toJson() })
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
        val blocks = blocksJson.toBlocks()
        return TemplateContent(title = title, kind = kind, blocks = blocks)
    }

    private fun TemplateBlock.toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        when (val block = this@toJson) {
            is TemplateBlock.Text -> {
                put("type", "text")
                put("text", block.text)
                put("settings", block.settings.toJson())
            }
            is TemplateBlock.Date -> {
                put("type", "date")
                put("settings", block.settings.toJson())
            }
            is TemplateBlock.Table -> {
                put("type", "table")
                put("rows", block.rows)
                put("columns", block.columns)
                put("hasHeaderColumn", block.hasHeaderColumn)
                put("cells", JSONArray(block.cells.map { row -> JSONArray(row) }))
                put("rowSettings", JSONArray(block.rowSettings.map { it.toJson() }))
                put("columnSettings", JSONArray(block.columnSettings.map { it.toJson() }))
                put("cellSettings", JSONArray(block.cellSettings.map { row -> JSONArray(row.map { it.toJson() }) }))
            }
            is TemplateBlock.Signature -> put("type", "signature")
            is TemplateBlock.Stamp -> put("type", "stamp")
            is TemplateBlock.ProfileData -> {
                put("type", "profile_data")
                put("field", block.field.jsonValue)
            }
            is TemplateBlock.Images -> {
                put("type", "images")
                put("blocks", JSONArray(block.blocks.map { it.toJson() }))
            }
            is TemplateBlock.Image -> put("type", "image")
            is TemplateBlock.PageBreak -> put("type", "page_break")
            is TemplateBlock.PageNumber -> {
                put("type", "page_number")
                put("settings", block.settings.toJson())
            }
            is TemplateBlock.Header -> {
                put("type", "header")
                put("blocks", JSONArray(block.blocks.map { it.toJson() }))
            }
            is TemplateBlock.Footer -> {
                put("type", "footer")
                put("blocks", JSONArray(block.blocks.map { it.toJson() }))
            }
            is TemplateBlock.Html -> {
                put("type", "html")
                put("html", block.html)
            }
        }
    }

    private fun JSONArray.toBlocks(): List<TemplateBlock> = buildList {
        for (i in 0 until length()) {
            val obj = optJSONObject(i) ?: continue
            val id = obj.optString("id").ifBlank { UUID.randomUUID().toString() }
            when (obj.optString("type")) {
                "text" -> add(TemplateBlock.Text(id = id, text = obj.optString("text", ""), settings = obj.optJSONObject("settings").toCellSettings()))
                "date" -> add(TemplateBlock.Date(id = id, settings = obj.optJSONObject("settings").toCellSettings()))
                "table" -> add(obj.toTableBlock(id))
                "signature" -> add(TemplateBlock.Signature(id = id))
                "stamp" -> add(TemplateBlock.Stamp(id = id))
                "profile_data" -> add(TemplateBlock.ProfileData(id = id, field = hu.gc.jegyzokonyv.domain.model.ProfileDataField.fromJson(obj.optString("field"))))
                "images" -> add(TemplateBlock.Images(id = id, blocks = (obj.optJSONArray("blocks") ?: JSONArray().put(JSONObject().put("type", "image").put("id", "image-component"))).toBlocks()))
                "image" -> add(TemplateBlock.Image(id = id))
                "page_break" -> add(TemplateBlock.PageBreak(id = id))
                "page_number" -> add(TemplateBlock.PageNumber(id = id, settings = obj.optJSONObject("settings").toCellSettings()))
                "header" -> add(TemplateBlock.Header(id = id, blocks = (obj.optJSONArray("blocks") ?: JSONArray()).toBlocks()))
                "footer" -> add(TemplateBlock.Footer(id = id, blocks = (obj.optJSONArray("blocks") ?: JSONArray()).toBlocks()))
                "html" -> add(TemplateBlock.Html(id = id, html = obj.optString("html", "")))
            }
        }
    }

    private fun JSONObject.toTableBlock(id: String) = TemplateBlock.Table(
        id = id,
        rows = optInt("rows", 2).coerceIn(1, 50),
        columns = optInt("columns", 2).coerceIn(1, 20),
        hasHeaderColumn = optBoolean("hasHeaderColumn", false),
        cells = optJSONArray("cells")?.let { cellsJson ->
            buildList {
                for (rowIndex in 0 until cellsJson.length()) {
                    val rowJson = cellsJson.optJSONArray(rowIndex) ?: JSONArray()
                    add(buildList {
                        for (colIndex in 0 until rowJson.length()) add(rowJson.optString(colIndex, ""))
                    })
                }
            }
        } ?: emptyList(),
        rowSettings = optJSONArray("rowSettings")?.let { settingsJson ->
            buildList {
                for (index in 0 until settingsJson.length()) add(settingsJson.optJSONObject(index).toAxisSettings())
            }
        } ?: emptyList(),
        columnSettings = optJSONArray("columnSettings")?.let { settingsJson ->
            buildList {
                for (index in 0 until settingsJson.length()) add(settingsJson.optJSONObject(index).toAxisSettings())
            }
        } ?: emptyList(),
        cellSettings = optJSONArray("cellSettings")?.let { settingsJson ->
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
