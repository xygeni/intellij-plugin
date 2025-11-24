package com.github.xygeni.intellij.events

/**
 * SettingsEvents
 *
 * @author : Carmendelope
 * @version : 8/10/25 (Carmendelope)
 **/

import com.intellij.openapi.project.Project
import com.intellij.util.messages.Topic
// --------------------- //
// -- Settings change -- //
// --------------------- //
val SETTINGS_CHANGED_TOPIC = Topic.create(
    "Xygeni Settings Changed",
    SettingsChangeListener::class.java
)
interface SettingsChangeListener {
    fun settingsChanged()
}

// ---------------------- //
// -- Connection state -- //
// ---------------------- //
val CONNECTION_STATE_TOPIC = Topic.create(
    "Xygeni Connection State",
    ConnectionStateListener::class.java
)
interface ConnectionStateListener {
    fun connectionStateChanged(project: Project?, urlOk: Boolean, tokenOk: Boolean)
}

// ---------------- //
// -- Scan state -- //
// ---------------- //
val SCAN_STATE_TOPIC = Topic.create(
    "Xygeni Scan State",
    ScanStateListener::class.java
)
// 0: Finished with errors
// 1: Finished successfully
// 2: Running
interface ScanStateListener {
    fun scanStateChanged(project: Project?, status: Int)
}

val READ_TOPIC = Topic.create(
    "Xygeni Read",
    ReadListener::class.java
)
interface ReadListener {
    fun readCompleted(project: Project?, reportType: String?)
}


