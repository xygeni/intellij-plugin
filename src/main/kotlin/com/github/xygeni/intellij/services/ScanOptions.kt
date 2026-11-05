package com.github.xygeni.intellij.services

/**
 * Options of `xygeni scan` the user may add from the IDE (xygeni/xygeni-product-backlog#415, "scanner options").
 *
 * A closed list, not free text: the Scan configuration view offers these with autocompletion and refuses anything
 * else, and the same validation runs when `.xygeni/ide.json` is read, so a hand-edited file cannot smuggle in an
 * option the plugin manages itself (`--run`, `-o`, ...). Taken from ScanCommand and its option groups in
 * DepsDoctor-CLI (`xygeni scan --help`). Kept identical in the four Xygeni IDE plugins.
 */
object ScanOptions {

    enum class Kind { FLAG, ENUM, LIST, TEXT, PATH }

    class Spec(
        /** Long name, with the dashes (`--ai-review`). */
        val name: String,
        val kind: Kind,
        /** One line, as in the CLI help. */
        val description: String,
        /** ENUM: the only values accepted. LIST: suggested items (others are accepted too unless [closed]). */
        val values: List<String> = emptyList(),
        /** LIST: every item must be one of [values]. */
        val closed: Boolean = false,
        val placeholder: String? = null
    )

    private val SEVERITIES = listOf("critical", "high", "low", "info")

    /** The options offered, in the order of the autocompletion. */
    // Only options of `xygeni scan` itself: `--run-languages` / `--skip-languages` belong to `xygeni sast` and `xygeni quality`, and `scan` refuses them with "Unknown option" (exit 2).
    val SPECS: List<Spec> = listOf(
        Spec("--include", Kind.LIST, "Include patterns, comma-separated (glob, ** at any depth)", placeholder = "src/**"),
        Spec("--detectors", Kind.TEXT, "Detectors to include per scan: <scan>=<ids, a severity or all>, | between scans", placeholder = "secrets=high|iac=all"),
        Spec("--skip-detectors", Kind.TEXT, "Detectors to leave out per scan, same syntax as --detectors", placeholder = "sast=hardcoded_password"),
        Spec("--mute", Kind.PATH, "Mute descriptor file (.xygeni.mute.yml) or #name of a named profile", placeholder = ".xygeni.mute.yml"),
        Spec("--standard", Kind.TEXT, "ID of the compliance standard to check", placeholder = "owasp-top10"),
        Spec("--ai-review", Kind.ENUM, "Which findings an AI agent reviews (needs an AI agent configured; scanner 6.23 or later)", listOf("off", "all", "ai-detected")),
        Spec("--ai-review-filter", Kind.LIST, "Findings the AI review looks at: a severity (that one and higher), detector IDs, !<id> to leave one out, or all (scanner 6.23 or later)", SEVERITIES, placeholder = "high,!sql_injection"),
        Spec("--ai-mode", Kind.ENUM, "Convention profile of the application before analyzing it (needs an AI agent configured; scanner 6.23 or later)", listOf("off", "auto", "stored", "rebuild")),
        Spec("--include-license-analysis", Kind.FLAG, "Evaluate the licenses of the components (SCA)"),
        Spec("--conf", Kind.PATH, "Scan configuration file template (the filename is prefixed with SCAN.)", placeholder = "conf/custom.yml"),
        Spec("--custom-detectors-dir", Kind.PATH, "Directory with custom detectors", placeholder = ".xygeni/detectors"),
    )

    /** Options the plugin builds itself or that would break the IDE flow, with the reason shown when typed. */
    val MANAGED: Map<String, String> = mapOf(
        "--run" to "chosen with the scan type switches",
        "--skip" to "chosen with the scan type switches",
        "--incremental" to "set by Scan project on save",
        "-d" to "the project folder is scanned", "--dir" to "the project folder is scanned",
        "-o" to "the report location is managed by the plugin", "--output" to "the report location is managed by the plugin",
        "-f" to "the plugin reads JSON reports", "--format" to "the plugin reads JSON reports",
        "--upload" to "IDE scans are never uploaded", "--no-upload" to "IDE scans are never uploaded",
        "--include-vulnerabilities" to "always set by the plugin",
        "-e" to "replaces the scanner defaults: use Excluded folders instead", "--exclude" to "replaces the scanner defaults: use Excluded folders instead",
        "--exclude-add" to "use Excluded folders", "--exclude-remove" to "use Excluded folders",
        "-n" to "the project is named by the plugin", "--name" to "the project is named by the plugin",
        "--branch" to "the branch is taken from the project",
        "-repo" to "the IDE scans the local project", "--repository" to "the IDE scans the local project",
        "--repo-token" to "the IDE scans the local project", "--repo-branch" to "the IDE scans the local project",
        "--api-key" to "the token comes from the Xygeni settings",
        "--never-fail" to "the plugin reads the exit code", "--fail-on" to "the plugin reads the exit code",
        "--try-all-scans" to "the plugin reads the exit code", "--stop-on-errors" to "the plugin reads the exit code",
        "--secrets-mode" to "internal", "--iac-mode" to "internal", "--sast-mode" to "internal", "--malware-mode" to "internal",
        "-b" to "baselines are a CI concern", "--baseline" to "baselines are a CI concern", "--set-baseline" to "baselines are a CI concern",
        "--auto-remediate" to "opens pull requests: a CI option", "--pr-comment" to "comments on pull requests: a CI option", "--triage" to "a CI option",
    )

