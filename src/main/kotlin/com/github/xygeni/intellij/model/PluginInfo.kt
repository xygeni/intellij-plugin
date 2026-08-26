package com.github.xygeni.intellij.model

import com.intellij.ide.plugins.PluginManager
import com.intellij.openapi.extensions.PluginId

/**
 * PluginInfo
 *
 * @author : Carmendelope
 * @version : 9/10/25 (Carmendelope)
 **/
object PluginInfo {

    private const val PLUGIN_ID = "xygeni"

    private val descriptor
        // PluginManagerCore is internal API (flagged by the Marketplace verifier, #1688);
        // PluginManager.findEnabledPlugin is the public replacement.
        get() = PluginManager.getInstance().findEnabledPlugin(PluginId.getId(PLUGIN_ID))

    val id: String
        get() = descriptor?.pluginId?.idString ?: "unknown"

    val name: String
        get() = descriptor?.name ?: "unknown"

    val version: String
        get() = descriptor?.version ?: "unknown"
}