package com.github.xygeni.intellij.settings

/**
 * XygeniSettings
 *
 * @author : Carmendelope
 * @version : 7/10/25 (Carmendelope)
 **/
import com.github.xygeni.intellij.model.PluginConfig
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.project.Project

@State(
    name = "XygeniGlobalSettings",
    storages = [Storage("xygeni_global_settings.xml")]
)
@Service(Service.Level.APP)
class XygeniSettings : PersistentStateComponent<XygeniSettings.State> {

    data class State(
        var apiUrl: String = "",
        var apiToken: String = ""
    )

    private var state = State()

    override fun getState(): State = state

    override fun loadState(state: State) {
        this.state = state
    }

    fun toPluginConfig(): PluginConfig = PluginConfig(
        url = apiUrl,
        token = apiToken
    )

    companion object {
        fun getInstance(): XygeniSettings =
            com.intellij.openapi.application.ApplicationManager
                .getApplication()
                .getService(XygeniSettings::class.java)
    }

    var apiUrl: String
        get() = state.apiUrl
        set(value) {
            state.apiUrl = value
        }

    var apiToken: String
        get() = state.apiToken
        set(value) {
            state.apiToken = value
        }
}