package com.github.xygeni.intellij.dynamichtml.browser

import com.github.xygeni.intellij.logger.Logger
import com.intellij.ui.jcef.JBCefApp

/**
 * JcefSupport
 *
 * Single crash-safe gate for every JCEF usage in the plugin.
 *
 * Since IntelliJ 2026.2 (build 262) JCEF lives in a separate bundled plugin
 * (`com.intellij.modules.jcef`, declared as an optional dependency in plugin.xml),
 * so `com.intellij.ui.jcef.*` classes may be missing from the plugin class loader.
 * The `JBCefApp` reference below is resolved lazily INSIDE the try block, so a
 * `NoClassDefFoundError` degrades to `isAvailable == false` instead of killing
 * the caller (xygeni-product-backlog#1688).
 *
 * Every JCEF entry point must check [isAvailable] BEFORE touching any
 * `com.intellij.ui.jcef.*` type.
 **/
object JcefSupport {
    val isAvailable: Boolean by lazy {
        try {
            JBCefApp.isSupported()
        } catch (throwable: Throwable) {
            Logger.warn("JCEF is not available in this IDE: ${throwable.message}")
            false
        }
    }
}
