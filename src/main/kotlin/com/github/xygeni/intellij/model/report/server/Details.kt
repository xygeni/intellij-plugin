package com.github.xygeni.intellij.model.report.server

import kotlinx.serialization.Serializable

/**
 * component
 *
 * @author : Carmendelope
 * @version : 16/10/25 (Carmendelope)
 **/
@Serializable
data class Component (val group:String, val name:String, val technology:String, val version: String)

@Serializable
data class GavtComponents(val gavComponent: List<Component>)

@Serializable
data class DetectorDetail (val linkDocumentation:String, val descriptionDoc:String)