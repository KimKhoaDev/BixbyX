package com.bixbyx.assistant.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.bixbyx.assistant.agent.BixbyEngine
import com.bixbyx.assistant.agent.Diagnostics
import com.bixbyx.assistant.agent.Skills
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.*
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Add
import top.yukonga.miuix.kmp.icon.extended.Mic
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.OverlayDropdownPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme

private enum class Tool { Diagnostics, Interpreter, Skills }

@Composable
fun ToolsScreen(padding: PaddingValues) {
    var sub by remember { mutableStateOf<Tool?>(null) }
    BackHandler(sub != null) { sub = null }
    AnimatedContent(sub, transitionSpec = {
        if (targetState != null) (slideInHorizontally(spring(stiffness = 400f)) { it / 3 } + fadeIn()) togetherWith (slideOutHorizontally { -it / 5 } + fadeOut())
        else (slideInHorizontally { -it / 5 } + fadeIn()) togetherWith (slideOutHorizontally { it / 3 } + fadeOut())
    }, label = "tools") { t ->
        when (t) {
            Tool.Diagnostics -> DiagnosticsScreen(padding) { sub = null }
            Tool.Interpreter -> InterpreterScreen(padding) { sub = null }
            Tool.Skills -> SkillsScreen(padding) { sub = null }
            null -> ToolsHome(padding) { sub = it }
        }
    }
}

@Composable
private fun ToolsHome(padding: PaddingValues, open: (Tool) -> Unit) {
    var confirm by remember { mutableStateOf(false) }
    Page("Tools", padding) {
        item { Stagger(0) { Column {
            SmallTitle("Tools")
            Group {
                ArrowPreference("Diagnostics", summary = "Battery, storage and memory", onClick = { open(Tool.Diagnostics) })
                ArrowPreference("Interpreter", summary = "Translate speech and text", onClick = { open(Tool.Interpreter) })
                ArrowPreference("Skills", summary = "Instructions Bixby can load", onClick = { open(Tool.Skills) })
            }
        } } }
        item { Stagger(1) { Column {
            SmallTitle("Actions")
            Group {
                ArrowPreference("Read the screen aloud", summary = "Describe what is open", onClick = { BixbyEngine.describeScreen() })
                ArrowPreference("Test voice", onClick = { BixbyEngine.testVoice() })
            }
        } } }
        item { Stagger(2) { Column {
            SmallTitle("Data")
            Group { ArrowPreference("Clear all chats", summary = "Deletes every conversation", onClick = { confirm = true }) }
        } } }
    }
    OverlayDialog(show = confirm, title = "Clear all chats?", summary = "Every conversation will be deleted.", onDismissRequest = { confirm = false }) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            TextButton("Cancel", { confirm = false }, Modifier.weight(1f))
            TextButton("Delete", { BixbyEngine.clearAllChats(); confirm = false }, Modifier.weight(1f), colors = ButtonDefaults.textButtonColorsPrimary())
        }
    }
}

// ---------------------------------------------------------------- Diagnostics
@Composable
private fun Meter(title: String, value: String, frac: Float, warn: Boolean) {
    val c = MiuixTheme.colorScheme
    val anim by animateFloatAsState(frac.coerceIn(0f, 1f), tween(800), label = "meter")
    Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
        Row(Modifier.fillMaxWidth()) {
            Text(title, Modifier.weight(1f), style = MiuixTheme.textStyles.body1)
            Text(value, color = if (warn) c.error else c.onSurfaceVariantSummary, style = MiuixTheme.textStyles.body2)
        }
        Spacer(Modifier.height(8.dp))
        LinearProgressIndicator(Modifier.fillMaxWidth(), progress = anim)
    }
}

@Composable
private fun DiagnosticsScreen(padding: PaddingValues, onBack: () -> Unit) {
    val ctx = LocalContext.current
    var h by remember { mutableStateOf(Diagnostics.read(ctx)) }
    var report by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) { while (true) { delay(5000); h = Diagnostics.read(ctx) } }
    Page("Diagnostics", padding, onBack) {
        item {
            Group {
                Meter("Battery", "${h.battery}%" + if (h.charging) ", charging" else "", h.battery / 100f, h.battery < 20 && !h.charging)
                Meter("Battery temperature", "${"%.1f".format(h.tempC)} °C", h.tempC / 60f, h.tempC > 42)
                Meter("Storage", "${(h.storageUsed * 100).toInt()}% used, ${"%.1f".format(h.storageFreeGb)} GB free", h.storageUsed, h.storageUsed > .9f)
                Meter("Memory", "${(h.ramUsed * 100).toInt()}% used, ${"%.1f".format(h.ramFreeGb)} GB free", h.ramUsed, h.ramUsed > .9f)
                Meter("Uptime", "${"%.1f".format(h.uptimeH)} h", h.uptimeH / 168f, h.uptimeH > 168)
            }
        }
        item {
            ThemedButton(if (busy) "Checking" else "Check with Bixby",
                { if (!busy) { busy = true; scope.launch { report = runCatching { BixbyEngine.diagnose(h) }.getOrElse { "Error: ${it.message}" }; busy = false } } },
                Modifier.fillMaxWidth().padding(horizontal = 12.dp).padding(bottom = 12.dp), primary = true)
        }
        if (report.isNotEmpty()) item { Group { Text(report, Modifier.padding(16.dp), style = MiuixTheme.textStyles.body1) } }
    }
}

