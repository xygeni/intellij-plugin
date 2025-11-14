package com.github.xygeni.intellij.services.server

import com.github.xygeni.intellij.logger.Logger
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import com.github.xygeni.intellij.settings.XygeniSettings

import com.vladsch.flexmark.html.HtmlRenderer
import com.vladsch.flexmark.parser.Parser
import org.json.JSONObject



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
            return settings.apiToken?: ""
        }

        private val parser: Parser by lazy { Parser.builder().build() }
        private val renderer: HtmlRenderer by lazy { HtmlRenderer.builder().build() }

        fun convert(docMD: String): String {
            if (docMD.isNotEmpty()){
                val document = parser.parse(docMD)
                val htmlConverted = renderer.render(document)
                    .replace("<a href=", "<a target=\"_blank\" href=")
                return htmlConverted
            }
            return docMD
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
        if (this.baseUrl == "" || this.token == "") {
            Logger.log("❌ Cannot load detector details because the server configuration is missing.")
            throw Exception("❌ Cannot load detector details because the server configuration is missing.")
        }
        val params = mapOf("tool" to tool, "kind" to kind, "detectorId" to detector)
        val response =  this.getJson("internal/policy/detector/doc", params = params)

        if (response.isNullOrBlank()) return ""
        return try{
            val jsonData = JSONObject(response)
            val docMD = jsonData.optString ("descriptionDoc")
            val htmlConverted = convert(docMD)
            jsonData.put("descriptionDoc", htmlConverted)
            jsonData.toString()
        }
        catch (e: Exception) {
            println("Error processing AsciiDoc: {$e.message}")
             response
        }

    }

}