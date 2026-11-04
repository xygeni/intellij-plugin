package com.github.xygeni.intellij.services

/**
 * Version strings of the Xygeni MCP server (xygeni/xygeni-product-backlog#2382).
 *
 * The published `version.txt` holds the JAR version on a single line (`1.2.2`, no `v` prefix); the installed
 * one is kept in `~/.xygeni/mcp/version.txt`. A missing or unparseable installed version counts as outdated,
 * so a JAR installed before the version file existed is updated once.
 */
object McpVersion {

    private val VERSION_PATTERN = Regex("^v?(\\d+(?:\\.\\d+)*)(?:[-+].*)?$")
    private val SHA256_PATTERN = Regex("^[0-9a-fA-F]{64}$")

    /** `1.2.3` -> [1, 2, 3]. A `v` prefix and a `-SNAPSHOT` / `+build` suffix are ignored. Null when not a version. */
    fun parse(raw: String?): List<Int>? {
        val match = VERSION_PATTERN.find(raw?.trim() ?: return null) ?: return null
        return match.groupValues[1].split('.').map { it.toIntOrNull() ?: return null }
    }

    /** Numeric, segment by segment; a missing segment counts as 0 (`1.2` == `1.2.0`). Unparseable sorts first. */
    fun compare(a: String?, b: String?): Int {
        val left = parse(a)
        val right = parse(b)
        if (left == null || right == null) return (left != null).compareTo(right != null)
        for (i in 0 until maxOf(left.size, right.size)) {
            val diff = left.getOrElse(i) { 0 }.compareTo(right.getOrElse(i) { 0 })
            if (diff != 0) return diff
        }
        return 0
    }

    /** True when `remote` is a version newer than `local`; an unknown or invalid `local` is always outdated. */
    fun isNewer(remote: String?, local: String?): Boolean {
        if (parse(remote) == null) return false
        if (parse(local) == null) return true
        return compare(remote, local) > 0
    }

    /** The version in a `version.txt` body: first non-blank line, trimmed, without the `v` prefix. Null when invalid. */
    fun fromVersionFile(content: String?): String? {
        val line = content?.lineSequence()?.map { it.trim() }?.firstOrNull { it.isNotEmpty() } ?: return null
        return if (parse(line) != null) line.removePrefix("v") else null
    }

    /** The hash in a `sha256sum`-style body (`<hash>  xygeni-mcp-server.jar`), lowercase. Null when it is not one. */
    fun fromChecksumFile(content: String?): String? {
        val token = content?.trim()?.split(Regex("\\s+"))?.firstOrNull() ?: return null
        return if (SHA256_PATTERN.matches(token)) token.lowercase() else null
    }
}
