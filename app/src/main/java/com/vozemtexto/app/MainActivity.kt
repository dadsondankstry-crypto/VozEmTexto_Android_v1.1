package com.vozemtexto.app

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.app.ProgressDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.View
import android.widget.Button
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

@Suppress("DEPRECATION")
class MainActivity : Activity() {
    private companion object {
        const val REQ_MIC = 201
        const val REQ_NOTIFICATION = 202
        const val REQ_CAPTURE = 203
        const val REQ_SAVE = 204
    }

    private val handler = Handler(Looper.getMainLooper())
    private val stateObserver: () -> Unit = { render() }
    private lateinit var statusView: TextView
    private lateinit var finalView: TextView
    private lateinit var partialView: TextView
    private lateinit var modeDescription: TextView
    private lateinit var startButton: Button
    private lateinit var sourceGroup: RadioGroup
    private lateinit var scroll: ScrollView
    private var priorText = ""
    private var downloading = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        SafeArea.apply(this)
        AppState.initialize(this)
        statusView = findViewById(R.id.status)
        finalView = findViewById(R.id.txtFinal)
        partialView = findViewById(R.id.txtPartial)
        modeDescription = findViewById(R.id.modeDescription)
        startButton = findViewById(R.id.btnStart)
        sourceGroup = findViewById(R.id.sourceGroup)
        sourceGroup.check(if (Preferences.sourceInternal(this)) R.id.sourceInternal else R.id.sourceMic)
        sourceGroup.setOnCheckedChangeListener { _, selected ->
            Preferences.setSourceInternal(this, selected == R.id.sourceInternal)
        }
        scroll = findViewById(R.id.scrollText)
        findViewById<TextView>(R.id.btnSettings).setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }
        startButton.setOnClickListener {
            if (AppState.running) requestStop() else begin()
        }
        findViewById<Button>(R.id.btnCopy).setOnClickListener {
            val text = TranscriptStore.read(this)
            if (text.isBlank()) { message("Ainda não há texto para copiar"); return@setOnClickListener }
            val clipboard = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(ClipData.newPlainText("Transcrição", text))
            message("Transcrição copiada")
        }
        findViewById<Button>(R.id.btnSave).setOnClickListener {
            if (TranscriptStore.read(this).isBlank()) {
                message("Ainda não há texto para salvar")
                return@setOnClickListener
            }
            val date = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm"))
            val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "text/plain"
                putExtra(Intent.EXTRA_TITLE, "transcricao_$date.txt")
            }
            startActivityForResult(intent, REQ_SAVE)
        }
        findViewById<Button>(R.id.btnShare).setOnClickListener {
            val text = TranscriptStore.read(this)
            if (text.isBlank()) { message("Ainda não há texto para compartilhar"); return@setOnClickListener }
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_SUBJECT, "Minha transcrição")
                putExtra(Intent.EXTRA_TEXT, text)
            }
            startActivity(Intent.createChooser(intent, "Compartilhar transcrição"))
        }
        findViewById<TextView>(R.id.btnClear).setOnClickListener {
            if (AppState.running) { message("Pare a transcrição antes de apagar o texto"); return@setOnClickListener }
            AlertDialog.Builder(this).setTitle("Apagar transcrição?")
                .setMessage("Essa ação apaga o texto salvo dentro do aplicativo. Não afeta arquivos TXT já exportados.")
                .setNegativeButton("Cancelar", null)
                .setPositiveButton("Apagar") { _, _ -> AppState.clear(this) }
                .show()
        }
        render()
    }

    override fun onResume() {
        super.onResume()
        AppState.listen(stateObserver)
        modeDescription.text = "Modo: ${if (Preferences.phrases(this)) "frases" else "palavra por palavra"}" +
            " • Janela: ${if (Preferences.overlay(this)) "ligada" else "desligada"}" +
            "\nPortuguês offline: ${if (ModelDownloader.isInstalled(this)) "pronto" else "download inicial necessário"}"
        if (AppState.running) {
            startService(Intent(this, TranscriptionService::class.java)
                .setAction(TranscriptionService.ACTION_REFRESH))
        }
    }

    override fun onPause() {
        AppState.unlisten(stateObserver)
        super.onPause()
    }

    private fun selectedKind(): String = if (sourceGroup.checkedRadioButtonId == R.id.sourceInternal)
        TranscriptionService.KIND_INTERNAL else TranscriptionService.KIND_MIC

    private fun begin() {
        if (downloading) return
        if (!ModelDownloader.isInstalled(this)) {
            requestModelDownload()
            return
        }
        if (Preferences.overlay(this) && !Settings.canDrawOverlays(this)) {
            AlertDialog.Builder(this).setTitle("Permitir janelinha flutuante?")
                .setMessage("Abra as configurações do Android e permita 'Mostrar sobre outros apps'. " +
                    "Ou continue sem janela: a transcrição continuará funcionando em segundo plano.")
                .setNegativeButton("Continuar sem janela") { _, _ ->
                    Preferences.setOverlay(this, false)
                    begin()
                }
                .setPositiveButton("Abrir configurações") { _, _ ->
                    startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:$packageName")))
                }.show()
            return
        }
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED &&
            !Preferences.notificationAsked(this)) {
            Preferences.setNotificationAsked(this)
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), REQ_NOTIFICATION)
            return
        }
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), REQ_MIC)
            return
        }
        if (selectedKind() == TranscriptionService.KIND_INTERNAL) {
            AlertDialog.Builder(this).setTitle("Transcrever o áudio do celular")
                .setMessage("O Android pedirá para autorizar a captura do som. " +
                    "Apenas apps que permitem a captura do próprio áudio poderão ser transcritos; " +
                    "chamadas e conteúdos protegidos podem não funcionar.")
                .setNegativeButton("Cancelar", null)
                .setPositiveButton("Autorizar") { _, _ ->
                    val manager = getSystemService(MediaProjectionManager::class.java)
                    startActivityForResult(manager.createScreenCaptureIntent(), REQ_CAPTURE)
                }.show()
        } else {
            startTranscription(TranscriptionService.KIND_MIC)
        }
    }

    private fun startTranscription(kind: String, resultCode: Int = 0, projectionData: Intent? = null) {
        val serviceIntent = Intent(this, TranscriptionService::class.java).apply {
            action = TranscriptionService.ACTION_START
            putExtra(TranscriptionService.EXTRA_KIND, kind)
            if (projectionData != null) {
                putExtra(TranscriptionService.EXTRA_RESULT_CODE, resultCode)
                putExtra(TranscriptionService.EXTRA_PROJECTION_DATA, projectionData)
            }
        }
        try {
            startForegroundService(serviceIntent)
            AppState.updateStatus("Preparando reconhecimento…", true)
        } catch (e: Exception) {
            AppState.updateStatus("Não foi possível iniciar: ${e.localizedMessage}", false)
            message("Falha ao iniciar captura")
        }
    }

    private fun requestStop() {
        startService(Intent(this, TranscriptionService::class.java)
            .setAction(TranscriptionService.ACTION_STOP))
        AppState.updateStatus("Finalizando…", true)
    }

    private fun requestModelDownload() {
        AlertDialog.Builder(this).setTitle("Instalar português offline")
            .setMessage("Para transformar voz e áudio interno em texto, o aplicativo precisa baixar " +
                "uma vez o modelo de reconhecimento de português (aproximadamente 31 MB). " +
                "Depois disso, a transcrição funciona sem enviar áudio à internet.\n\n" +
                "Baixar agora?")
            .setNegativeButton("Agora não", null)
            .setPositiveButton("Baixar") { _, _ -> downloadModel() }.show()
    }

    private fun downloadModel() {
        downloading = true
        val dialog = ProgressDialog(this).apply {
            setTitle("Instalando português")
            setMessage("Conectando…")
            setProgressStyle(ProgressDialog.STYLE_HORIZONTAL)
            max = 100
            setCancelable(false)
            show()
        }
        ModelDownloader.install(this.applicationContext,
            update = { progress, label -> handler.post {
                if (!isFinishing && !isDestroyed) {
                    dialog.progress = progress
                    dialog.setMessage(label)
                }
            } },
            finish = { error -> handler.post {
                downloading = false
                if (isFinishing || isDestroyed) return@post
                dialog.dismiss()
                if (error == null) {
                    message("Modelo instalado. Toque em iniciar para transcrever.")
                } else {
                    AlertDialog.Builder(this).setTitle("Não foi possível instalar o modelo")
                        .setMessage("Confira sua internet e o espaço livre. Detalhe: $error")
                        .setPositiveButton("Entendi", null).show()
                }
            } })
    }

    private fun render() {
        if (!::startButton.isInitialized) return
        statusView.text = "● ${AppState.status}"
        statusView.setTextColor(if (AppState.running) getColor(R.color.mint)
            else if (AppState.status.startsWith("Erro") || AppState.status.startsWith("Não"))
                getColor(R.color.alert) else getColor(R.color.text_muted))
        startButton.text = if (AppState.running) "■  Parar transcrição" else "▶  Iniciar transcrição"
        sourceGroup.isEnabled = !AppState.running
        for (i in 0 until sourceGroup.childCount) {
            sourceGroup.getChildAt(i).isEnabled = !AppState.running
        }
        // Mantém toda a transcrição no arquivo, mas limita a renderização a 16 mil caracteres.
        val fullText = AppState.committed
        val displayText = if (fullText.length > 16_000) "…\n" + fullText.takeLast(16_000) else fullText
        val rendered = displayText.ifBlank { "Sua transcrição aparecerá aqui..." }
        if (finalView.text.toString() != rendered) finalView.text = rendered
        partialView.text = AppState.partial.let { if (it.isNotBlank()) "● $it" else "" }
        val allText = rendered + partialView.text
        if (allText != priorText) {
            priorText = allText
            scroll.post { scroll.fullScroll(View.FOCUS_DOWN) }
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>,
        grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQ_NOTIFICATION) {
            // Notificação visual opcional; o serviço continua exigindo indicador de uso de captura.
            begin()
        } else if (requestCode == REQ_MIC) {
            if (grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) begin()
            else message("Autorize a gravação de áudio para usar a transcrição")
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQ_CAPTURE) {
            if (resultCode == RESULT_OK && data != null)
                startTranscription(TranscriptionService.KIND_INTERNAL, resultCode, data)
            else message("Captura de áudio não autorizada")
        } else if (requestCode == REQ_SAVE && resultCode == RESULT_OK && data?.data != null) {
            try {
                contentResolver.openOutputStream(data.data!!)?.bufferedWriter(Charsets.UTF_8).use { writer ->
                    requireNotNull(writer) { "Não foi possível abrir o destino." }
                    writer.write(TranscriptStore.read(this))
                }
                message("Arquivo TXT salvo com sucesso")
            } catch (e: Exception) { message("Erro ao salvar: ${e.localizedMessage}") }
        }
    }

    private fun message(text: String) = Toast.makeText(this, text, Toast.LENGTH_LONG).show()
}
