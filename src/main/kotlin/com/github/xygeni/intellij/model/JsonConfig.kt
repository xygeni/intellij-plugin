package com.github.xygeni.intellij.model

import kotlinx.serialization.json.Json

/**
 * JsonConfig
 *
 * @author : Carmendelope
 * @version : 10/10/25 (Carmendelope)
 **/
object JsonConfig {
    val relaxed = Json {
        ignoreUnknownKeys = true
        prettyPrint = true
    }
}