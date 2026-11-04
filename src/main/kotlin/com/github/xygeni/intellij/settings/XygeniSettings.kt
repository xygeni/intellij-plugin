package com.github.xygeni.intellij.settings

/**
 * XygeniSettings
 *
 * @author : Carmendelope
 * @version : 7/10/25 (Carmendelope)
 **/
import com.github.xygeni.intellij.services.ScannerGlobalOptions
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
        var autoScan: Boolean = false,
        // Scanner global options (xygeni/tech-support#378)
        var skipSslVerify: Boolean = false,
        var skipUpdate: Boolean = false,
        var verbose: Boolean = false,
        var additionalGlobalOptions: String = "",
        // MCP server updates (xygeni/xygeni-product-backlog#2382): notify (default), auto or off
        var mcpUpdateMode: String = McpUpdateMode.NOTIFY.value,
        // A version the user chose to ignore in the update notification; empty when none
        var mcpIgnoredVersion: String = "",
        // Version installed by an update that the AI assistant has not loaded yet; empty once the user says OK
        var mcpRestartPending: String = ""
    )

    private var state = State()

    override fun getState(): State = state
    override fun loadState(state: State) {
        // Re-normalise values persisted by older versions (#1976).
        this.state = state.copy(apiUrl = normalizeApiUrl(state.apiUrl))
    }

    init {
        if (state.apiUrl.isBlank()) {
            state.apiUrl = "https://api.xygeni.io"
        }
    }

    companion object {
        private const val TOKEN_KEY = "XygeniApiToken"
        private const val MCP_SERVER_BASE_URL = "https://get.xygeni.io/latest/mcp-server"

        fun getInstance(): XygeniSettings =
            ApplicationManager.getApplication().getService(XygeniSettings::class.java)

        /** The API URL is concatenated as `"$apiUrl/scan/releases/"` etc.; a pasted trailing slash
         *  produced `//scan/releases/`, which the backend answers with 401 (#1976). */
        fun normalizeApiUrl(raw: String): String = raw.trim().trimEnd('/')

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
            state.apiUrl = normalizeApiUrl(value)
        }

    var autoScan: Boolean
        get() = state.autoScan
        set(value) {
            state.autoScan = value
        }

    var skipSslVerify: Boolean
        get() = state.skipSslVerify
        set(value) {
            state.skipSslVerify = value
        }

    var skipUpdate: Boolean
        get() = state.skipUpdate
        set(value) {
            state.skipUpdate = value
        }

    var verbose: Boolean
        get() = state.verbose
        set(value) {
            state.verbose = value
        }

    var additionalGlobalOptions: String
        get() = state.additionalGlobalOptions
        set(value) {
            state.additionalGlobalOptions = value.trim()
        }

    var mcpUpdateMode: McpUpdateMode
        get() = McpUpdateMode.fromValue(state.mcpUpdateMode)
        set(value) {
            state.mcpUpdateMode = value.value
        }

    var mcpIgnoredVersion: String
        get() = state.mcpIgnoredVersion
        set(value) {
            state.mcpIgnoredVersion = value.trim()
        }

    /** Set after an MCP server update until the user confirms the AI assistant was restarted (#2382). */
    var mcpRestartPending: String?
        get() = state.mcpRestartPending.ifBlank { null }
        set(value) {
            state.mcpRestartPending = value?.trim().orEmpty()
        }

    /** The options placed before the scanner command (`xygeni <options> scan ...`). */
    fun scannerGlobalOptions(): List<String> {
        val enabled = listOfNotNull(
            ScannerGlobalOptions.SKIP_SSL_VERIFY.takeIf { skipSslVerify },
            ScannerGlobalOptions.SKIP_UPDATE.takeIf { skipUpdate },
            ScannerGlobalOptions.VERBOSE.takeIf { verbose }
        )
        return ScannerGlobalOptions.build(enabled, additionalGlobalOptions)
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

    fun getMcpDownloadUrl(): String = "$MCP_SERVER_BASE_URL/xygeni-mcp-server.jar"

    /** `<hash>  xygeni-mcp-server.jar`, published next to the JAR (xygeni/xygeni-product-backlog#2381). */
    fun getMcpChecksumUrl(): String = "$MCP_SERVER_BASE_URL/xygeni-mcp-server.jar.sha256"

    /** The version of the published JAR on a single line (xygeni/xygeni-product-backlog#2381). */
    fun getMcpVersionUrl(): String = "$MCP_SERVER_BASE_URL/version.txt"


}
/** How the plugin reacts to a newer MCP server being published (xygeni/xygeni-product-backlog#2382). */
enum class McpUpdateMode(val value: String, val label: String) {
    /** Notify with Update / Ignore this version actions. */
    NOTIFY("notify", "Notify me"),
    /** Update in the background and notify when done. */
    AUTO("auto", "Update automatically"),
    /** Never check. The manual "Check for MCP Server Updates" action still works. */
    OFF("off", "Off");

    override fun toString(): String = label

    companion object {
        fun fromValue(value: String?): McpUpdateMode = entries.firstOrNull { it.value == value } ?: NOTIFY
    }
}
