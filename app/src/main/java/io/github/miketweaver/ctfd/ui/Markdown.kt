package io.github.miketweaver.ctfd.ui

import android.content.ClipData
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.text.HtmlCompat
import io.github.miketweaver.ctfd.data.CtfdApi
import kotlinx.coroutines.launch
import org.commonmark.ext.gfm.tables.TableBlock
import org.commonmark.ext.gfm.tables.TableCell
import org.commonmark.ext.gfm.tables.TableHead
import org.commonmark.ext.gfm.tables.TableRow
import org.commonmark.ext.gfm.tables.TablesExtension
import org.commonmark.node.BlockQuote
import org.commonmark.node.BulletList
import org.commonmark.node.Code
import org.commonmark.node.Emphasis
import org.commonmark.node.FencedCodeBlock
import org.commonmark.node.HardLineBreak
import org.commonmark.node.Heading
import org.commonmark.node.HtmlBlock
import org.commonmark.node.HtmlInline
import org.commonmark.node.Image
import org.commonmark.node.IndentedCodeBlock
import org.commonmark.node.Link
import org.commonmark.node.ListItem
import org.commonmark.node.Node
import org.commonmark.node.OrderedList
import org.commonmark.node.Paragraph
import org.commonmark.node.SoftLineBreak
import org.commonmark.node.StrongEmphasis
import org.commonmark.node.ThematicBreak
import org.commonmark.parser.Parser
import org.commonmark.node.Text as TextNode

private val parser = Parser.builder().extensions(listOf(TablesExtension.create())).build()

/** Reading text: Material's bodyLarge with tighter tracking, which suits long descriptions. */
@Composable
private fun prose() = MaterialTheme.typography.bodyLarge.copy(letterSpacing = 0.15.sp)

private fun Node.children(): List<Node> = generateSequence(firstChild) { it.next }.toList()

/**
 * Challenge descriptions are Markdown that may carry HTML, as organisers write them.
 * Rendered natively, block by block: code keeps its lines, lists keep their numbers, images
 * load from the CTF, and text the site hides with CSS stays hidden. No script or style runs.
 */
@Composable
fun MarkdownText(markdown: String, api: CtfdApi?, modifier: Modifier = Modifier) {
    val document = remember(markdown) { parser.parse(markdown) }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        document.children().forEach { Block(it, api) }
    }
}

@Composable
private fun Blocks(parent: Node, api: CtfdApi?, spacing: Int = 12) {
    Column(verticalArrangement = Arrangement.spacedBy(spacing.dp)) {
        parent.children().forEach { Block(it, api) }
    }
}

@Composable
private fun Block(node: Node, api: CtfdApi?) {
    when (node) {
        is Paragraph -> Paragraph(node, api)
        is Heading -> Text(
            inlines(node),
            style = when (node.level) {
                1 -> MaterialTheme.typography.headlineSmall
                2 -> MaterialTheme.typography.titleLarge
                else -> MaterialTheme.typography.titleMedium
            },
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(top = 8.dp),
        )
        is BulletList -> ListBlock(node, api) { "•" }
        is OrderedList -> {
            val start = node.markerStartNumber ?: 1
            ListBlock(node, api) { "${start + it}." }
        }
        is FencedCodeBlock -> CodeBlock(node.literal)
        is IndentedCodeBlock -> CodeBlock(node.literal)
        is BlockQuote -> Row(Modifier.height(IntrinsicSize.Min)) {
            Box(
                Modifier
                    .width(3.dp)
                    .fillMaxHeight()
                    .background(MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(2.dp))
            )
            Box(Modifier.padding(start = 12.dp)) {
                CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurfaceVariant) {
                    Blocks(node, api, spacing = 8)
                }
            }
        }
        is ThematicBreak -> HorizontalDivider()
        is HtmlBlock -> HtmlBlock(node.literal, api)
        is TableBlock -> Table(node)
        else -> Blocks(node, api)
    }
}

@Composable
private fun Paragraph(node: Paragraph, api: CtfdApi?) {
    val images = node.children().filterIsInstance<Image>()
    val text = inlines(node)
    // A paragraph that was all hidden text takes no space, as on the site.
    if (text.isBlank() && images.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (text.isNotBlank()) Text(text, style = prose())
        images.forEach { RemoteImage(it.destination, it.title ?: altText(it), api) }
    }
}

@Composable
private fun ListBlock(list: Node, api: CtfdApi?, marker: (Int) -> String) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        list.children().filterIsInstance<ListItem>().forEachIndexed { index, item ->
            Row {
                Text(
                    marker(index),
                    style = prose(),
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.widthIn(min = 20.dp).padding(end = 8.dp),
                )
                Blocks(item, api, spacing = 6)
            }
        }
    }
}

@Composable
private fun CodeBlock(code: String) {
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(8.dp)) {
        Box {
            // Code keeps its lines and scrolls sideways rather than wrapping.
            SelectionContainer(
                Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(start = 12.dp, top = 12.dp, bottom = 12.dp, end = 48.dp)
            ) {
                Text(code.trimEnd('\n'), fontFamily = Mono, style = MaterialTheme.typography.bodyMedium, softWrap = false)
            }
            IconButton(
                onClick = { scope.launch { clipboard.setClipEntry(ClipEntry(ClipData.newPlainText("code", code.trimEnd('\n')))) } },
                modifier = Modifier.align(Alignment.TopEnd),
            ) { Icon(Icons.Filled.ContentCopy, "Copy code") }
        }
    }
}

