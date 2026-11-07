# BixbyX — One UI 9 style Bixby for Xiaomi / HyperOS

Native Kotlin + Jetpack Compose. AI control loop is a port of orailnoor/private-agent
(read accessibility tree -> LLM returns one JSON action -> execute -> repeat).
UI is a One UI 9 look recreated in Compose (collapsing large titles, 26dp cards, spring slide transitions,
animated Bixby ring).  oneui-design/SESL needs a private GitHub Packages token, so it is NOT a dependency;
if you have a token you can swap the Compose theme for it later.

## Build
Open in Android Studio (Ladybug+), JDK 17, press Run. Or: `./gradlew assembleDebug`
APK: app/build/outputs/apk/debug/app-debug.apk

## First run on Xiaomi
1. Settings -> choose **Kilo AI** (key from app.kilo.ai) or **OpenRouter**, pick a model, "Save & test".
2. Permissions list: enable Screen control (accessibility), Display over other apps, Microphone.
   If "Restricted setting": App info -> ⋮ -> Allow restricted settings.
3. Xiaomi: tap "Autostart & battery" -> Autostart ON, Battery saver = No restrictions, and allow
   "Display pop-up windows while running in the background" + "Show on lock screen".
4. Shizuku (optional, adds wifi/data/bluetooth/dark-mode/force-stop shell control): install Shizuku, start it via
   wireless debugging, tap Grant in BixbyX.
5. Default assistant: Settings -> Apps -> Default apps -> Digital assistant app -> Bixby.
   (HyperOS may route power-button long-press to Xiaomi's assistant; set it in Settings -> Additional settings -> Button shortcuts.)

## Features (modelled on One UI 9 Bixby)
Floating orb · assist popup sheet · gesture wake-up (experimental sensor heuristic) · widget (type / talk / history)
· conversation history · Diagnostics (+AI analysis) · Interpreter · voice in/out · screen agent · Shizuku shell
· provider switcher (Kilo AI, OpenRouter, OpenAI, any OpenAI-compatible URL).
Shell commands from the model pass a deny-list (no rm/uninstall/reboot/etc.).
