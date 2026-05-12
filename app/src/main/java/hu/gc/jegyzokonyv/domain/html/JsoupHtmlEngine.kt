package hu.gc.jegyzokonyv.domain.html

import hu.gc.jegyzokonyv.domain.model.TemplateBlock
import hu.gc.jegyzokonyv.domain.model.TemplateContent
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Entities
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class JsoupHtmlEngine @Inject constructor() : HtmlEngine {

    override fun renderTemplate(
        content: TemplateContent,
        title: String,
        todayIso: String,
    ): String {
        val doc = parse(wrapperHtml(title))
        val container = ensureContentContainer(doc)
        content.blocks.forEach { block ->
            when (block) {
                is TemplateBlock.Text -> {
                    val div = container.appendElement("div").addClass("text-block")
                    div.appendElement("p").text(block.text)
                }
                is TemplateBlock.Date -> {
                    container.appendElement("div").addClass("date-block").text(todayIso)
                }
            }
        }
        return render(doc)
    }

    override fun setTitle(html: String, title: String): String {
        val doc = parse(html)
        doc.selectFirst("h1")?.text(title)
        doc.selectFirst("title")?.text(title)
        return render(doc)
    }

    override fun appendPhotoBlock(html: String, relativeImagePath: String, caption: String?): String {
        val doc = parse(html)
        val content = ensureContentContainer(doc)
        val block = content.appendElement("div").addClass("photo-block")
        block.appendElement("img").attr("src", relativeImagePath)
        if (!caption.isNullOrBlank()) {
            block.appendElement("p").text(caption)
        }
        return render(doc)
    }

    override fun appendTextBlock(html: String, text: String): String {
        val doc = parse(html)
        val content = ensureContentContainer(doc)
        val block = content.appendElement("div").addClass("text-block")
        block.appendElement("p").text(text)
        return render(doc)
    }

    private fun parse(html: String): Document {
        val doc = Jsoup.parse(html)
        doc.outputSettings()
            .prettyPrint(false)
            .escapeMode(Entities.EscapeMode.xhtml)
            .charset("UTF-8")
        return doc
    }

    private fun ensureContentContainer(doc: Document) =
        doc.getElementById(CONTENT_ID)
            ?: doc.body().appendElement("div").attr("id", CONTENT_ID)

    private fun render(doc: Document): String = doc.outerHtml()

    private fun wrapperHtml(title: String): String {
        val safeTitle = title
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
        return """
            <!DOCTYPE html>
            <html lang="hu">
            <head>
            <meta charset="utf-8" />
            <title>$safeTitle</title>
            <style>
              * { box-sizing: border-box; }
              body {
                font-family: -apple-system, "Roboto", sans-serif;
                padding: 24px;
                margin: 0;
                background-color: #ffffff;
                color: #1a1a1a;
                line-height: 1.45;
                font-size: 14px;
              }
              h1 {
                font-size: 22px;
                margin: 0 0 12px;
                border-bottom: 2px solid #2e5266;
                padding-bottom: 6px;
              }
              #content { margin-top: 8px; }
              .photo-block {
                margin: 14px 0;
                page-break-inside: avoid;
              }
              .photo-block img {
                width: 100%;
                max-width: 100%;
                height: auto;
                border-radius: 6px;
                display: block;
              }
              .photo-block p {
                margin: 6px 0 0;
                font-size: 13px;
                color: #333;
                font-style: italic;
              }
              .text-block {
                margin: 12px 0;
                white-space: pre-wrap;
              }
              .text-block p {
                margin: 0;
              }
              .date-block {
                margin: 12px 0;
                font-weight: 600;
              }
            </style>
            </head>
            <body>
            <h1>$safeTitle</h1>
            <div id="content"></div>
            </body>
            </html>
        """.trimIndent()
    }

    private companion object {
        const val CONTENT_ID = "content"
    }
}
