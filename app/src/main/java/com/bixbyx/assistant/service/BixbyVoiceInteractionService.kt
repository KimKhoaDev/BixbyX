package com.bixbyx.assistant.service

import android.content.Intent
import android.os.Bundle
import android.service.voice.VoiceInteractionService
import android.service.voice.VoiceInteractionSession
import android.service.voice.VoiceInteractionSessionService
import com.bixbyx.assistant.AssistActivity

/** Registers BixbyX as a "Digital assistant app" so power-button long-press / assist gesture opens it. */
class BixbyVoiceInteractionService : VoiceInteractionService()

class BixbySessionService : VoiceInteractionSessionService() {
    override fun onNewSession(args: Bundle?): VoiceInteractionSession = object : VoiceInteractionSession(this) {
        override fun onShow(args: Bundle?, flags: Int) {
            super.onShow(args, flags)
            startAssistantActivity(Intent(this@BixbySessionService, AssistActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK).putExtra("listen", true))
            hide()
        }
    }
}
