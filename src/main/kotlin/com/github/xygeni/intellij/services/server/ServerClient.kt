package com.github.xygeni.intellij.services.server

import com.github.xygeni.intellij.logger.Logger
import com.github.xygeni.intellij.model.report.server.DetectorDetail
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import com.github.xygeni.intellij.settings.XygeniSettings


/**
 * ServerClient
 *
 * @author : Carmendelope
 * @version : 16/10/25 (Carmendelope)
 **/
class ServerClient(private val baseUrl: String, private val token: String) {

    constructor() : this(
        baseUrl = findBaseUrl(),
        token = findToken()
    )

    companion object {
        private fun findBaseUrl(): String {
            val settings = XygeniSettings.getInstance()
            return settings.apiUrl
        }

        private fun findToken(): String {
            val settings = XygeniSettings.getInstance()
            return settings.apiToken
        }
    }

    private val client = OkHttpClient()
    private val json = Json { prettyPrint = true }

    private inline fun <reified TRequest : Any, reified TResponse : Any> post(
        endpoint: String,
        requestObj: TRequest
    ): TResponse? {
        val jsonBody = json.encodeToString(requestObj)
        val requestBody = jsonBody.toRequestBody("application/json".toMediaType())

        val request = Request.Builder()
            .url("$baseUrl/$endpoint")
            .addHeader("Authorization", "Bearer $token")
            .addHeader("Content-Type", "application/json")
            .post(requestBody)
            .build()

        return executeRequest(request)
    }

    private inline fun <reified TResponse : Any> get(
        endpoint: String,
        params: Map<String, String> = emptyMap()
    ): TResponse? {
        val urlBuilder = "$baseUrl/$endpoint".toHttpUrl().newBuilder()
        for ((key, value) in params) {
            urlBuilder.addQueryParameter(key, value)
        }
        val url = urlBuilder.build().toString()

        val request = Request.Builder()
            .url(url)
            .addHeader("Authorization", "Bearer $token")
            .addHeader("Accept", "application/json")
            .get()
            .build()

        return executeRequest(request);
    }


    private inline fun <reified TResponse : Any> executeRequest(request: Request): TResponse? {
        client.newCall(request).execute().use { response ->
            val bodyString = response.body?.string() ?: return null

            if (!response.isSuccessful) {
                Logger.error("Error HTTP: ${response.code} - $bodyString")
                return null
            }

            return json.decodeFromString(bodyString)
        }
    }

    private fun executeRawRequest(request: Request): String? {
        client.newCall(request).execute().use { response ->
            val bodyString = response.body?.string() ?: return null

            if (!response.isSuccessful) {
                Logger.error("Error HTTP: ${response.code} - $bodyString")
                return null
            }

            return bodyString
        }
    }

    private fun getJson(endpoint: String, params: Map<String, String> = emptyMap()): String? {
        val urlBuilder = "$baseUrl/$endpoint".toHttpUrl().newBuilder()
        for ((key, value) in params) {
            urlBuilder.addQueryParameter(key, value)
        }
        val url = urlBuilder.build().toString()

        val request = Request.Builder()
            .url(url)
            .addHeader("Authorization", "Bearer $token")
            .addHeader("Accept", "application/json")
            .get()
            .build()

        return executeRawRequest(request)
    }

    fun getDetectorDetails (tool: String, kind: String, detector: String) : String{
        val params = mapOf("tool" to tool, "kind" to kind, "detectorId" to detector)
        val response =  this.getJson("internal/policy/detector/doc", params = params)

        if (response == null){
            Logger.log("NULL")
        }else{
            Logger.log("Response: $response")
        }
        return response.toString()
    }

}