    class Parsed(val name: String, val value: String?)

    private val OPTION = Regex("^(-{1,2}[A-Za-z][\\w-]*)(?:\\s*=\\s*|\\s+)?(.*)$", RegexOption.DOT_MATCHES_ALL)

    /** `--name`, `--name=value` or `--name value` (one option per entry); null when it is not an option at all. */
    fun parse(text: String): Parsed? {
        val trimmed = text.trim()
        if (!trimmed.startsWith("-")) return null
        val match = OPTION.find(trimmed) ?: return null
        val rest = match.groupValues[2].trim()
        return Parsed(match.groupValues[1], rest.ifEmpty { null })
    }

    fun spec(name: String): Spec? = SPECS.firstOrNull { it.name == name }

    sealed class Validation {
        /** [option] is the canonical form: `--name=value`, or `--name` for a flag. */
        class Ok(val option: String, val spec: Spec) : Validation()
        class Error(val message: String) : Validation()
    }

    /** Checks one entry. Refuses what is not in [SPECS], saying why when the plugin manages that option itself. */
    fun validate(text: String): Validation {
        val parsed = parse(text) ?: return Validation.Error("An option starts with -- (type -- to see them)")
        MANAGED[parsed.name]?.let { return Validation.Error("${parsed.name} is not available here: $it") }
        val spec = spec(parsed.name) ?: return Validation.Error("${parsed.name} is not an option the IDE can pass to the scanner")
        if (spec.kind == Kind.FLAG) {
            return if (parsed.value == null) Validation.Ok(spec.name, spec) else Validation.Error("${spec.name} takes no value")
        }
        val value = parsed.value?.trim().orEmpty()
        if (value.isEmpty()) {
            val like = spec.placeholder?.let { ", like $it" } ?: ""
            return Validation.Error("${spec.name} needs a value$like")
        }
        if (spec.kind == Kind.ENUM && value !in spec.values) {
            return Validation.Error("${spec.name} is one of ${spec.values.joinToString(", ")}")
        }
        if (spec.kind == Kind.LIST) {
            val items = value.split(',').map { it.trim() }
            if (items.any { it.isEmpty() }) return Validation.Error("${spec.name}: comma-separated values, none empty")
            if (spec.closed) {
                val unknown = items.filter { it !in spec.values }
                if (unknown.isNotEmpty()) return Validation.Error("${spec.name}: ${unknown.joinToString(", ")} not among ${spec.values.joinToString(", ")}")
            }
            return Validation.Ok("${spec.name}=${items.joinToString(",")}", spec)
        }
        if (spec.kind != Kind.PATH && value.any { it.isWhitespace() }) return Validation.Error("${spec.name}: no spaces in the value")
        return Validation.Ok("${spec.name}=$value", spec)
    }

    /** The option name of a canonical entry (`--ai-review=all` → `--ai-review`). */
    fun nameOf(entry: String): String = parse(entry)?.name ?: entry

    /** Keeps the valid entries, in canonical form, one per option (the last one wins), in the order of [SPECS]. */
    fun normalize(entries: List<String>?): List<String> {
        if (entries == null) return emptyList()
        val byName = LinkedHashMap<String, String>()
        for (entry in entries) {
            val result = validate(entry)
            if (result is Validation.Ok) byName[result.spec.name] = result.option
        }
        return SPECS.mapNotNull { byName[it.name] }
    }

    /**
     * What the autocompletion offers for the text typed so far: option names while there is no `=` yet (the
     * whole text replaced by `--name=` or `--name` for a flag), the values of an ENUM / LIST option after it.
     */
    fun suggestions(text: String): List<String> {
        val typed = text.trimStart()
        val parsed = parse(typed)
        val hasSeparator = typed.contains('=') || typed.trim().any { it.isWhitespace() }
        if (parsed == null || !hasSeparator) {
            val prefix = (parsed?.name ?: typed).lowercase()
            return SPECS.filter { prefix.isEmpty() || prefix == "-" || it.name.startsWith(prefix) }
                .map { if (it.kind == Kind.FLAG) it.name else "${it.name}=" }
        }
        val spec = spec(parsed.name) ?: return emptyList()
        if (spec.values.isEmpty()) return emptyList()
        val items = (parsed.value ?: "").split(',')
        val last = items.last().trim().lowercase()
        val head = items.dropLast(1).map { it.trim() }.filter { it.isNotEmpty() }
        val base = spec.name + "=" + (if (head.isEmpty()) "" else head.joinToString(",") + ",")
        return spec.values.filter { it.startsWith(last) && it !in head }.map { base + it }
    }
}
