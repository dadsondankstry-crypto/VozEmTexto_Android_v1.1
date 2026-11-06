package com.vozemtexto.app

import android.content.Context
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.zip.ZipInputStream

/** Modelo oficial leve pt-BR (Apache 2.0), baixado apenas após ação do usuário. */
object ModelDownloader {
    const val MODEL_NAME = "vosk-model-small-pt-0.3"
    private const val MODEL_URL = "https://alphacephei.com/vosk/models/$MODEL_NAME.zip"
    private const val MAX_EXTRACTED = 350L * 1024 * 1024

    fun modelDir(context: Context) = File(context.filesDir, MODEL_NAME)
    fun isInstalled(context: Context): Boolean =
        File(modelDir(context), "am").isDirectory &&
        File(modelDir(context), "conf").isDirectory &&
        File(modelDir(context), "graph").isDirectory

    fun install(context: Context, update: (Int, String) -> Unit, finish: (String?) -> Unit) {
        Thread({
            val download = File(context.cacheDir, "$MODEL_NAME.zip.part")
            val staging = File(context.filesDir, "$MODEL_NAME.installing")
            var connection: HttpURLConnection? = null
            try {
                if (isInstalled(context)) {
                    finish(null)
                    return@Thread
                }
                update(0, "Conectando ao download oficial…")
                connection = (URL(MODEL_URL).openConnection() as HttpURLConnection).apply {
                    connectTimeout = 15_000
                    readTimeout = 30_000
                    instanceFollowRedirects = true
                }
                connection.connect()
                if (connection.responseCode !in 200..299) {
                    error("Servidor respondeu: HTTP ${connection.responseCode}")
                }
                val contentLength = connection.contentLengthLong
                var bytesDownloaded = 0L
                connection.inputStream.use { input ->
                    FileOutputStream(download).use { output ->
                        val buffer = ByteArray(32 * 1024)
                        while (true) {
                            val count = input.read(buffer)
                            if (count == -1) break
                            output.write(buffer, 0, count)
                            bytesDownloaded += count
                            if (contentLength > 0) {
                                update((bytesDownloaded * 90 / contentLength).toInt().coerceIn(0, 90),
                                    "Baixando português: ${bytesDownloaded / 1_000_000} MB")
                            }
                        }
                    }
                }
                update(92, "Preparando reconhecimento offline…")
                staging.deleteRecursively()
                check(staging.mkdirs()) { "Não foi possível preparar a pasta do modelo." }
                var extracted = 0L
                ZipInputStream(download.inputStream().buffered()).use { zip ->
                    while (true) {
                        val entry = zip.nextEntry ?: break
                        // Somente arquivos na pasta esperada. Impede Zip Slip.
                        val prefix = "$MODEL_NAME/"
                        if (!entry.name.startsWith(prefix)) {
                            if (entry.name == "$MODEL_NAME/") continue
                            error("Conteúdo inesperado no modelo: ${entry.name}")
                        }
                        val relative = entry.name.removePrefix(prefix)
                        if (relative.isBlank()) continue
                        val file = File(staging, relative)
                        val rootPath = staging.canonicalPath + File.separator
                        check(file.canonicalPath.startsWith(rootPath)) { "Arquivo fora da pasta permitida." }
                        if (entry.isDirectory) file.mkdirs() else {
                            check(file.parentFile?.mkdirs() != false || file.parentFile?.isDirectory == true)
                            FileOutputStream(file).use { output ->
                                val buffer = ByteArray(32 * 1024)
                                while (true) {
                                    val count = zip.read(buffer)
                                    if (count == -1) break
                                    extracted += count
                                    check(extracted <= MAX_EXTRACTED) { "Modelo excedeu o limite de tamanho." }
                                    output.write(buffer, 0, count)
                                }
                            }
                        }
                        zip.closeEntry()
                    }
                }
                check(File(staging, "am").isDirectory && File(staging, "conf").isDirectory &&
                    File(staging, "graph").isDirectory) { "Modelo de reconhecimento incompleto." }
                val destination = modelDir(context)
                destination.deleteRecursively()
                check(staging.renameTo(destination)) { "Erro ao finalizar instalação do modelo." }
                update(100, "Modelo pronto para uso offline")
                finish(null)
            } catch (e: Exception) {
                staging.deleteRecursively()
                finish(e.localizedMessage ?: "Não foi possível baixar o modelo")
            } finally {
                download.delete()
                connection?.disconnect()
            }
        }, "download-modelo-pt").start()
    }
}
