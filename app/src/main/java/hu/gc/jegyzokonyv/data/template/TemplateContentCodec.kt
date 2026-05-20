package hu.gc.jegyzokonyv.data.template

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
                        )
                    )
                    "signature" -> add(TemplateBlock.Signature(id = id))
                    "stamp" -> add(TemplateBlock.Stamp(id = id))
                    "images" -> add(TemplateBlock.Images(id = id))
                    "page_break" -> add(TemplateBlock.PageBreak(id = id))
                }
            }
        }
        return TemplateContent(title = title, kind = kind, blocks = blocks)
    }
}
