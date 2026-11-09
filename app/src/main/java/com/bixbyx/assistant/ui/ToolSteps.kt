package com.bixbyx.assistant.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bixbyx.assistant.agent.Step
import com.bixbyx.assistant.agent.StepKind
import com.bixbyx.assistant.agent.StepState
import top.yukonga.miuix.kmp.basic.CircularProgressIndicator
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.ChevronForward
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** Small round badge: ">_" for a command, a brain-like mark for a thought. */
@Composable
private fun Glyph(kind: StepKind, size: Int = 28, modifier: Modifier = Modifier) {
    val c = MiuixTheme.colorScheme
    Box(modifier.size(size.dp).clip(CircleShape).background(c.background), Alignment.Center) {
        Box(Modifier.fillMaxSize(0.92f).clip(CircleShape).background(c.onSurface.copy(alpha = .10f)), Alignment.Center) {
            if (kind == StepKind.Ran) Text(">_", fontFamily = FontFamily.Monospace, fontSize = (size * .38f).sp, color = c.onSurface.copy(alpha = .8f))
            else {
                val line = c.onSurface.copy(alpha = .8f)
                Canvas(Modifier.size((size * .5f).dp)) {
                    val sw = 1.4.dp.toPx()
                    drawCircle(line, this.size.minDimension / 2 - sw, style = Stroke(sw))
                    drawLine(line, Offset(this.size.width / 2, sw * 2), Offset(this.size.width / 2, this.size.height - sw * 2), sw, StrokeCap.Round)
                    drawArc(line, 100f, 160f, false, Offset(this.size.width * .18f, this.size.height * .22f), Size(this.size.width * .34f, this.size.height * .56f), style = Stroke(sw))
                    drawArc(line, -80f, 160f, false, Offset(this.size.width * .48f, this.size.height * .22f), Size(this.size.width * .34f, this.size.height * .56f), style = Stroke(sw))
                }
            }
        }
    }
}

@Composable
private fun Chevron(open: Boolean) {
    val angle by animateFloatAsState(if (open) 90f else 0f, spring(dampingRatio = .7f), label = "chev")
    Icon(MiuixIcons.ChevronForward, null, Modifier.size(18.dp).rotate(angle), tint = MiuixTheme.colorScheme.onSurfaceVariantSummary)
}

@Composable
private fun StepRow(s: Step) {
    val c = MiuixTheme.colorScheme
    var shown by remember { mutableStateOf(false) }
    var open by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { shown = true }
    AnimatedVisibility(shown, enter = fadeIn() + expandVertically() + slideInVertically { it / 3 }) {
        Column(Modifier.padding(vertical = 3.dp)) {
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { open = !open }.padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                Glyph(s.kind)
                Spacer(Modifier.width(10.dp))
                if (s.kind == StepKind.Ran) {
                    Text("Ran", fontWeight = FontWeight.SemiBold, color = c.onSurface, style = MiuixTheme.textStyles.body1)
                    Spacer(Modifier.width(5.dp))
                    Text(s.text, Modifier.weight(1f, false), maxLines = 1, overflow = TextOverflow.Ellipsis, style = MiuixTheme.textStyles.body1,
                        color = if (s.state == StepState.Failed) c.error else c.onSurfaceVariantSummary)
                } else {
                    Text("Thought", fontWeight = FontWeight.SemiBold, color = c.onSurface, style = MiuixTheme.textStyles.body1)
                }
                Spacer(Modifier.width(4.dp))
                if (s.state == StepState.Running) CircularProgressIndicator(size = 14.dp, strokeWidth = 2.dp) else Chevron(open)
            }
            AnimatedVisibility(open, enter = fadeIn() + expandVertically(), exit = fadeOut() + shrinkVertically()) {
                if (s.kind == StepKind.Thought) {
                    Text(s.text, Modifier.padding(start = 38.dp, top = 4.dp, bottom = 4.dp), color = c.onSurfaceVariantSummary, style = MiuixTheme.textStyles.body2)
                } else {
                    Box(Modifier.padding(start = 38.dp, top = 6.dp).fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(c.onSurface.copy(alpha = .07f))
                        .heightIn(max = 180.dp).verticalScroll(rememberScrollState()).padding(10.dp)) {
                        Text(buildString { append("$ ").append(s.input); if (s.output.isNotBlank()) append("\n\n").append(s.output) },
                            fontFamily = FontFamily.Monospace, fontSize = 12.sp, color = c.onSurface.copy(alpha = .85f))
                    }
                }
            }
        }
    }
}

/**
 * "11 tools used": overlapping badges, then every thought and command as an expandable row.
 * Open while Bixby works; collapsed once it has answered.
 */
@Composable
fun ToolSteps(steps: List<Step>, running: Boolean, defaultOpen: Boolean, modifier: Modifier = Modifier) {
    if (steps.isEmpty()) return
    val c = MiuixTheme.colorScheme
    var open by remember(defaultOpen) { mutableStateOf(defaultOpen) }
    LaunchedEffect(running) { if (running) open = true }
    val recent = steps.takeLast(5)
    Column(modifier.padding(horizontal = 12.dp).padding(bottom = 4.dp).animateContentSize(spring(dampingRatio = .85f, stiffness = 400f))) {
        Row(Modifier.clip(RoundedCornerShape(14.dp)).clickable { open = !open }.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.width((22 * recent.size + 10 + if (steps.size > 5) 12 else 0).dp).height(28.dp)) {
                var x = 0
                if (steps.size > 5) {
                    Box(Modifier.size(28.dp).clip(CircleShape).background(c.background), Alignment.Center) {
                        Box(Modifier.fillMaxSize(0.92f).clip(CircleShape).background(c.onSurface.copy(alpha = .10f)), Alignment.Center) {
                            Text("+${steps.size - 5}", fontSize = 10.sp, color = c.onSurfaceVariantSummary)
                        }
                    }
                    x = 22
                }
                recent.forEachIndexed { i, s -> Glyph(s.kind, 28, Modifier.offset(x = (x + i * 22).dp)) }
            }
            Spacer(Modifier.width(8.dp))
            Text("${steps.size}", color = c.onSurface, style = MiuixTheme.textStyles.body1)
            Text(" tools used", color = c.onSurfaceVariantSummary, style = MiuixTheme.textStyles.body1)
            Spacer(Modifier.width(4.dp))
            if (running) CircularProgressIndicator(size = 14.dp, strokeWidth = 2.dp) else Chevron(open)
        }
        AnimatedVisibility(open, enter = fadeIn() + expandVertically(), exit = fadeOut() + shrinkVertically()) {
            Column(Modifier.padding(top = 2.dp)) { steps.forEach { key(it.n) { StepRow(it) } } }
        }
    }
}
