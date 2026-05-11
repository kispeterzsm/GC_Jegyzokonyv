package hu.gc.jegyzokonyv.domain.html

import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Entities
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class JsoupHtmlEngine @Inject constructor() : HtmlEngine {

    override fun instantiateTemplate(templateHtml: String, title: String): String {
        val withTitle = templateHtml.replace(PLACEHOLDER_TITLE, escapePlaceholder(title))
        val doc = parse(withTitle)
        ensureContentContainer(doc)
        return render(doc)
    }

    override fun setTitle(html: String, title: String): String {
        val doc = parse(html)
        val h1 = doc.selectFirst("h1")
        if (h1 != null) {
            h1.text(title)
        }
        val head = doc.selectFirst("title")
        head?.text(title)
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

    private fun escapePlaceholder(value: String): String =
        value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")

    private companion object {
        const val CONTENT_ID = "content"
        const val PLACEHOLDER_TITLE = "{{title}}"
    }
}
