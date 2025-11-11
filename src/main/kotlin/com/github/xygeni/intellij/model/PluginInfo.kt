package com.github.xygeni.intellij.model

import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.openapi.extensions.PluginId
import kotlin.lazy

/**
 * PluginInfo
 *
 * @author : Carmendelope
 * @version : 9/10/25 (Carmendelope)
 **/
object PluginInfo {

    private const val PLUGIN_ID = "xygeni"

    private val descriptor
        get() = PluginManagerCore.getPlugin(PluginId.getId(PLUGIN_ID))

    val id: String
        get() = descriptor?.pluginId?.idString ?: "unknown"

    val name: String
        get() = descriptor?.name ?: "unknown"

    val version: String
        get() = descriptor?.version ?: "unknown"
}