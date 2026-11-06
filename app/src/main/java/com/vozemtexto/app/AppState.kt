package com.vozemtexto.app

import android.content.Context
import android.os.Handler
import android.os.Looper
import java.io.File
import java.util.concurrent.CopyOnWriteArraySet

object Preferences {
    private const val PREFS = "vozemtexto_settings"
    private const val MODE = "mode_phrases"
    private const val OVERLAY = "overlay_enabled"
    private const val NOTIFICATION_ASKED = "notification_asked"
    private const val SOURCE_INTERNAL = "source_internal"

    fun sourceInternal(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(SOURCE_INTERNAL, false)

    fun setSourceInternal(context: Context, internal: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putBoolean(SOURCE_INTERNAL, internal).apply()
    }

    fun phrases(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(MODE, false)

    fun setPhrases(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(MODE, enabled).apply()
    }

    fun overlay(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(OVERLAY, false)

    fun setOverlay(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(OVERLAY, enabled).apply()
    }

    fun notificationAsked(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(NOTIFICATION_ASKED, false)

    fun setNotificationAsked(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putBoolean(NOTIFICATION_ASKED, true).apply()
    }
}

object TranscriptStore {
    private const val FILE_NAME = "transcricao.txt"
    private fun file(context: Context): File = File(context.filesDir, FILE_NAME)

    @Synchronized fun read(context: Context): String = file(context).let { f ->
        if (f.exists()) f.readText(Charsets.UTF_8) else ""
    }

    @Synchronized fun append(context: Context, text: String) {
        file(context).appendText(text.trim() + "\n", Charsets.UTF_8)
    }

    @Synchronized fun clear(context: Context) {
        file(context).writeText("", Charsets.UTF_8)
    }
}

object AppState {
    private val callbacks = CopyOnWriteArraySet<() -> Unit>()
    private val handler = Handler(Looper.getMainLooper())

    @Volatile var running = false
        private set
    @Volatile var status = "Pronto para começar"
        private set
    @Volatile var committed = ""
        private set
    @Volatile var partial = ""
        private set

    fun initialize(context: Context) {
        committed = TranscriptStore.read(context).takeLast(25_000)
        notifyViews()
    }

    fun listen(callback: () -> Unit) { callbacks.add(callback); notifyViews() }
    fun unlisten(callback: () -> Unit) { callbacks.remove(callback) }

    private fun notifyViews() {
        handler.post { callbacks.forEach { it.invoke() } }
    }

    fun updateStatus(newStatus: String, active: Boolean) {
        status = newStatus
        running = active
        if (!active) partial = ""
        notifyViews()
    }

    fun updatePartial(text: String) {
        partial = text
        notifyViews()
    }

    fun commit(context: Context, recognized: String) {
        val normalized = recognized.trim()
        if (normalized.isBlank()) return
        val polished = normalized.replaceFirstChar { it.titlecase() }
            .let { if (it.last() in ".!?" ) it else "$it." }
        TranscriptStore.append(context, polished)
        committed = (committed + "$polished\n").takeLast(25_000)
        partial = ""
        notifyViews()
    }

    fun clear(context: Context) {
        TranscriptStore.clear(context)
        committed = ""
        partial = ""
        notifyViews()
    }
}
