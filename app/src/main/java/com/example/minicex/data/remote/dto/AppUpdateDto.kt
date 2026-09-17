package com.example.minicex.data.remote.dto

import com.google.gson.annotations.SerializedName

data class AppUpdateDto(
    val success: Boolean,
    val tipo: String, // "opcional", "obligatoria", "critica"
    @SerializedName("version_code") val versionCode: Int,
    @SerializedName("version_actual") val versionActual: String?,
    @SerializedName("change_logs") val changeLogs: List<String>?,
    @SerializedName("download_url") val downloadUrl: String
)
