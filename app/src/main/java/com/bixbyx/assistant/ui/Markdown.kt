package com.bixbyx.assistant.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

private sealed interface Md {
    data class Code(val text: String) : Md
    data class Line(val text: String, val kind: Int) : Md   // 0 plain, 1 bullet, 2 heading
}

private val bullet = Regex("^\\s*[-*\u2022]\\s+")

private fun parse(src: String): List<Md> {
    val out = ArrayList<Md>()
    val code = StringBuilder()
    var inCode = false
    for (raw in src.lines()) {
        if (raw.trimStart().startsWith("```")) {
            if (inCode) { out += Md.Code(code.toString().trimEnd()); code.clear() }
            inCode = !inCode
            continue
        }
        if (inCode) { code.appendLine(raw); continue }
        val l = raw.trimEnd()
        when {
            l.isBlank() -> {}
            l.trimStart().startsWith("#") -> out += Md.Line(l.trimStart().trimStart('#').trim(), 2)
            bullet.containsMatchIn(l) -> out += Md.Line(l.replace(bullet, ""), 1)
            else -> out += Md.Line(l, 0)
        }
    }
    if (inCode && code.isNotEmpty()) out += Md.Code(code.toString().trimEnd())
    return out
}

/** **bold** and `code` inside a line. */
private fun inline(s: String, codeBg: Color): AnnotatedString = buildAnnotatedString {
    var i = 0
    while (i < s.length) {
        if (s.startsWith("**", i)) {
            val e = s.indexOf("**", i + 2)
            if (e > 0) { withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(s.substring(i + 2, e)) }; i = e + 2; continue }
        }
        if (s[i] == '`') {
            val e = s.indexOf('`', i + 1)
            if (e > 0) { withStyle(SpanStyle(fontFamily = FontFamily.Monospace, background = codeBg)) { append(s.substring(i + 1, e)) }; i = e + 1; continue }
        }
        append(s[i]); i++
    }
}

/** Renders the common markdown a model replies with: headings, bullets, bold, inline code and code blocks. */
@Composable
fun Markdown(text: String, modifier: Modifier = Modifier) {
    val c = MiuixTheme.colorScheme
    val blocks = remember(text) { parse(text) }
    val codeBg = c.onSurface.copy(alpha = .12f)
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        blocks.forEach { b ->
            when (b) {
                is Md.Code -> Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(c.onSurface.copy(alpha = .07f)).padding(10.dp)) {
                    Text(b.text, fontFamily = FontFamily.Monospace, fontSize = 12.sp)
                }
                is Md.Line -> when (b.kind) {
                    2 -> Text(inline(b.text, codeBg), fontWeight = FontWeight.SemiBold, style = MiuixTheme.textStyles.title4)
                    1 -> Row {
                        Text("\u2022", Modifier.padding(end = 8.dp), style = MiuixTheme.textStyles.body1)
                        Text(inline(b.text, codeBg), Modifier.weight(1f), style = MiuixTheme.textStyles.body1)
                    }
                    else -> Text(inline(b.text, codeBg), style = MiuixTheme.textStyles.body1)
                }
            }
        }
    }
}
