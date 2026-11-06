package com.vozemtexto.app

import android.app.*
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.*
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.*
import android.provider.Settings
import org.json.JSONObject
import org.vosk.Model
import org.vosk.Recognizer

/** Serviço visível ao Android: usuário inicia, acompanha na notificação e pode interromper. */
class TranscriptionService : Service() {
    companion object {
        const val ACTION_START = "com.vozemtexto.app.START"
        const val ACTION_STOP = "com.vozemtexto.app.STOP"
        const val ACTION_REFRESH = "com.vozemtexto.app.REFRESH"
        const val EXTRA_KIND = "kind"
        const val EXTRA_RESULT_CODE = "projectionCode"
        const val EXTRA_PROJECTION_DATA = "projectionData"
        const val KIND_MIC = "mic"
        const val KIND_INTERNAL = "internal"
        private const val CHANNEL_ID = "transcription_active"
        private const val NOTIFICATION_ID = 8710
        private const val SAMPLE_RATE = 16_000
    }

    private val mainHandler = Handler(Looper.getMainLooper())
    private var worker: Thread? = null
    @Volatile private var stopRequested = false
    @Volatile private var record: AudioRecord? = null
    @Volatile private var projection: MediaProjection? = null
    private var overlay: FloatingTranscript? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        val manager = getSystemService(NotificationManager::class.java)
        val channel = NotificationChannel(CHANNEL_ID, "Transcrição em andamento",
            NotificationManager.IMPORTANCE_LOW)
        channel.description = "Indica a captura de áudio ativa e permite pará-la"
        manager.createNotificationChannel(channel)
        overlay = FloatingTranscript(this)
    }

    @Suppress("DEPRECATION")
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> { requestStop(); return START_NOT_STICKY }
            ACTION_REFRESH -> { refreshOverlay(); return START_NOT_STICKY }
            ACTION_START -> {
                if (worker?.isAlive == true || AppState.running && worker != null) {
                    refreshOverlay()
                    return START_NOT_STICKY
                }
                stopRequested = false
                val kind = intent.getStringExtra(EXTRA_KIND) ?: KIND_MIC
                try {
                    val notificationType = if (kind == KIND_INTERNAL)
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
                    else ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
                    startForeground(NOTIFICATION_ID, buildNotification(kind), notificationType)
                    AppState.updateStatus("Carregando modelo de português…", true)
                    val resultCode = intent.getIntExtra(EXTRA_RESULT_CODE, 0)
                    val projectionData: Intent? = if (Build.VERSION.SDK_INT >= 33)
                        intent.getParcelableExtra(EXTRA_PROJECTION_DATA, Intent::class.java)
                    else intent.getParcelableExtra(EXTRA_PROJECTION_DATA)
                    worker = Thread({ capture(kind, resultCode, projectionData) }, "transcricao-vosk").apply { start() }
                    refreshOverlay()
                } catch (e: Exception) {
                    AppState.updateStatus("Erro ao iniciar: ${e.localizedMessage}", false)
                    mainHandler.post { stopSelf() }
                }
                return START_NOT_STICKY
            }
            else -> {
                stopSelf()
                return START_NOT_STICKY
            }
        }
    }

    private fun buildNotification(kind: String): Notification {
        val stopIntent = Intent(this, TranscriptionService::class.java).setAction(ACTION_STOP)
        val stopPending = PendingIntent.getService(this, 1, stopIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val openIntent = PendingIntent.getActivity(this, 2,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        return Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_app)
            .setContentTitle("Voz em Texto • gravando")
            .setContentText(if (kind == KIND_MIC) "Transcrevendo o microfone" else "Transcrevendo áudio interno permitido")
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(openIntent)
            .addAction(Notification.Action.Builder(null, "Parar", stopPending).build())
            .build()
    }

    private fun capture(kind: String, resultCode: Int, projectionData: Intent?) {
        var model: Model? = null
        var recognizer: Recognizer? = null
        var recorder: AudioRecord? = null
        var failure: String? = null
        try {
            check(ModelDownloader.isInstalled(this)) { "Baixe o modelo de português primeiro." }
            model = Model(ModelDownloader.modelDir(this).absolutePath)
            if (stopRequested) return
            recognizer = Recognizer(model, SAMPLE_RATE.toFloat())
            val bufferSize = maxOf(8_192,
                AudioRecord.getMinBufferSize(SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT) * 2)
            val format = AudioFormat.Builder()
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .setSampleRate(SAMPLE_RATE)
                .setChannelMask(AudioFormat.CHANNEL_IN_MONO)
                .build()
            val builder = AudioRecord.Builder().setAudioFormat(format).setBufferSizeInBytes(bufferSize)
            if (kind == KIND_INTERNAL) {
                check(resultCode == Activity.RESULT_OK && projectionData != null) {
                    "É necessário autorizar a captura do Android."
                }
                val manager = getSystemService(MediaProjectionManager::class.java)
                val token = manager.getMediaProjection(resultCode, projectionData)
                projection = token
                token.registerCallback(object : MediaProjection.Callback() {
                    override fun onStop() { requestStop() }
                }, mainHandler)
                val config = AudioPlaybackCaptureConfiguration.Builder(token)
                    .addMatchingUsage(AudioAttributes.USAGE_MEDIA)
                    .addMatchingUsage(AudioAttributes.USAGE_GAME)
                    .addMatchingUsage(AudioAttributes.USAGE_UNKNOWN)
                    .build()
                builder.setAudioPlaybackCaptureConfig(config)
            } else {
                builder.setAudioSource(MediaRecorder.AudioSource.VOICE_RECOGNITION)
            }
            recorder = builder.build()
            check(recorder.state == AudioRecord.STATE_INITIALIZED) {
                "O dispositivo não conseguiu iniciar a captura de som."
            }
            record = recorder
            if (stopRequested) return
            recorder.startRecording()
            check(recorder.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                "O Android recusou o início da gravação."
            }
            AppState.updateStatus(if (kind == KIND_MIC) "Ouvindo o microfone…" else "Ouvindo áudio interno…", true)
            val buffer = ByteArray(4096)
            var lastPartial = ""
            var lastUpdate = 0L
            while (!stopRequested) {
                val received = recorder.read(buffer, 0, buffer.size, AudioRecord.READ_BLOCKING)
                if (received <= 0) {
                    if (received < 0 && !stopRequested) error("Erro ao ler o áudio: $received")
                    continue
                }
                val completedPhrase = recognizer.acceptWaveForm(buffer, received)
                if (completedPhrase) {
                    AppState.commit(this, jsonField(recognizer.result, "text"))
                    lastPartial = ""
                } else if (!Preferences.phrases(this)) {
                    val partial = jsonField(recognizer.partialResult, "partial")
                    val now = android.os.SystemClock.elapsedRealtime()
                    if (partial != lastPartial && now - lastUpdate > 190L) {
                        AppState.updatePartial(partial)
                        lastPartial = partial
                        lastUpdate = now
                    }
                } else if (AppState.partial.isNotEmpty()) {
                    AppState.updatePartial("")
                }
            }
        } catch (e: Exception) {
            if (!stopRequested) failure = "Erro de captura: ${e.localizedMessage ?: "desconhecido"}"
        } finally {
            try { recognizer?.let { AppState.commit(this, jsonField(it.finalResult, "text")) } }
            catch (_: Exception) { /* áudio final pode estar vazio */ }
            try { recorder?.stop() } catch (_: Exception) { }
            try { recorder?.release() } catch (_: Exception) { }
            record = null
            try { recognizer?.close() } catch (_: Exception) { }
            try { model?.close() } catch (_: Exception) { }
            try { projection?.stop() } catch (_: Exception) { }
            projection = null
            mainHandler.post {
                overlay?.hide()
                AppState.updateStatus(failure ?: "Transcrição parada. Texto preservado.", false)
                try { stopForeground(STOP_FOREGROUND_REMOVE) } catch (_: Exception) { }
                stopSelf()
            }
        }
    }

    private fun jsonField(json: String, name: String): String =
        try { JSONObject(json).optString(name, "").trim() } catch (_: Exception) { "" }

    private fun refreshOverlay() {
        if (AppState.running && Preferences.overlay(this) && Settings.canDrawOverlays(this)) {
            overlay?.show()
        } else {
            overlay?.hide()
        }
    }

    private fun requestStop() {
        stopRequested = true
        try { record?.stop() } catch (_: Exception) { }
        worker?.interrupt()
        // O worker drena o texto final e encerra serviço/notificação.
        if (worker == null) {
            overlay?.hide()
            AppState.updateStatus("Transcrição parada", false)
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    override fun onDestroy() {
        stopRequested = true
        try { record?.stop() } catch (_: Exception) { }
        overlay?.hide()
        super.onDestroy()
    }
}
