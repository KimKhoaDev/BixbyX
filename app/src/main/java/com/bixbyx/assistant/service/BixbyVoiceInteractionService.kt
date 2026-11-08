package com.bixbyx.assistant.service

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionService
import android.service.voice.VoiceInteractionService
import android.service.voice.VoiceInteractionSession
import android.service.voice.VoiceInteractionSessionService
import com.bixbyx.assistant.AssistActivity

/** Registers Bixby as the "Digital assistant app" so power-button long-press / assist gesture opens it. */
class BixbyVoiceInteractionService : VoiceInteractionService()

/** Android only lists a voice interaction service as selectable if it declares a recognition service. */
class BixbyRecognitionService : RecognitionService() {
    override fun onStartListening(intent: Intent?, listener: Callback?) { runCatching { listener?.error(android.speech.SpeechRecognizer.ERROR_CLIENT) } }
    override fun onCancel(listener: Callback?) {}
    override fun onStopListening(listener: Callback?) {}
}

fun openBixby(ctx: Context, listen: Boolean) {
    ctx.startActivity(Intent(ctx, AssistActivity::class.java)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP).putExtra("listen", listen))
}

class BixbySessionService : VoiceInteractionSessionService() {
    override fun onNewSession(args: Bundle?): VoiceInteractionSession = object : VoiceInteractionSession(this) {
        override fun onShow(args: Bundle?, flags: Int) {
            super.onShow(args, flags)
            val i = Intent(this@BixbySessionService, AssistActivity::class.java).putExtra("listen", true)
            try { startAssistantActivity(i) } catch (e: Exception) { openBixby(this@BixbySessionService, true) }
            hide()
        }
    }
}
