package com.bixbyx.assistant.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bixbyx.assistant.R
import com.bixbyx.assistant.agent.Updater
import kotlinx.coroutines.launch

private sealed class Upd {
    object Idle : Upd(); object Checking : Upd(); object Latest : Upd(); object Failed : Upd()
    data class Available(val r: Updater.Release) : Upd()
}

private fun open(ctx: Context, url: String) { runCatching { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } }

@Composable
fun AboutScreen() {
    val ctx = LocalContext.current
    val version = remember { runCatching { ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName }.getOrNull() ?: "?" }
    var upd by remember { mutableStateOf<Upd>(Upd.Idle) }
    val scope = rememberCoroutineScope()
    val scale = remember { Animatable(.5f) }
    LaunchedEffect(Unit) { scale.animateTo(1f, spring(dampingRatio = .38f, stiffness = 180f)) }
    val t = rememberInfiniteTransition(label = "about")
    val float by t.animateFloat(-6f, 6f, infiniteRepeatable(tween(2600, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "f")

    OneUiPage("About") {
        item { Column(Modifier.fillMaxWidth().padding(vertical = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Image(painterResource(R.drawable.ic_bixby_logo), "Bixby", Modifier.size(120.dp).graphicsLayer {
                scaleX = scale.value; scaleY = scale.value; translationY = float * density })
            Spacer(Modifier.height(12.dp))
            Text("Bixby", fontSize = 26.sp)
            Text("Version $version", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurface.copy(.55f))
        } }

        item { Stagger(0) { OneGroup {
            OneRow("Software update", when (val u = upd) {
                Upd.Idle -> "Tap to check for a new version"; Upd.Checking -> "Checking…"; Upd.Latest -> "You're up to date"
                Upd.Failed -> "Couldn't reach GitHub"; is Upd.Available -> "${u.r.tag} is available" },
                Icons.Default.SystemUpdate, OneBlue, {
                    val cur = upd
                    if (cur is Upd.Available) open(ctx, cur.r.apk ?: cur.r.page)
                    else if (cur != Upd.Checking) { upd = Upd.Checking; scope.launch {
                        val r = Updater.latest()
                        upd = when { r == null -> Upd.Failed; Updater.isNewer(r.tag, version) -> Upd.Available(r); else -> Upd.Latest } } }
                }) {
                AnimatedContent(upd, transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "upd") { u ->
                    when (u) {
                        Upd.Checking -> CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
                        Upd.Latest -> Icon(Icons.Default.CheckCircle, null, tint = OneGreen)
                        is Upd.Available -> Text("Download", color = OneBlue, fontSize = 14.sp)
                        else -> Chevron()
                    }
                }
            }
        } } }

        item { Stagger(1) { OneGroup {
            OneRow("Source code", "github.com/${Updater.REPO}", Icons.Default.Code, OnePurple, { open(ctx, "https://github.com/${Updater.REPO}") }) { Chevron() }
            OneDivider()
            OneRow("Release notes", "Every build is published automatically", Icons.Default.Notes, OneGreen, { open(ctx, "https://github.com/${Updater.REPO}/releases") }) { Chevron() }
        } } }

        item { Stagger(2) { OneCard {
            Text("Credits", fontSize = 16.sp)
            Text("AI screen-control loop based on private-agent by orailnoor. Design inspired by Samsung One UI. Voice by Fish Audio. " +
                "Bixby, One UI, Xiaomi and HyperOS belong to their owners; this is an independent project, not affiliated with Samsung, Xiaomi or Fish Audio.",
                fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface.copy(.6f), modifier = Modifier.padding(top = 6.dp))
        } } }
    }
}
