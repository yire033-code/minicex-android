package com.example.minicex.utils

import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.view.LayoutInflater
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.FileProvider
import com.example.minicex.BuildConfig
import com.example.minicex.R
import com.example.minicex.data.remote.RetrofitClient
import com.example.minicex.data.remote.dto.AppUpdateDto
import kotlinx.coroutines.*
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.util.Locale

class UpdateManager(private val context: Context, private val scope: CoroutineScope) {

    fun checkForUpdates() {
        scope.launch(Dispatchers.IO) {
            try {
                val response = RetrofitClient.instance.checkForUpdates()
                if (response.isSuccessful) {
                    val updateInfo = response.body()
                    if (updateInfo != null && updateInfo.success && updateInfo.versionCode > BuildConfig.VERSION_CODE) {
                        withContext(Dispatchers.Main) {
                            showUpdateDialog(updateInfo)
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun showUpdateDialog(updateInfo: AppUpdateDto) {
        val builder = AlertDialog.Builder(context)
        
        val title = when(updateInfo.tipo) {
            "critica" -> "🚨 ACTUALIZACIÓN CRÍTICA"
            "obligatoria" -> "Actualización Obligatoria"
            else -> "Actualización Recomendada"
        }
        builder.setTitle(title)
        
        val message = StringBuilder()
        if (updateInfo.tipo == "critica") {
            message.append("Esta versión corrige fallos graves de seguridad o corrupción de datos.\n\n")
        } else if (updateInfo.tipo == "obligatoria") {
            message.append("Se han realizado cambios necesarios en el sistema.\n\n")
        }
        
        message.append("Cambios en v${updateInfo.versionActual ?: ""}:\n")
        updateInfo.changeLogs?.forEach { message.append("• $it\n") }
        
        builder.setMessage(message.toString())
        
        val isMandatory = updateInfo.tipo == "obligatoria" || updateInfo.tipo == "critica"
        builder.setCancelable(!isMandatory)

        builder.setPositiveButton("Actualizar ahora") { _, _ ->
            startDownload(updateInfo.downloadUrl)
        }

        if (!isMandatory) {
            builder.setNegativeButton("Recordar más tarde", null)
        }

        val dialog = builder.create()
        dialog.show()
    }

    private fun startDownload(downloadUrl: String) {
        val dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_download_progress, null)
        val progressBar = dialogView.findViewById<ProgressBar>(R.id.downloadProgressBar)
        val progressText = dialogView.findViewById<TextView>(R.id.downloadProgressText)

        val progressDialog = AlertDialog.Builder(context)
            .setTitle("Descargando actualización...")
            .setView(dialogView)
            .setCancelable(false)
            .create()

        progressDialog.show()

        scope.launch(Dispatchers.IO) {
            try {
                val client = OkHttpClient()
                val request = Request.Builder().url(downloadUrl).build()
                val response = client.newCall(request).execute()

                if (!response.isSuccessful) throw Exception("Error de conexión con el servidor")

                val body = response.body ?: throw Exception("El servidor no envió datos")
                val totalBytes = body.contentLength()
                val inputStream = body.byteStream()
                
                // USAMOS getExternalFilesDir(null) porque coincide con "external_files" en file_paths.xml
                val file = File(context.getExternalFilesDir(null), "update_minicex.apk")
                val outputStream = FileOutputStream(file)

                val buffer = ByteArray(8192)
                var bytesRead: Int
                var downloadedBytes: Long = 0

                while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                    outputStream.write(buffer, 0, bytesRead)
                    downloadedBytes += bytesRead
                    
                    if (totalBytes > 0) {
                        val progress = (downloadedBytes * 100 / totalBytes).toInt()
                        withContext(Dispatchers.Main) {
                            progressBar.progress = progress
                            progressText.text = String.format(Locale.getDefault(), "%d%%", progress)
                        }
                    }
                }

                outputStream.flush()
                outputStream.close()
                inputStream.close()

                withContext(Dispatchers.Main) {
                    progressDialog.dismiss()
                    installApk(file)
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    progressDialog.dismiss()
                    Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun installApk(file: File) {
        try {
            val authority = "${context.packageName}.fileprovider"
            val uri: Uri = FileProvider.getUriForFile(context, authority, file)
            
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(context, "Error al instalar: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }
}