// ---------------------------------------------------------------- Interpreter
@Composable
private fun InterpreterScreen(padding: PaddingValues, onBack: () -> Unit) {
    val prefs = BixbyEngine.prefs
    val langs = listOf("English", "Vietnamese", "Chinese", "Japanese", "Korean", "Spanish", "French", "German", "Hindi", "Arabic", "Russian")
    var input by remember { mutableStateOf("") }
    var out by remember { mutableStateOf("") }
    var lang by remember { mutableIntStateOf(langs.indexOf(prefs.targetLang).coerceAtLeast(0)) }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    fun go() { if (input.isBlank() || busy) return; busy = true; scope.launch {
        out = runCatching { BixbyEngine.translate(input, langs[lang]) }.getOrElse { "Error: ${it.message}" }; busy = false } }
    Page("Interpreter", padding, onBack) {
        item { Group { OverlayDropdownPreference(langs, lang, "Translate to", onSelectedIndexChange = { lang = it; prefs.targetLang = langs[it] }) } }
        item {
            Group {
                TextField(input, { input = it }, Modifier.fillMaxWidth().padding(12.dp), label = "Text or speech", minLines = 3)
                Row(Modifier.padding(start = 12.dp, end = 12.dp, bottom = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton({ BixbyEngine.listenInto { input = it; go() } }) { Icon(MiuixIcons.Mic, "Speak", tint = MiuixTheme.colorScheme.primary) }
                    ThemedButton(if (busy) "Translating" else "Translate", ::go, Modifier.weight(1f), primary = true)
                }
            }
        }
        if (out.isNotEmpty()) item {
            Group {
                Text(out, Modifier.padding(16.dp), style = MiuixTheme.textStyles.title4)
                TextButton("Play", { BixbyEngine.speak(out) }, Modifier.padding(start = 8.dp, bottom = 8.dp))
            }
        }
    }
}

// ---------------------------------------------------------------- Skills
@Composable
private fun SkillsScreen(padding: PaddingValues, onBack: () -> Unit) {
    var editing by remember { mutableStateOf<String?>(null) }   // "" = new skill
    var tick by remember { mutableIntStateOf(0) }
    BackHandler(editing != null) { editing = null }
    val e = editing
    if (e == null) {
        val names = remember(tick) { Skills.names() }
        Page("Skills", padding, onBack, actions = { IconButton({ editing = "" }) { Icon(MiuixIcons.Add, "New skill") } }) {
            item {
                Text("Short instructions Bixby can load when a task needs them. It sees each skill's first line.", Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary, style = MiuixTheme.textStyles.footnote1)
            }
            item {
                Group {
                    if (names.isEmpty()) BasicComponent(title = "No skills", summary = "Tap + to add one")
                    names.forEach { n -> ArrowPreference(n, summary = Skills.description(n), onClick = { editing = n }) }
                }
            }
        }
    } else {
        var name by remember(e) { mutableStateOf(e) }
        var body by remember(e) { mutableStateOf(if (e.isEmpty()) "" else Skills.read(e).orEmpty()) }
        Page(if (e.isEmpty()) "New skill" else e, padding, { editing = null }) {
            item {
                Group {
                    TextField(name, { name = it }, Modifier.fillMaxWidth().padding(12.dp), label = "Name", singleLine = true, enabled = e.isEmpty())
                    TextField(body, { body = it }, Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, bottom = 12.dp),
                        label = "First line: what it does. Then the steps.", minLines = 8)
                }
            }
            item {
                Row(Modifier.padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (e.isNotEmpty()) TextButton("Delete", { Skills.delete(e); tick++; editing = null }, Modifier.weight(1f))
                    ThemedButton("Save", { if (name.isNotBlank() && body.isNotBlank()) { Skills.save(name, body); tick++; editing = null } }, Modifier.weight(1f), primary = true)
                }
            }
        }
    }
}
