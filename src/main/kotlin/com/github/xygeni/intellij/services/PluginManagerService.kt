package com.github.xygeni.intellij.services

import com.github.xygeni.intellij.model.PluginContext
import com.intellij.openapi.components.Service

/**
 * PluginManagerService
 *
 * @author : Carmendelope
 * @version : 10/10/25 (Carmendelope)
 **/
@Service(Service.Level.APP)
class PluginManagerService {
    val pluginContext = PluginContext()
    val executor = DefaultProcessExecutorService()
}