package com.quant.terminal.api

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit

object ApiClient {
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    // Ganti USERNAME dengan username GitHub Anda
    private const val GIST_RAW_URL = "https://gist.githubusercontent.com/USERNAME/af6beb50b48ba4f24b7e672c4b174184/raw/active_server_url.txt"
    
    var activeBaseUrl: String = ""

    suspend fun syncActiveUrl(): Boolean = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder().url(GIST_RAW_URL).build()
            val response = client.newCall(request).execute()
            if (response.isSuccessful) {
                val url = response.body?.string()?.trim() ?: ""
                if (url.startsWith("http")) {
                    activeBaseUrl = url
                    return@withContext true
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return@withContext false
    }

    suspend fun postData(endpoint: String, jsonPayload: String): String? = withContext(Dispatchers.IO) {
        if (activeBaseUrl.isEmpty()) return@withContext null
        
        val body = jsonPayload.toRequestBody("application/json; charset=utf-8".toMediaType())
        val request = Request.Builder()
            .url("$activeBaseUrl$endpoint")
            .post(body)
            .build()

        try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) throw IOException("Unexpected code $response")
                return@use response.body?.string()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            return@withContext null
        }
    }
    
    suspend fun getData(endpoint: String): String? = withContext(Dispatchers.IO) {
        if (activeBaseUrl.isEmpty()) return@withContext null
        
        val request = Request.Builder()
            .url("$activeBaseUrl$endpoint")
            .get()
            .build()

        try {
            client.newCall(request).execute().use { response ->
                return@use response.body?.string()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            return@withContext null
        }
    }
}
