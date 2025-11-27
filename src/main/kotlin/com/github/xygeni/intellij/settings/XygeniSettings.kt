package com.github.xygeni.intellij.settings

/**
 * XygeniSettings
 *
 * @author : Carmendelope
 * @version : 7/10/25 (Carmendelope)
 **/
import com.github.xygeni.intellij.logger.Logger
import com.github.xygeni.intellij.model.report.server.RemediationData
import com.intellij.credentialStore.CredentialAttributes
import com.intellij.credentialStore.generateServiceName
import com.intellij.ide.passwordSafe.PasswordSafe
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage

@State(
    name = "XygeniGlobalSettings",
    storages = [Storage("xygeni_global_settings.xml")]
)
@Service(Service.Level.APP)
class XygeniSettings : PersistentStateComponent<XygeniSettings.State> {

    data class State(
        var apiUrl: String = ""
    )

    private var state = State()

    override fun getState(): State = state
    override fun loadState(state: State) {
        this.state = state
    }

    init {
        if (state.apiUrl.isBlank()) {
            state.apiUrl = "https://api.xygeni.io/deps-doctor-service"
        }
    }

    companion object {
        private const val TOKEN_KEY = "XygeniApiToken"

        fun getInstance(): XygeniSettings =
            ApplicationManager.getApplication().getService(XygeniSettings::class.java)
        
        /**
         * Creates CredentialAttributes for storing API token securely.
         * Note: Plugin verifier may report false positive deprecated warnings due to 
         * Kotlin-generated synthetic constructors with DefaultConstructorMarker.
         * These warnings are suppressed in build.gradle.kts.
         */
        private fun createCredentialAttributes() = CredentialAttributes(
            generateServiceName("Xygeni", TOKEN_KEY),
            null
        )
    }

    var apiUrl: String
        get() = state.apiUrl
        set(value) {
            state.apiUrl = value
        }

    // --------------------
    // Token (Password safe)
    // --------------------

    private var cachedToken: String? = null
    var apiToken: String
        get() {
            cachedToken?.let { return it }
            val attributes = createCredentialAttributes()
            val token = PasswordSafe.instance.getPassword(attributes) ?: ""
            cachedToken = token
            return token
        }
        set(value) {
            cachedToken = value
            val attributes = createCredentialAttributes()
            PasswordSafe.instance.setPassword(attributes, value)
        }

    fun toEnv(): Map<String, String> = mapOf(
        "XYGENI_TOKEN" to apiToken,
        "XYGENI_URL" to apiUrl
    )

}