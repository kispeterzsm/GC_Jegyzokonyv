package hu.gc.jegyzokonyv.domain.html

interface HtmlEngine {
    fun instantiateTemplate(templateHtml: String, title: String): String
    fun setTitle(html: String, title: String): String
    fun appendPhotoBlock(html: String, relativeImagePath: String, caption: String?): String
    fun appendTextBlock(html: String, text: String): String
}
