package com.github.xygeni.intellij.settings

/**
 * XygeniSettings
 *
 * @author : Carmendelope
 * @version : 7/10/25 (Carmendelope)
 **/
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
        var apiUrl: String = "",
        var autoScan: Boolean = false
    )

    private var state = State()

    override fun getState(): State = state
    override fun loadState(state: State) {
        this.state = state
    }

    init {
        if (state.apiUrl.isBlank()) {
            state.apiUrl = "https://api.xygeni.io"
        }
    }

    companion object {
        private const val TOKEN_KEY = "XygeniApiToken"

        fun getInstance(): XygeniSettings =
            ApplicationManager.getApplication().getService(XygeniSettings::class.java)

        /**
         * Creates CredentialAttributes for storing API token securely.
         * All five constructor arguments are passed explicitly (platform defaults: no
         * requestor, not memory-only, cache denied items) so Kotlin binds the current
         * primary constructor instead of the deprecated 4-arg one through its synthetic
         * default-args bridge, which the Marketplace verifier flags (#1688).
         */
        private fun createCredentialAttributes() = CredentialAttributes(
            generateServiceName("Xygeni", TOKEN_KEY),
            null,
            null,
            false,
            true
        )
    }

    var apiUrl: String
        get() = state.apiUrl
        set(value) {
            state.apiUrl = value
        }

    var autoScan: Boolean
        get() = state.autoScan
        set(value) {
            state.autoScan = value
        }

    // --------------------
    // Token (Password safe)
    // --------------------

    private var cachedToken: String? = null
    var apiToken: String
        get() {
            cachedToken?.let { return it }
            if (ApplicationManager.getApplication().isHeadlessEnvironment) {
                return ""
            }
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


    fun getScannerDownloadUrl(): String {
        return if (apiUrl.contains("api.xygeni.io")) {
            "https://get.xygeni.io/latest/scanner/xygeni_scanner.zip"
        } else {
            "${apiUrl}/scan/releases/"
        }
    }

    fun getMcpDownloadUrl(): String {
        return "https://get.xygeni.io/latest/mcp-server/xygeni-mcp-server.jar"
    }


}