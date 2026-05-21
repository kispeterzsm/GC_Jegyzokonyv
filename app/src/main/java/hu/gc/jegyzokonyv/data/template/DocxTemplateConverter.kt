package hu.gc.jegyzokonyv.data.template

import hu.gc.jegyzokonyv.domain.model.TableAxisSettings
import hu.gc.jegyzokonyv.domain.model.TemplateBlock
import hu.gc.jegyzokonyv.domain.model.TemplateContent
import org.w3c.dom.Document
import org.w3c.dom.Element
import org.w3c.dom.Node
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.util.UUID
import java.util.zip.ZipInputStream
import javax.inject.Inject
import javax.xml.parsers.DocumentBuilderFactory

class DocxTemplateConverter @Inject constructor() {

    fun convert(input: InputStream, title: String): TemplateContent {
        val entries = readDocxEntries(input)
        val documentXml = entries[DOCUMENT_XML] ?: error("A DOCX fájl nem tartalmaz dokumentumot.")

        val blocks = buildList {
            val headerBlocks = entries
                .filterKeys { it.startsWith("word/header") && it.endsWith(".xml") }
                .toSortedMap()
                .values
                .flatMap { parseDocumentPart(it) }
            if (headerBlocks.isNotEmpty()) add(TemplateBlock.Header(id = newId(), blocks = headerBlocks))

            addAll(parseDocumentPart(documentXml))

            val footerBlocks = entries
                .filterKeys { it.startsWith("word/footer") && it.endsWith(".xml") }
                .toSortedMap()
                .values
                .flatMap { parseDocumentPart(it) }
            if (footerBlocks.isNotEmpty()) add(TemplateBlock.Footer(id = newId(), blocks = footerBlocks))

            if (none { it is TemplateBlock.Images }) {
                add(TemplateBlock.Images(id = "template-images"))
            }
        }

        return TemplateContent(
            title = title,
            blocks = blocks.ifEmpty {
                listOf(
                    TemplateBlock.Text(id = newId(), text = title),
                    TemplateBlock.Images(id = "template-images"),
                )
            },
        )
    }

    private fun readDocxEntries(input: InputStream): Map<String, ByteArray> {
        val entries = mutableMapOf<String, ByteArray>()
        ZipInputStream(input.buffered()).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                if (!entry.isDirectory && entry.name.startsWith("word/") && entry.name.endsWith(".xml")) {
                    entries[entry.name] = zip.readBytes()
                }
                zip.closeEntry()
            }
        }
        return entries
    }

    private fun parseDocumentPart(xml: ByteArray): List<TemplateBlock> {
        val doc = parseXml(xml)
        val body = firstDescendant(doc.documentElement, "body") ?: doc.documentElement
        return buildList {
            body.childElements().forEach { element ->
                when (element.local()) {
                    "p" -> addAll(parseParagraph(element))
                    "tbl" -> parseTable(element)?.let(::add)
                }
            }
        }
    }

    private fun parseParagraph(paragraph: Element): List<TemplateBlock> {
        val text = paragraphText(paragraph).trimLines()
        val hasPageBreak = paragraph.hasDescendant("br") { it.attr("type") == "page" } ||
            paragraph.hasDescendant("lastRenderedPageBreak")
        val hasImage = paragraph.hasDescendant("drawing") || paragraph.hasDescendant("pict")
        val prefix = if (paragraph.hasDescendant("numPr")) "• " else ""

        return buildList {
            val fullText = buildString {
                append(prefix)
                append(text)
                if (hasImage) {
                    if (isNotBlank()) append('\n')
                    append("[Kép a DOCX dokumentumból]")
                }
            }.trim()
            if (fullText.isNotBlank()) add(TemplateBlock.Text(id = newId(), text = fullText))
            if (hasPageBreak) add(TemplateBlock.PageBreak(id = newId()))
        }
    }

    private fun parseTable(table: Element): TemplateBlock.Table? {
        val rows = table.childElements("tr").map { row ->
            row.childElements("tc").map { cell ->
                cell.childElements("p")
                    .map { paragraphText(it).trimLines() }
                    .filter { it.isNotBlank() }
                    .joinToString("\n")
                    .ifBlank { descendantText(cell).trimLines() }
            }
        }.filter { it.isNotEmpty() }

        if (rows.isEmpty()) return null

        val rowCount = rows.size.coerceIn(1, 50)
        val columnCount = rows.maxOf { it.size }.coerceIn(1, 20)
        val normalized = rows.take(rowCount).map { row ->
            row.take(columnCount) + List((columnCount - row.size).coerceAtLeast(0)) { "" }
        }
        val firstRowLooksLikeHeader = normalized.firstOrNull()?.all { it.isNotBlank() } == true
        val rowSettings = if (firstRowLooksLikeHeader) {
            listOf(TableAxisSettings(headerRow = true))
        } else {
            emptyList()
        }

        return TemplateBlock.Table(
            id = newId(),
            rows = rowCount,
            columns = columnCount,
            hasHeaderColumn = false,
            cells = normalized,
            rowSettings = rowSettings,
        )
    }

    private fun paragraphText(paragraph: Element): String = buildString {
        fun visit(node: Node) {
            node.childNodesSeq().forEach { child ->
                when (child.local()) {
                    "t" -> append(child.textContent)
                    "tab" -> append('\t')
                    "br", "cr" -> if (child.attr("type") != "page") append('\n')
                    else -> visit(child)
                }
            }
        }
        visit(paragraph)
    }

    private fun descendantText(element: Element): String = buildString {
        fun visit(node: Node) {
            node.childNodesSeq().forEach { child ->
                if (child.local() == "t") append(child.textContent) else visit(child)
            }
        }
        visit(element)
    }

    private fun parseXml(xml: ByteArray): Document {
        val factory = DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = true
            runCatching { setFeature("http://apache.org/xml/features/disallow-doctype-decl", true) }
            runCatching { setFeature("http://xml.org/sax/features/external-general-entities", false) }
            runCatching { setFeature("http://xml.org/sax/features/external-parameter-entities", false) }
        }
        return factory.newDocumentBuilder().parse(ByteArrayInputStream(xml))
    }

    private fun firstDescendant(element: Element, localName: String): Element? {
        if (element.local() == localName) return element
        element.childElements().forEach { child ->
            firstDescendant(child, localName)?.let { return it }
        }
        return null
    }

    private fun Element.hasDescendant(localName: String, predicate: (Element) -> Boolean = { true }): Boolean {
        if (local() == localName && predicate(this)) return true
        return childElements().any { it.hasDescendant(localName, predicate) }
    }

    private fun Element.childElements(localName: String? = null): List<Element> = childNodesSeq()
        .filterIsInstance<Element>()
        .filter { localName == null || it.local() == localName }
        .toList()

    private fun Node.childNodesSeq(): Sequence<Node> = sequence {
        val nodes = childNodes
        for (index in 0 until nodes.length) yield(nodes.item(index))
    }

    private fun Node.local(): String = localName ?: nodeName.substringAfter(':')

    private fun Node.attr(localName: String): String = if (this is Element) {
        getAttributeNS(WORD_NAMESPACE, localName).ifBlank { getAttribute("w:$localName") }.ifBlank { getAttribute(localName) }
    } else {
        ""
    }

    private fun String.trimLines(): String = lines().joinToString("\n") { it.trim() }.trim()

    private fun newId(): String = UUID.randomUUID().toString()

    private companion object {
        const val DOCUMENT_XML = "word/document.xml"
        const val WORD_NAMESPACE = "http://schemas.openxmlformats.org/wordprocessingml/2006/main"
    }
}
