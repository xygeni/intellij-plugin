package com.github.xygeni.intellij.model

import com.github.xygeni.intellij.settings.XygeniSettings

/**
 * PluginConfig
 *
 * @author : Carmendelope
 * @version : 6/10/25 (Carmendelope)
 **/

data class PluginConfig(
    var url: String = "",
    var token: String = ""
) {

    companion object{
        fun fromSettings(settings: XygeniSettings): PluginConfig {
            return PluginConfig(
                url = settings.state.apiUrl,
                token = settings.state.apiToken
            )
        }
    }

    fun isValid(): Boolean {
        return url.isNotBlank() && token.isNotBlank()
    }

    fun toEnv():Map<String, String>? {
        return mapOf(
            "XYGENI_TOKEN" to token,
            "XYGENI_URL" to url
        )
    }
}