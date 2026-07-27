package com.github.xygeni.intellij.services

import com.github.xygeni.intellij.events.LICENSE_STATE_TOPIC
import com.github.xygeni.intellij.logger.Logger
import com.github.xygeni.intellij.settings.XygeniSettings
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.net.NetworkInterface
import java.security.MessageDigest
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Registers / releases the IDE seat against Xygeni's
 * `/internal/license/ideaccess` endpoint, mirroring the VS Code flow:
 *
 *  - On startup (after token is validated) POSTs the machine fingerprint
 *    to `/internal/license/ideaccess`. A 200 whose body is `true` means the
 *    seat is granted; a `false` body (also 200) denies it.
 *  - On plugin/app shutdown POSTs to `/internal/license/ideaccess/uninstall`
 *    to release the seat.
 *
 * The fingerprint is `SHA-256(hostname | primaryMac | platform | arch)` and
 * is persisted at `~/.xygeni/fingerprint.dat` so the same identifier is
 * reused across launches.
 */
@Service(Service.Level.APP)
class LicenseService : Disposable {

    @Serializable
    data class MachineFingerprint(
        val hostname: String,
        val platform: String,
        val mac: String,
        val fingerprint: String
    )

    @Serializable
    private data class LicenseStatePlan(val licenseType: String? = null)

    @Serializable
    private data class LicenseState(val dataLicensePlan: LicenseStatePlan? = null)

    private val client = OkHttpClient()
    private val json = Json { prettyPrint = false; encodeDefaults = true; ignoreUnknownKeys = true }
    private val valid = AtomicBoolean(false)
    private val free = AtomicBoolean(false)

    fun isLicenseValid(): Boolean = valid.get()

    /**
     * True when the installed license is a Free edition. Auto Scan on Save relies on
     * `xygeni scan --incremental`, which the Free edition of the scanner CLI rejects, so the
     * feature must be disabled for Free licenses. Resolved from `GET /license/state` after the
     * IDE seat is registered; defaults to `false` ("unknown — assume non-Free") until resolved.
     */
    fun isFreeLicense(): Boolean = free.get()

    /**
     * Builds (or loads) the machine fingerprint and registers the IDE seat.
     * Updates [isLicenseValid] with the response.
     */
    fun register(project: Project? = null) {
        // Network + PasswordSafe access must run off the EDT. Callers reach this method through
        // validateConnection callbacks that are dispatched on the EDT via invokeLater, so dispatch
        // the actual work to a pooled thread; otherwise the synchronous POST is an illegal slow
        // operation on the EDT and aborts before the license state is updated.
        ApplicationManager.getApplication().executeOnPooledThread {
            val settings = XygeniSettings.getInstance()
            val apiUrl = settings.apiUrl
            val token = settings.apiToken
            if (apiUrl.isBlank() || token.isBlank()) {
                Logger.log("License check skipped — missing API URL or token", project)
                updateState(project, false)
                return@executeOnPooledThread
            }

            val fingerprint = loadOrCreateFingerprint()
            val body = json.encodeToString(fingerprint)
            val ok = post("$apiUrl/internal/license/ideaccess", body, token, project)
            // Resolve the license plan only when the seat is valid; otherwise assume non-Free so
            // the gating UI does not flip to "Free" on a transient failure. Done before
            // updateState() publishes the topic so subscribers can read isFreeLicense() right away.
            free.set(if (ok) fetchIsFreeLicense(apiUrl, token, project) else false)
            updateState(project, ok)
            Logger.log(if (ok) "✅ Xygeni IDE License registered" else "❌ Xygeni IDE License denied", project)
        }
    }

    private fun updateState(project: Project?, ok: Boolean) {
        valid.set(ok)
        ApplicationManager.getApplication().invokeLater {
            ApplicationManager.getApplication().messageBus
                .syncPublisher(LICENSE_STATE_TOPIC)
                .licenseStateChanged(project, ok)
        }
    }

    override fun dispose() {
        uninstall()
    }

    private fun uninstall() {
        val settings = XygeniSettings.getInstance()
        val apiUrl = settings.apiUrl
        if (apiUrl.isBlank()) return
        val file = fingerprintFile()
        if (!file.exists()) return
        try {
            val body = file.readText()
            val ok = post("$apiUrl/internal/license/ideaccess/uninstall", body, settings.apiToken, null)
            Logger.log(if (ok) "✅ Xygeni IDE License released" else "⚠️ Xygeni IDE License release not confirmed")
        } catch (e: Exception) {
            Logger.warn("Failed to release Xygeni IDE License: ${e.message}")
        } finally {
            valid.set(false)
        }
    }

