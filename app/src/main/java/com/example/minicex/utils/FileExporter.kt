package com.example.minicex.utils

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.os.Environment
import android.widget.Toast
import androidx.core.content.FileProvider
import com.google.gson.Gson
import okhttp3.ResponseBody
import java.io.File
import java.io.FileOutputStream

/**
 * Guarda archivos binarios (PDF/XLSX/CSV) generados por el servidor en el
 * directorio privado de descargas de la app y los abre con el visor adecuado
 * (Adobe Acrobat, Excel, Google Sheets, etc.) vía FileProvider.
 */
object FileExporter {

    const val MIME_PDF = "application/pdf"
    const val MIME_XLSX = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
    const val MIME_CSV = "text/csv"

    /** Escribe [bytes] en Downloads de la app y abre el archivo. Devuelve null si falla. */
    fun saveAndOpen(context: Context, bytes: ByteArray, fileName: String, mimeType: String): File? {
        return try {
            val dir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
            if (dir == null) {
                Toast.makeText(context, "No se pudo acceder al directorio de descargas", Toast.LENGTH_LONG).show()
                return null
            }
            val file = File(dir, fileName)
            FileOutputStream(file).use { it.write(bytes) }
            openFile(context, file, mimeType)
            file
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(context, "Error al guardar el archivo: ${e.message}", Toast.LENGTH_LONG).show()
            null
        }
    }

    /** Abre un archivo con la app correspondiente usando FileProvider. */
    fun openFile(context: Context, file: File, mimeType: String) {
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, mimeType)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try {
            context.startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(
                context,
                "No tienes una app instalada para abrir este archivo (${file.name})",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    /** Extrae el mensaje "message" de un error JSON del servidor (fallback si no se puede parsear). */
    fun serverMessage(errorBody: ResponseBody?, fallback: String): String {
        if (errorBody == null) return fallback
        return try {
            val json = errorBody.string()
            if (json.isBlank()) fallback
            else Gson().fromJson(json, ServerMessage::class.java)?.message
                ?.takeIf { it.isNotBlank() } ?: fallback
        } catch (_: Exception) {
            fallback
        }
    }

    private data class ServerMessage(val success: Boolean = false, val message: String? = null)
}