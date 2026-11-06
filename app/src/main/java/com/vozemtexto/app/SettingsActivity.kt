package com.vozemtexto.app

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.widget.RadioButton
import android.widget.Switch
import android.widget.TextView

class SettingsActivity : Activity() {
    private lateinit var overlaySwitch: Switch
    private var updatingSwitch = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)
        SafeArea.apply(this)
        findViewById<TextView>(R.id.btnBack).setOnClickListener { finish() }
        val words = findViewById<RadioButton>(R.id.radioWords)
        val phrases = findViewById<RadioButton>(R.id.radioPhrases)
        if (Preferences.phrases(this)) phrases.isChecked = true else words.isChecked = true
        words.setOnClickListener { Preferences.setPhrases(this, false) }
        phrases.setOnClickListener { Preferences.setPhrases(this, true) }

        overlaySwitch = findViewById(R.id.switchOverlay)
        overlaySwitch.isChecked = Preferences.overlay(this)
        overlaySwitch.setOnCheckedChangeListener { _, checked ->
            if (updatingSwitch) return@setOnCheckedChangeListener
            Preferences.setOverlay(this, checked)
            if (checked && !Settings.canDrawOverlays(this)) {
                showOverlayPermissionHelp()
            }
        }
        findViewById<TextView>(R.id.btnOverlayHelp)
            .setOnClickListener { showOverlayPermissionHelp() }
    }

    override fun onResume() {
        super.onResume()
        // Se a permissão foi negada, impede marcar a janela como disponível.
        if (!Settings.canDrawOverlays(this) && Preferences.overlay(this)) {
            Preferences.setOverlay(this, false)
            updatingSwitch = true
            overlaySwitch.isChecked = false
            updatingSwitch = false
        }
    }

    private fun showOverlayPermissionHelp() {
        AlertDialog.Builder(this)
            .setTitle("Como permitir a janela flutuante")
            .setMessage("1. Toque em Abrir configurações.\n\n" +
                "2. Procure Voz em Texto.\n\n" +
                "3. Ative 'Mostrar sobre outros apps' (o nome varia conforme o aparelho).\n\n" +
                "4. Volte ao aplicativo e ligue a opção de janela flutuante.\n\n" +
                "Essa permissão é opcional: sem ela o app transcreve em segundo plano.")
            .setNegativeButton("Agora não", null)
            .setPositiveButton("Abrir configurações") { _, _ ->
                startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:$packageName")))
            }
            .show()
    }
}
