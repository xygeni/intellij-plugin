package icons

import com.intellij.openapi.util.IconLoader.getIcon

/**
 * Icons
 *
 * @author : Carmendelope
 * @version : 6/10/25 (Carmendelope)
 **/
object Icons {

    @JvmField val XYGENI_ICON = getIcon("/icons/xygeni.svg", Icons::class.java)

    @JvmField val MESSAGES_ICON = getIcon("/icons/messages.svg", Icons::class.java)
    @JvmField val INSTALL_ICON = getIcon("/icons/install.svg", Icons::class.java)

    @JvmField val CHEVRON_RIGHT_ICON = getIcon("/icons/chevronRight.svg", Icons::class.java)
    @JvmField val CHEVRON_DOWN_ICON = getIcon("/icons/chevronDown.svg", Icons::class.java)

    @JvmField val RUN_ICON = getIcon("/icons/run.svg", Icons::class.java)
    @JvmField val RUN_IN_QUEUE_ICON = getIcon("/icons/runInQueue.svg", Icons::class.java)

    @JvmField val SECRET_ICON = getIcon("/icons/secret.svg", Icons::class.java)
    @JvmField val SAST_ICON = getIcon("/icons/sast.svg", Icons::class.java)
    @JvmField val IAC_ICON = getIcon("/icons/swiftPackage.svg", Icons::class.java)
    @JvmField val CICD_ICON = getIcon("/icons/relay.svg", Icons::class.java)


    @JvmField val CRITICAL_ICON = getIcon("/icons/error.svg", Icons::class.java)
    @JvmField val HIGH_ICON = getIcon("/icons/warning.svg", Icons::class.java)
    @JvmField val LOW_ICON = getIcon("/icons/info.svg", Icons::class.java)
    @JvmField val INFO_ICON = getIcon("/icons/infoOutline.svg", Icons::class.java)

}