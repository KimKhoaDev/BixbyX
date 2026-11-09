package com.bixbyx.assistant.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.bixbyx.assistant.R
import com.bixbyx.assistant.agent.Updater
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme

private sealed class Upd {
    object Idle : Upd(); object Checking : Upd(); object Latest : Upd(); object Failed : Upd()
    data class Available(val r: Updater.Release) : Upd()
}

private fun open(ctx: Context, url: String) { runCatching { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } }

@Composable
fun AboutScreen(padding: PaddingValues) {
    val ctx = LocalContext.current
    val c = MiuixTheme.colorScheme
    val version = remember { runCatching { ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName }.getOrNull() ?: "" }
    var upd by remember { mutableStateOf<Upd>(Upd.Idle) }
    val scope = rememberCoroutineScope()
    val scale = remember { Animatable(.6f) }
    LaunchedEffect(Unit) { scale.animateTo(1f, spring(dampingRatio = .45f, stiffness = 200f)) }
    val float by rememberInfiniteTransition(label = "about").animateFloat(-5f, 5f, infiniteRepeatable(tween(2600, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "f")

    Page("About", padding) {
        item {
            Column(Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Image(painterResource(R.drawable.ic_bixby_logo), "Bixby", Modifier.size(104.dp).graphicsLayer { scaleX = scale.value; scaleY = scale.value; translationY = float * density })
                Spacer(Modifier.height(14.dp))
                Text("Bixby", style = MiuixTheme.textStyles.title2)
                Text("Version $version", color = c.onSurfaceVariantSummary, style = MiuixTheme.textStyles.body2)
            }
        }
        item { Stagger(0) { Group {
            ArrowPreference("Software update", summary = when (val u = upd) {
                Upd.Idle -> "Check for a new version"; Upd.Checking -> "Checking"; Upd.Latest -> "You have the latest version"
                Upd.Failed -> "Could not reach GitHub"; is Upd.Available -> "${u.r.tag} is available. Tap to download." },
                onClick = {
                    val cur = upd
                    if (cur is Upd.Available) open(ctx, cur.r.apk ?: cur.r.page)
                    else if (cur != Upd.Checking) { upd = Upd.Checking; scope.launch {
                        val r = Updater.latest()
                        upd = when { r == null -> Upd.Failed; Updater.isNewer(r.tag, version) -> Upd.Available(r); else -> Upd.Latest } } }
                })
        } } }
        item { Stagger(1) { Column {
            SmallTitle("Project")
            Group {
                ArrowPreference("Source code", summary = "github.com/${Updater.REPO}", onClick = { open(ctx, "https://github.com/${Updater.REPO}") })
                ArrowPreference("Release notes", onClick = { open(ctx, "https://github.com/${Updater.REPO}/releases") })
            }
        } } }
        item { Stagger(2) { Column {
            SmallTitle("Credits")
            Group {
                BasicComponent(title = "Screen control", summary = "Based on private-agent by orailnoor")
                BasicComponent(title = "Interface", summary = "Miuix")
                BasicComponent(title = "Voice", summary = "Fish Audio")
                BasicComponent(summary = "Independent project. Not affiliated with Samsung, Xiaomi or Fish Audio.")
            }
        } } }
    }
}
