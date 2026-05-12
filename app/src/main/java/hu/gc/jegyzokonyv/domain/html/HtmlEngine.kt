package hu.gc.jegyzokonyv.domain.html

import hu.gc.jegyzokonyv.domain.model.TemplateContent

interface HtmlEngine {
    fun renderTemplate(content: TemplateContent, title: String, todayIso: String): String
    fun setTitle(html: String, title: String): String
    fun appendPhotoBlock(html: String, relativeImagePath: String, caption: String?): String
    fun appendTextBlock(html: String, text: String): String
}
