package com.github.xygeni.intellij.services

import com.github.xygeni.intellij.logger.Logger
import com.intellij.openapi.project.Project
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.io.File
import java.io.IOException

/**
 * Per-project scan configuration (xygeni/xygeni-product-backlog#415): which scan types the full scan and the
 * on-save incremental scan run, and the folders excluded from both. Saved in `.xygeni/ide.json` at the project
 * root, the same file the other Xygeni IDE plugins read, so a team can commit it and share it.
 *
 * A missing file or key means the defaults, which are what the plugins always did: every scan type in the full
 * scan, the file-scoped ones on save, nothing excluded.
 */
data class ScanConfig(
    /** Scan types of the full scan, in CLI order. Never empty. */
    val scans: List<String>,
    /** Scan types of the on-save incremental scan: a subset of [scans] among the file-scoped types. */
    val onSave: List<String>,
    /** Glob patterns (scanner syntax, `**` at any depth) added to the scanner's default excludes. */
    val excludes: List<String>,
    /** Extra `xygeni scan` options in canonical form (`--name=value` / `--name`), only the ones in [ScanOptions.SPECS]. */
    val options: List<String> = emptyList()
) {
    val isDefault: Boolean
        get() = scans.toSet() == DEFAULT_FULL_SCAN_TYPES.toSet() && onSave.toSet() == DEFAULT_ON_SAVE_SCAN_TYPES.toSet() &&
            excludes.isEmpty() && options.isEmpty()

    fun withScanType(type: String, enabled: Boolean): ScanConfig =
        normalize(scans = if (enabled) scans + type else scans - type, onSave = onSave, excludes = excludes, options = options)

    fun withOnSaveType(type: String, enabled: Boolean): ScanConfig =
        normalize(scans = scans, onSave = if (enabled) onSave + type else onSave - type, excludes = excludes, options = options)

    /** The default on-save types (within the full scan): what Auto Scan on Save runs when nothing was chosen. */
    fun withDefaultOnSave(): ScanConfig = normalize(scans, DEFAULT_ON_SAVE_SCAN_TYPES, excludes, options)

    fun withAllScanTypes(enabled: Boolean): ScanConfig =
        normalize(scans = if (enabled) ALL_SCAN_TYPES else emptyList(), onSave = onSave, excludes = excludes, options = options)

    fun withExclude(pattern: String): ScanConfig = normalize(scans, onSave, excludes + pattern, options)

    fun withoutExclude(pattern: String): ScanConfig = normalize(scans, onSave, excludes - pattern, options)

    /** Adds (or replaces, same option) one canonical scan option. */
    fun withScanOption(option: String): ScanConfig {
        val name = ScanOptions.nameOf(option)
        return normalize(scans, onSave, excludes, options.filter { ScanOptions.nameOf(it) != name } + option)
    }

    /** Removes the option with that name (`--ai-review`), whatever its value. */
    fun withoutScanOption(name: String): ScanConfig =
        normalize(scans, onSave, excludes, options.filter { ScanOptions.nameOf(it) != name })

    /**
     * The `--exclude-add` value (patterns comma-separated), or null without excludes. `--exclude-add` keeps the
     * scanner's default excludes (node_modules, target, dist…); `-e/--exclude` would replace them (scanner 6.23+, #2324).
     */
    fun excludeAddValue(): String? = excludes.takeIf { it.isNotEmpty() }?.joinToString(",")

    /**
     * The file content. The file is shared by the four plugins and committed: the keys of [existing] this version does
     * not know (added by another plugin or a later version) are kept, only the four known ones are replaced.
     */
    fun toJson(existing: String? = null): String {
        val others = try {
            existing?.takeIf { it.isNotBlank() }?.let { Json.parseToJsonElement(it).jsonObject }?.filterKeys { it !in KNOWN_KEYS }
        } catch (e: Exception) {
            null
        }.orEmpty()
        return buildJsonObject {
            put("scans", JsonArray(scans.map { JsonPrimitive(it) }))
            put("onSave", JsonArray(onSave.map { JsonPrimitive(it) }))
            put("excludes", JsonArray(excludes.map { JsonPrimitive(it) }))
            put("options", JsonArray(options.map { JsonPrimitive(it) }))
            others.forEach { (key, value) -> put(key, value) }
        }.let { PRETTY_JSON.encodeToString(JsonObject.serializer(), it) } + "\n"
    }

    /** One row per switch of the Scan configuration section, in the order shown. */
    class Scanner(
        /** The `--run=` name the scanner CLI accepts. */
        val key: String,
        val label: String,
        val description: String,
        /** With `--incremental` only these scan the changed files; the rest would scan the whole project on every save. */
        val onSave: Boolean,
        /** Runs with another scanner and has no switch of its own (suspect deps need the dependencies). */
        val runsWith: String? = null
    )

    companion object {
        const val CONFIG_DIR = ".xygeni"
        const val CONFIG_FILE = "ide.json"

        /** The keys this version reads and writes; any other key in the file is kept as it is. */
        val KNOWN_KEYS = setOf("scans", "onSave", "excludes", "options")

        val SCANNERS: List<Scanner> = listOf(
            Scanner("secrets", "Secrets", "Hardcoded credentials and tokens", onSave = true),
            Scanner("sast", "SAST", "Vulnerabilities in your own code", onSave = true),
            Scanner("deps", "SCA", "Vulnerable open source dependencies (also suspect dependencies)", onSave = false),
            Scanner("iac", "IaC", "Terraform, Kubernetes and CloudFormation flaws", onSave = true),
            Scanner("misconf", "CI/CD misconfigurations", "Pipeline and SCM settings", onSave = false),
            Scanner("malware", "Malware", "Malicious and suspicious packages", onSave = true),
            Scanner("quality", "Code Quality", "Maintainability and reliability issues", onSave = true),
            Scanner("apisec", "API Security", "Vulnerabilities in your APIs", onSave = false),
            Scanner("ai", "AI Security", "OWASP LLM Top 10 risks in your AI code", onSave = false),
            Scanner("suspectdeps", "Suspect dependencies", "Runs with SCA", onSave = false, runsWith = "deps"),
        )

        /** The scanners with a switch of their own. */
        val SWITCHABLE_SCANNERS: List<Scanner> = SCANNERS.filter { it.runsWith == null }

        /** The scan types `--incremental` can restrict to the changed files. */
        val ON_SAVE_SCANNERS: List<Scanner> = SCANNERS.filter { it.onSave }

        /** Every scan type, in the order the `--run=` list always had (the UI order above is a different one). */
        val ALL_SCAN_TYPES: List<String> = listOf("deps", "secrets", "misconf", "iac", "suspectdeps", "sast", "malware", "quality", "apisec", "ai")

        /** What the plugins always ran: every type in the full scan, secrets / IaC / SAST / malware on save. */
        val DEFAULT_FULL_SCAN_TYPES: List<String> = ALL_SCAN_TYPES
        val DEFAULT_ON_SAVE_SCAN_TYPES: List<String> = listOf("secrets", "iac", "sast", "malware")

        private val PRETTY_JSON = Json { prettyPrint = true; prettyPrintIndent = "  " }

        fun defaults(): ScanConfig = ScanConfig(DEFAULT_FULL_SCAN_TYPES, DEFAULT_ON_SAVE_SCAN_TYPES, emptyList())

        /**
         * Makes a configuration consistent, whatever it was read from: unknown types dropped, CLI order kept, a
         * scanner that runs with another follows it, on-save limited to the file-scoped types that are also in the
         * full scan, and an empty full scan falls back to the defaults (the scanner needs at least one type).
         */
        fun normalize(scans: List<String>?, onSave: List<String>?, excludes: List<String>?, options: List<String>? = null): ScanConfig {
            val requested = (scans ?: DEFAULT_FULL_SCAN_TYPES).filter { it in ALL_SCAN_TYPES }.toMutableSet()
            for (scanner in SCANNERS) {
                val parent = scanner.runsWith ?: continue
                if (parent in requested) requested.add(scanner.key) else requested.remove(scanner.key)
            }
            val effectiveScans = ALL_SCAN_TYPES.filter { it in requested }.ifEmpty { DEFAULT_FULL_SCAN_TYPES }
            val requestedOnSave = (onSave ?: DEFAULT_ON_SAVE_SCAN_TYPES).toSet()
            val onSaveTypes = ON_SAVE_SCANNERS.map { it.key }
            val effectiveOnSave = ALL_SCAN_TYPES.filter { it in requestedOnSave && it in onSaveTypes && it in effectiveScans }
            // A quote or control character could end the argument on Windows (ScanOptions.hasUnsafeChars): dropped.
            val effectiveExcludes = (excludes ?: emptyList()).map { it.trim() }.filter { it.isNotEmpty() && !ScanOptions.hasUnsafeChars(it) }.distinct()
            return ScanConfig(effectiveScans, effectiveOnSave, effectiveExcludes, ScanOptions.normalize(options))
        }

        /** Parses the file content; invalid JSON or a non-object gives the defaults. */
        fun parse(content: String?): ScanConfig {
            if (content.isNullOrBlank()) return defaults()
            return try {
                val root = Json.parseToJsonElement(content).jsonObject
                normalize(root.stringList("scans"), root.stringList("onSave"), root.stringList("excludes"), root.stringList("options"))
            } catch (e: Exception) {
                defaults()
            }
        }

        /** A missing key is null (defaults); a key that is not an array of strings is an empty list. */
        private fun JsonObject.stringList(key: String): List<String>? {
            val element = this[key] ?: return null
            return try {
                element.jsonArray.mapNotNull { item -> (item as? JsonPrimitive)?.takeIf { it.isString }?.jsonPrimitive?.content }
            } catch (e: Exception) {
                emptyList()
            }
        }

        /**
         * Refuses to write through a link: a repository can commit `.xygeni` or `ide.json` as a symbolic link, and a
         * switch in the IDE would then overwrite the file it points to (`~/.bashrc`…), outside the project.
         */
        fun checkNotLinked(file: File) {
            for (path in listOf(file.parentFile.toPath(), file.toPath())) {
                if (java.nio.file.Files.isSymbolicLink(path)) throw IOException("$path $LINKED")
            }
        }

        private const val LINKED = "is a symbolic link: not written, so a change cannot land outside the project"

        fun file(project: Project): File? = project.basePath?.let { File(File(it, CONFIG_DIR), CONFIG_FILE) }

        /**
         * The exclude pattern of a folder inside the project: its path relative to the root, forward slashes, plus a
         * trailing slash and a double star (everything under it). Null for the root itself or a folder outside it.
         */
        fun excludePatternForFolder(projectRoot: File, folder: File): String? {
            val relative = try {
                projectRoot.canonicalFile.toPath().relativize(folder.canonicalFile.toPath())
            } catch (e: IllegalArgumentException) {
                return null
            }
            val text = relative.toString().replace(File.separatorChar, '/')
            if (text.isEmpty() || text == "." || text.startsWith("..") || relative.isAbsolute) return null
            return "$text/**"
        }

        /** The configuration of the project: the file when it exists, the defaults otherwise. */
        fun load(project: Project): ScanConfig {
            val file = file(project) ?: return defaults()
            return try {
                parse(if (file.exists()) file.readText() else null)
            } catch (e: IOException) {
                defaults()
            }
        }

        /**
         * Writes the configuration, keeping the keys of the file this version does not know. The defaults do not create
         * the file (an untouched project leaves nothing behind), but an existing one is rewritten, never deleted: it is
         * usually committed, and "Restore defaults" must not show up as a deleted file.
         */
        fun save(project: Project, config: ScanConfig) {
            val file = file(project) ?: return
            val normalized = normalize(config.scans, config.onSave, config.excludes, config.options)
            try {
                checkNotLinked(file)
                if (normalized.isDefault && !file.exists()) return
                val existing = if (file.exists()) file.readText() else null
                file.parentFile.mkdirs()
                file.writeText(normalized.toJson(existing))
            } catch (e: IOException) {
                Logger.error("Could not save the scan configuration to $file", e, project)
                com.github.xygeni.intellij.notifications.NotificationService.notifyError("Could not save the Xygeni scan configuration: ${e.message}", project)
            }
        }
    }
}
