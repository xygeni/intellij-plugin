package com.github.xygeni.intellij.views

import com.github.xygeni.intellij.logger.Logger


/**
 * HelpBlockView
 *
 * @author : Carmendelope
 * @version : 8/10/25 (Carmendelope)
 **/

class HelpBlockView () :
    CollapsibleBlockView(HEADER_TEXT, CONTENT_TEXT) {

    /* Implementar si necesitamos capturar algún evento
    override fun onAttach() {
        contentPane.addMouseListener(object : java.awt.event.MouseAdapter() {
            override fun mouseClicked(e: java.awt.event.MouseEvent?) {
                // Logger.log("Clic en el bloque de ayuda general")
            }
        })
    }
     */
}

const val HEADER_TEXT = "XYGENI DOCS & HELP"
const val CONTENT_TEXT= "<p>For more information, visit <a href=\"https://docs.xygeni.io\">https://docs.xygeni.io</a>" +
        "    <br> <br>" +
        "    See Xygeni output channel for more details. Show Output" +
        "    <br> <br>" +
        "    © 2025 Xygeni Security - All rights reserved" +
        "    </p></body></html>"