@Composable
private fun HtmlBlock(html: String, api: CtfdApi?) {
    val visible = remember(html) { stripHidden(html) }
    Regex("""<img\b[^>]*\bsrc\s*=\s*["']([^"']+)["'][^>]*>""", RegexOption.IGNORE_CASE).findAll(visible).forEach {
        val alt = Regex("""\balt\s*=\s*["']([^"']*)["']""").find(it.value)?.groupValues?.get(1).orEmpty()
        RemoteImage(it.groupValues[1], alt, api)
    }
    val text = remember(visible) { HtmlCompat.fromHtml(visible, HtmlCompat.FROM_HTML_MODE_COMPACT).toString().trim() }
    if (text.isNotEmpty()) Text(text, style = prose())
}

@Composable
private fun Table(table: TableBlock) {
    val rows = table.children().flatMap { section ->
        section.children().filterIsInstance<TableRow>().map { row -> (section is TableHead) to row }
    }
    val cells = rows.map { (_, row) -> row.children().filterIsInstance<TableCell>() }
    val columns = cells.maxOfOrNull { it.size } ?: 0
    // Columns sized by their longest cell, so they line up across rows.
    val widths = (0 until columns).map { col ->
        val longest = cells.maxOf { row -> row.getOrNull(col)?.let { plain(it).length } ?: 0 }
        (longest * 9 + 24).coerceIn(64, 280).dp
    }
    Box(Modifier.horizontalScroll(rememberScrollState())) {
        Column {
            rows.forEachIndexed { r, (head, _) ->
                Row(
                    if (head) Modifier.background(MaterialTheme.colorScheme.surfaceVariant) else Modifier
                ) {
                    cells[r].forEachIndexed { c, cell ->
                        Text(
                            inlines(cell),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = if (head) FontWeight.SemiBold else null,
                            modifier = Modifier.width(widths[c]).padding(8.dp),
                        )
                    }
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }
        }
    }
}

private fun plain(node: Node): String = buildString {
    fun walk(n: Node) {
        if (n is TextNode) append(n.literal) else if (n is Code) append(n.literal)
        n.children().forEach(::walk)
    }
    walk(node)
}

private fun altText(image: Image) = plain(image)

@Composable
private fun inlines(node: Node): AnnotatedString {
    val codeBackground = MaterialTheme.colorScheme.surfaceVariant
    val link = TextLinkStyles(SpanStyle(color = MaterialTheme.colorScheme.primary, textDecoration = TextDecoration.Underline))
    return buildAnnotatedString {
        // Inside an HTML element the site hides with CSS; its text is left out here too.
        var hiddenDepth = 0
        fun walk(n: Node) {
            when (n) {
                is HtmlInline -> {
                    val tag = n.literal
                    when {
                        tag.startsWith("</") -> if (hiddenDepth > 0) hiddenDepth--
                        isVoid(tag) -> if (hiddenDepth == 0 && tag.startsWith("<br", ignoreCase = true)) append("\n")
                        hiddenDepth > 0 -> hiddenDepth++
                        isHidden(tag) -> hiddenDepth = 1
                    }
                }
                else -> if (hiddenDepth == 0) when (n) {
                    is TextNode -> append(n.literal)
                    is SoftLineBreak -> append(" ")
                    is HardLineBreak -> append("\n")
                    is Code -> withStyle(SpanStyle(fontFamily = Mono, background = codeBackground, letterSpacing = 0.sp)) {
                        append(n.literal)
                    }
                    is Emphasis -> withStyle(SpanStyle(fontStyle = FontStyle.Italic)) { n.children().forEach(::walk) }
                    is StrongEmphasis -> withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { n.children().forEach(::walk) }
                    is Link -> withLink(LinkAnnotation.Url(n.destination, link)) { n.children().forEach(::walk) }
                    is Image -> Unit // shown below its paragraph
                    else -> n.children().forEach(::walk)
                }
            }
        }
        node.children().forEach(::walk)
    }
}

private val hiddenStyle = Regex(
    """display\s*:\s*none|visibility\s*:\s*hidden|opacity\s*:\s*0(\.0+)?\s*(;|"|'|$)|""" +
        """font-size\s*:\s*(0*\.\d+|0)\s*(px|pt|em|rem|%)?\s*(;|"|'|$)""",
    RegexOption.IGNORE_CASE,
)

private val voidTags = setOf("br", "img", "hr", "input", "wbr", "meta", "link", "source", "area", "col", "embed")

private fun isVoid(tag: String) = tag.endsWith("/>") ||
    Regex("""^<([a-zA-Z][a-zA-Z0-9]*)""").find(tag)?.groupValues?.get(1)?.lowercase() in voidTags

internal fun isHidden(tag: String) =
    hiddenStyle.containsMatchIn(tag) || Regex("""\shidden(\s|>|=)""", RegexOption.IGNORE_CASE).containsMatchIn(tag)

/** Drops elements the site hides with CSS from an HTML block, keeping the rest. */
internal fun stripHidden(html: String): String {
    var out = html
    repeat(5) {
        val open = Regex("""<([a-zA-Z][a-zA-Z0-9]*)\b[^>]*>""").findAll(out).firstOrNull { isHidden(it.value) } ?: return out
        val name = open.groupValues[1]
        val close = Regex("""</$name\s*>""", RegexOption.IGNORE_CASE).find(out, open.range.last + 1)
        out = out.removeRange(open.range.first, close?.range?.last?.plus(1) ?: out.length)
    }
    return out
}
