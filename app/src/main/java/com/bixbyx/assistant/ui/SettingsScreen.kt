package com.bixbyx.assistant.ui

import android.Manifest
import android.app.StatusBarManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Icon as GIcon
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.bixbyx.assistant.R
import com.bixbyx.assistant.agent.BixbyEngine
import com.bixbyx.assistant.agent.ShizukuShell
import com.bixbyx.assistant.ai.LlmClient
import com.bixbyx.assistant.ai.Provider
import com.bixbyx.assistant.service.AgentAccessibilityService
import com.bixbyx.assistant.service.BixbyTile
import com.bixbyx.assistant.service.OverlayService
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.*
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.OverlayDropdownPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.ThemeColorSpec
import top.yukonga.miuix.kmp.theme.ThemePaletteStyle

private val ColorModes = listOf("System", "Light", "Dark", "Monet system", "Monet light", "Monet dark")
private val NavModes = listOf("Icon and text", "Icon only", "Icon with selected label")
private val NavPositions = listOf("Center", "Start", "End")
private val Palettes = ThemePaletteStyle.entries.map { it.name }
private val Specs = ThemeColorSpec.entries.map { it.name }
private val KeyNames = listOf("Default") + KeyColors.map { it.first }
private val Engines = listOf("Auto", "Fish Audio", "Phone")
private val EngineKeys = listOf("auto", "fish", "system")

private fun appDetails(ctx: Context) = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${ctx.packageName}")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
private fun go(ctx: Context, i: Intent) { runCatching { ctx.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } }

private fun isDefaultAssistant(ctx: Context): Boolean {
    val cr = ctx.contentResolver
    val a = runCatching { Settings.Secure.getString(cr, "assistant") }.getOrNull().orEmpty()
    val v = runCatching { Settings.Secure.getString(cr, "voice_interaction_service") }.getOrNull().orEmpty()
    return a.startsWith(ctx.packageName) || v.startsWith(ctx.packageName)
}

private fun filesAccess() = Build.VERSION.SDK_INT < 30 || Environment.isExternalStorageManager()

private fun openXiaomi(ctx: Context) {
    val tries = listOf(
        Intent().setClassName("com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity"),
        Intent("miui.intent.action.APP_PERM_EDITOR").setClassName("com.miui.securitycenter", "com.miui.permcenter.permissions.PermissionsEditorActivity").putExtra("extra_pkgname", ctx.packageName))
    for (i in tries) if (runCatching { ctx.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }.isSuccess) return
    ctx.startActivity(appDetails(ctx))
}

private fun addTile(ctx: Context) {
    if (Build.VERSION.SDK_INT >= 33) {
        runCatching {
            ctx.getSystemService(StatusBarManager::class.java).requestAddTileService(
                ComponentName(ctx, BixbyTile::class.java), "Bixby", GIcon.createWithResource(ctx, R.drawable.ic_stat_bixby), ctx.mainExecutor) { }
        }
    } else Toast.makeText(ctx, "Swipe down twice, tap the pencil and drag the Bixby tile in", Toast.LENGTH_LONG).show()
}

@Composable
private fun Field(value: String, onChange: (String) -> Unit, label: String, secret: Boolean = false) {
    TextField(value, onChange, Modifier.fillMaxWidth().padding(horizontal = 12.dp).padding(bottom = 8.dp), label = label, singleLine = true,
        visualTransformation = if (secret) PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None)
}

