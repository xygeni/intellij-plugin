package com.github.xygeni.intellij.views.report

import javax.swing.Icon

/**
 * NodeData
 *
 * @author : Carmendelope
 * @version : 14/10/25 (Carmendelope)
 **/
data class NodeData(
    val text: String,
    val icon: Icon? = null,
    val tooltip: String? = null,
    val onClick: (() -> Unit)? = null
)