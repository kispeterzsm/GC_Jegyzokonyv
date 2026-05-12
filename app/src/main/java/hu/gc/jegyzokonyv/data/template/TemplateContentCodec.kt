package hu.gc.jegyzokonyv.data.template

import hu.gc.jegyzokonyv.domain.model.TemplateBlock
import hu.gc.jegyzokonyv.domain.model.TemplateContent
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
            }
            blocksArray.put(obj)
        }
        val root = JSONObject()
            .put("title", content.title)
            .put("blocks", blocksArray)
            .put("version", 1)
        return root.toString(2)
    }

    fun decode(json: String): TemplateContent {
        val root = JSONObject(json)
        val title = root.optString("title", "")
        val blocksJson = root.optJSONArray("blocks") ?: JSONArray()
        val blocks = buildList {
            for (i in 0 until blocksJson.length()) {
                val obj = blocksJson.optJSONObject(i) ?: continue
                val id = obj.optString("id").ifBlank { UUID.randomUUID().toString() }
                when (obj.optString("type")) {
                    "text" -> add(TemplateBlock.Text(id = id, text = obj.optString("text", "")))
                    "date" -> add(TemplateBlock.Date(id = id))
                }
            }
        }
        return TemplateContent(title = title, blocks = blocks)
    }
}
