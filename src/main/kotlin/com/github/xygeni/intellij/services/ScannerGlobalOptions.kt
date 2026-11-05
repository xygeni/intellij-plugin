package com.github.xygeni.intellij.services

/**
 * Global options of the Xygeni CLI (XygeniScanner.java @Option on the root command, not inherited by
 * subcommands): they must go between the launcher and the command (`xygeni <global> scan ...`), or
 * picocli rejects them as unknown options of `scan`. The launcher script also hands them to the Updater,
 * so --skip-ssl-verify covers the self-update download too. (xygeni/tech-support#378)
 */
object ScannerGlobalOptions {

    const val SKIP_SSL_VERIFY = "--skip-ssl-verify"
    const val SKIP_UPDATE = "--skip-update"
    const val VERBOSE = "--verbose"

    // Never reach the scanner: -q/--quiet hide the console output the plugin reads (licence lines), and the API
    // key must not be on the command line (the token already goes in the environment, from the Xygeni settings).
    private val BLOCKED = setOf("-q", "--quiet")
    private const val API_KEY = "--api-key"

    // What a JVM prints when a TLS-intercepting proxy re-signs the traffic with a CA it does not trust.
    private val CERTIFICATE_ERROR_MARKERS = listOf(
        "PKIX path building failed",
        "unable to find valid certification path",
        "SSLHandshakeException"
    )

    /**
     * Splits the user's option string like a shell would split words, honouring single and double quotes
     * (`-cop "a=b c"` → [-cop, a=b c]). No expansion of any kind: the scanner is started without a shell.
     */
    fun split(text: String?): List<String> {
        val tokens = mutableListOf<String>()
        if (text.isNullOrEmpty()) return tokens
        val current = StringBuilder()
        var inToken = false
        var quote: Char? = null
        for (ch in text) {
            when {
                quote != null -> if (ch == quote) quote = null else current.append(ch)
                ch == '"' || ch == '\'' -> { quote = ch; inToken = true }
                ch.isWhitespace() -> if (inToken) { tokens.add(current.toString()); current.clear(); inToken = false }
                else -> { current.append(ch); inToken = true }
            }
        }
        if (inToken) tokens.add(current.toString())
        return tokens
    }

    /**
     * The global options to put before the CLI command: the checked options first, then the free-text
     * ones, without repeating an option and without the blocked ones.
     */
    fun build(enabledOptions: List<String>, additionalOptions: String?): List<String> {
        val extra = partition(additionalOptions).first
        return enabledOptions.filter { it !in extra } + extra
    }

    /** The options in the free-text setting that are dropped (see BLOCKED). */
    fun blockedIn(additionalOptions: String?): List<String> = partition(additionalOptions).second

    /** The free-text options split into the kept and the blocked ones (BLOCKED; --api-key with its value). */
    private fun partition(additionalOptions: String?): Pair<List<String>, List<String>> {
        val kept = mutableListOf<String>()
        val blocked = mutableListOf<String>()
        val tokens = split(additionalOptions)
        var index = 0
        while (index < tokens.size) {
            val token = tokens[index]
            when {
                token in BLOCKED -> blocked.add(token)
                token == API_KEY -> { blocked.add(token); index++ } // and its value
                token.startsWith("$API_KEY=") -> blocked.add(API_KEY)
                else -> kept.add(token)
            }
            index++
        }
        return kept to blocked
    }

    fun isCertificateError(output: String): Boolean = CERTIFICATE_ERROR_MARKERS.any { output.contains(it) }
}