@Composable
fun SettingsScreen(padding: PaddingValues) {
    val ctx = LocalContext.current
    val prefs = BixbyEngine.prefs
    val look by BixbyEngine.look.collectAsState()
    var tick by remember { mutableIntStateOf(0) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { tick++ }
    val scope = rememberCoroutineScope()
    val version = remember { runCatching { ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName }.getOrNull() ?: "" }

    var provider by remember { mutableStateOf(prefs.provider) }
    var apiKey by remember(provider) { mutableStateOf(prefs.key(provider)) }
    var model by remember(provider) { mutableStateOf(prefs.model(provider)) }
    var url by remember { mutableStateOf(prefs.customUrl) }
    var models by remember { mutableStateOf<List<String>>(emptyList()) }
    var testMsg by remember { mutableStateOf("") }

    var fishKey by remember { mutableStateOf(prefs.fishKey) }
    var fishVoice by remember { mutableStateOf(prefs.fishVoice) }
    var tts by remember { mutableIntStateOf(EngineKeys.indexOf(prefs.ttsEngine).coerceAtLeast(0)) }
    var stt by remember { mutableIntStateOf(EngineKeys.indexOf(prefs.sttEngine).coerceAtLeast(0)) }
    var speak by remember { mutableStateOf(prefs.speak) }

    var orb by remember { mutableStateOf(prefs.orbEnabled) }
    var gesture by remember { mutableStateOf(prefs.gestureWake) }
    var describe by remember { mutableStateOf(prefs.autoDescribe) }
    var vol by remember { mutableStateOf(prefs.volumeCombo) }

    fun saveAi() { prefs.provider = provider; prefs.setKey(provider, apiKey); prefs.setModel(provider, model); prefs.customUrl = url }
    fun saveVoice() { prefs.fishKey = fishKey; prefs.fishVoice = fishVoice; prefs.ttsEngine = EngineKeys[tts]; prefs.sttEngine = EngineKeys[stt] }
    val onPrimary = MiuixTheme.colorScheme.onPrimary
    val monet = look.colorMode in 3..5

    Page("Settings", padding, subtitle = "v$version") {
        // ---- AI provider
        item { Stagger(0) { Column {
            SmallTitle("AI provider")
            Group {
                OverlayDropdownPreference(Provider.entries.map { it.label }, provider.ordinal, "Provider",
                    onSelectedIndexChange = { provider = Provider.entries[it]; prefs.provider = provider })
                if (provider == Provider.CUSTOM) Field(url, { url = it }, "Base URL (OpenAI compatible)")
                Field(apiKey, { apiKey = it }, "${provider.label} API key", secret = true)
                Field(model, { model = it }, "Model")
                if (models.isNotEmpty()) OverlayDropdownPreference(models.take(80), models.indexOf(model).coerceAtLeast(0), "Available models",
                    onSelectedIndexChange = { model = models[it] })
                Row(Modifier.padding(horizontal = 12.dp).padding(bottom = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button({ saveAi(); scope.launch {
                        models = runCatching { LlmClient.models(prefs) }.getOrDefault(emptyList())
                        testMsg = if (models.isEmpty()) "Could not list models. Check the key and URL." else "${models.size} models" } }, Modifier.weight(1f)) { Text("Fetch models") }
                    Button({ saveAi(); testMsg = "Testing"; scope.launch { testMsg = runCatching {
                        "Works: " + LlmClient.chat(prefs, listOf("user" to "Reply with the word OK")).take(40) }.getOrElse { "Failed: ${it.message}" } } },
                        Modifier.weight(1f), colors = ButtonDefaults.buttonColorsPrimary()) { Text("Save and test", color = onPrimary) }
                }
                if (testMsg.isNotEmpty()) Text(testMsg, Modifier.padding(horizontal = 16.dp).padding(bottom = 12.dp), color = MiuixTheme.colorScheme.onSurfaceVariantSummary, style = MiuixTheme.textStyles.footnote1)
            }
        } } }

        // ---- Voice
        item { Stagger(1) { Column {
            SmallTitle("Voice")
            Group {
                Field(fishKey, { fishKey = it }, "Fish Audio API key", secret = true)
                Field(fishVoice, { fishVoice = it }, "Voice model ID (optional)")
                OverlayDropdownPreference(Engines, tts, "Speech output", onSelectedIndexChange = { tts = it; saveVoice() })
                OverlayDropdownPreference(Engines, stt, "Speech input", onSelectedIndexChange = { stt = it; saveVoice() })
                SwitchPreference(speak, { speak = it; prefs.speak = it }, "Speak replies")
                Row(Modifier.padding(horizontal = 12.dp, vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button({ saveVoice(); BixbyEngine.notify("Saved") }, Modifier.weight(1f)) { Text("Save") }
                    Button({ saveVoice(); BixbyEngine.testVoice() }, Modifier.weight(1f), colors = ButtonDefaults.buttonColorsPrimary()) { Text("Test voice", color = onPrimary) }
                }
            }
        } } }

        // ---- Appearance (same options as the Miuix demo)
        item { Stagger(2) { Column {
            SmallTitle("Appearance")
            Group {
                OverlayDropdownPreference(ColorModes, look.colorMode, "Color mode", onSelectedIndexChange = { BixbyEngine.updateLook { l -> l.copy(colorMode = it) } })
                AnimatedVisibility(monet) {
                    OverlayDropdownPreference(KeyNames, look.seed, "Key color", onSelectedIndexChange = { BixbyEngine.updateLook { l -> l.copy(seed = it) } })
                }
                AnimatedVisibility(monet && look.seed > 0) {
                    Column {
                        OverlayDropdownPreference(Palettes, look.palette, "Palette style", onSelectedIndexChange = { BixbyEngine.updateLook { l -> l.copy(palette = it) } })
                        OverlayDropdownPreference(Specs, look.spec, "Color spec", onSelectedIndexChange = { BixbyEngine.updateLook { l -> l.copy(spec = it) } })
                    }
                }
                SwitchPreference(look.floatingNav, { BixbyEngine.updateLook { l -> l.copy(floatingNav = it) } }, "Floating navigation bar")
                AnimatedVisibility(look.floatingNav) {
                    OverlayDropdownPreference(NavPositions, look.navPosition, "Navigation bar position", onSelectedIndexChange = { BixbyEngine.updateLook { l -> l.copy(navPosition = it) } })
                }
                AnimatedVisibility(!look.floatingNav) {
                    OverlayDropdownPreference(NavModes, look.navMode, "Navigation bar mode", onSelectedIndexChange = { BixbyEngine.updateLook { l -> l.copy(navMode = it) } })
                }
                SwitchPreference(look.scrollHaptic, { BixbyEngine.updateLook { l -> l.copy(scrollHaptic = it) } }, "Scroll end haptic")
            }
        } } }

        // ---- Summon
        item { Stagger(3) { Column {
            SmallTitle("Summon Bixby")
            key(tick) { Group {
                val isDef = isDefaultAssistant(ctx)
                ArrowPreference("Default digital assistant", summary = if (isDef) "Bixby is selected" else "Choose Bixby",
                    onClick = { go(ctx, Intent(Settings.ACTION_VOICE_INPUT_SETTINGS)) }, endActions = { StatusText(isDef, "Set", "Not set") })
                ArrowPreference("Power button", summary = "Set long-press to the assistant, not Xiao AI. Additional settings, Button shortcuts.",
                    onClick = { Toast.makeText(ctx, "Additional settings > Button shortcuts > long-press power", Toast.LENGTH_LONG).show(); go(ctx, Intent(Settings.ACTION_SETTINGS)) })
                ArrowPreference("Quick Settings tile", summary = "Add a Bixby tile", onClick = { addTile(ctx) })
                SwitchPreference(vol, { vol = it; prefs.volumeCombo = it }, "Volume keys", summary = "Volume up and down together. Needs screen control.")
            } }
        } } }

        // ---- Permissions
        item { Stagger(4) { Column {
            SmallTitle("Permissions")
            key(tick) { Group {
                val a11y = AgentAccessibilityService.running()
                val overlay = Settings.canDrawOverlays(ctx)
                val mic = ContextCompat.checkSelfPermission(ctx, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
                val files = filesAccess()
                val shizuku = ShizukuShell.granted()
                val write = Settings.System.canWrite(ctx)
                ArrowPreference("Screen control", summary = "Accessibility service", onClick = { go(ctx, Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }, endActions = { StatusText(a11y) })
                ArrowPreference("Display over other apps", onClick = { go(ctx, Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${ctx.packageName}"))) }, endActions = { StatusText(overlay) })
                ArrowPreference("Microphone", onClick = { go(ctx, appDetails(ctx)) }, endActions = { StatusText(mic) })
                ArrowPreference("All files access", summary = "For files outside the workspace", onClick = {
                    if (Build.VERSION.SDK_INT >= 30) go(ctx, Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, Uri.parse("package:${ctx.packageName}"))) }, endActions = { StatusText(files) })
                ArrowPreference("Shizuku", summary = if (ShizukuShell.running()) "Running" else "Not running", onClick = {
                    if (ShizukuShell.running()) { ShizukuShell.request(); tick++ } else go(ctx, Intent(Intent.ACTION_VIEW, Uri.parse("https://shizuku.rikka.app/download/"))) }, endActions = { StatusText(shizuku) })
                ArrowPreference("Modify system settings", onClick = { go(ctx, Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS, Uri.parse("package:${ctx.packageName}"))) }, endActions = { StatusText(write) })
                ArrowPreference("Autostart and battery", summary = "Autostart on, battery set to No restrictions", onClick = { openXiaomi(ctx) })
            } }
        } } }

        // ---- Behavior
        item { Stagger(5) { Column {
            SmallTitle("Behavior")
            Group {
                SwitchPreference(orb, { orb = it; prefs.orbEnabled = it; if (it) OverlayService.start(ctx) else OverlayService.stop(ctx) }, "Floating icon", summary = "Show Bixby over other apps")
                SwitchPreference(gesture, { gesture = it; prefs.gestureWake = it; if (orb) { OverlayService.stop(ctx); OverlayService.start(ctx) } },
                    "Gesture wake-up", summary = "Raise the phone to your mouth. Experimental, needs the floating icon.")
                SwitchPreference(describe, { describe = it; prefs.autoDescribe = it }, "Describe the screen", summary = "Say what is on screen when Bixby opens")
            }
        } } }
    }
}