    private fun post(url: String, body: String, token: String, project: Project?): Boolean {
        return try {
            val builder = Request.Builder()
                .url(url)
                .post(body.toRequestBody("application/json".toMediaType()))
                .addHeader("Content-Type", "application/json")
            if (token.isNotBlank()) {
                builder.addHeader("Authorization", "Bearer $token")
            }
            client.newCall(builder.build()).execute().use { response ->
                if (!response.isSuccessful) {
                    Logger.log("License endpoint ${url.substringAfter("/internal/")} responded ${response.code}", project)
                    false
                } else {
                    // The endpoint returns a JSON boolean: `true` grants the IDE seat, `false`
                    // denies it (e.g. no seats available). A 200 status alone does NOT mean the
                    // seat is valid, so the response body must be read.
                    val payload = response.body?.string()?.trim()
                    payload.equals("true", ignoreCase = true)
                }
            }
        } catch (e: Exception) {
            Logger.warn("License call to $url failed: ${e.message}")
            false
        }
    }

    /**
     * Fetches the license plan from `GET /license/state` and reports whether it is a Free edition.
     * Never throws: any error resolves to `false` ("unknown — assume non-Free").
     */
    private fun fetchIsFreeLicense(apiUrl: String, token: String, project: Project?): Boolean {
        return try {
            val builder = Request.Builder()
                .url("$apiUrl/license/state")
                .get()
            if (token.isNotBlank()) {
                builder.addHeader("Authorization", "Bearer $token")
            }
            client.newCall(builder.build()).execute().use { response ->
                if (response.code != 200) {
                    Logger.log("License state endpoint responded ${response.code}", project)
                    return false
                }
                val payload = response.body?.string() ?: return false
                val state = json.decodeFromString<LicenseState>(payload)
                state.dataLicensePlan?.licenseType?.equals(FREE_LICENSE_TYPE, ignoreCase = true) == true
            }
        } catch (e: Exception) {
            Logger.warn("License state call failed: ${e.message}")
            false
        }
    }

    private fun loadOrCreateFingerprint(): MachineFingerprint {
        val file = fingerprintFile()
        if (file.exists()) {
            try {
                return json.decodeFromString(file.readText())
            } catch (e: Exception) {
                Logger.warn("Corrupt fingerprint file, regenerating: ${e.message}")
            }
        }
        val fresh = generateFingerprint()
        try {
            file.parentFile?.mkdirs()
            file.writeText(json.encodeToString(fresh))
        } catch (e: Exception) {
            Logger.warn("Could not persist fingerprint file: ${e.message}")
        }
        return fresh
    }

    private fun generateFingerprint(): MachineFingerprint {
        val hostname = runCatching { java.net.InetAddress.getLocalHost().hostName }.getOrDefault("unknown")
        val mac = primaryMac() ?: ""
        val platform = System.getProperty("os.name") ?: "unknown"
        val arch = System.getProperty("os.arch") ?: "unknown"
        val raw = listOf(hostname, mac, platform, arch).joinToString("|")
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(raw.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
        return MachineFingerprint(hostname, platform, mac, digest)
    }

    private fun primaryMac(): String? {
        return try {
            NetworkInterface.getNetworkInterfaces().asSequence()
                .filter { it.isUp && !it.isLoopback && !it.isVirtual }
                .mapNotNull { it.hardwareAddress }
                .firstOrNull { it.isNotEmpty() && it.any { b -> b.toInt() != 0 } }
                ?.joinToString(":") { "%02x".format(it) }
        } catch (e: Exception) {
            null
        }
    }

    private fun fingerprintFile(): File =
        File(System.getProperty("user.home"), ".xygeni/fingerprint.dat")

    companion object {
        private const val FREE_LICENSE_TYPE = "free"

        /** Xygeni pricing page opened by the "Upgrade" action when on a Free license. */
        const val PRICING_URL = "https://xygeni.io/pricing/"

        fun getInstance(): LicenseService =
            ApplicationManager.getApplication().getService(LicenseService::class.java)
    }
}
