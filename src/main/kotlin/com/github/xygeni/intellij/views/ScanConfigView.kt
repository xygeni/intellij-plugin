package com.github.xygeni.intellij.views

import com.github.xygeni.intellij.events.INSTALLER_STATE_TOPIC
import com.github.xygeni.intellij.events.InstallerStateListener
import com.github.xygeni.intellij.events.SCAN_CONFIG_CHANGED_TOPIC
import com.github.xygeni.intellij.events.ScanConfigChangeListener
import com.github.xygeni.intellij.settings.XygeniSettings
import com.github.xygeni.intellij.services.InstallerService
import com.github.xygeni.intellij.services.ScanConfig
import com.github.xygeni.intellij.services.ScanOptions
import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.codeInsight.lookup.LookupManager
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.editor.event.DocumentListener
import com.intellij.ui.JBColor
import com.intellij.ui.TextFieldWithAutoCompletion
import com.intellij.ui.TextFieldWithAutoCompletionListProvider
import java.awt.event.KeyAdapter
import java.awt.event.KeyEvent
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.fileChooser.FileChooser
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.ui.Messages
import com.intellij.ui.components.JBList
import com.intellij.ui.components.JBScrollPane
import javax.swing.DefaultListModel
import javax.swing.ListSelectionModel
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.ui.components.ActionLink
import com.intellij.ui.components.JBLabel
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import icons.Icons
import java.awt.Component
import java.io.File
import java.awt.Cursor
import java.awt.Dimension
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.Box
import javax.swing.BoxLayout
import javax.swing.Icon
import javax.swing.JLabel
import javax.swing.JPanel

/**
 * SCAN CONFIGURATION panel (xygeni/xygeni-product-backlog#415): one row per scanner with a "Full scan" and an
 * "On save" checkbox, Select all / Clear, and the `.xygeni/ide.json` the choice is saved in. Same layout and
 * rules as the other Xygeni IDE plugins; the rules live in [ScanConfig].
 */
class ScanConfigView(private val project: Project) : JPanel() {

    private lateinit var header: JLabel
    private lateinit var content: JPanel
    private lateinit var rows: JPanel
    private lateinit var fileLink: ActionLink
    private lateinit var optionsModel: DefaultListModel<String>
    private lateinit var optionsList: JBList<String>
    private lateinit var optionsScroll: JBScrollPane
    private lateinit var noOptionsLabel: JLabel
    private lateinit var removeOptionLink: ActionLink
    private lateinit var addOptionLink: ActionLink
    private lateinit var optionField: TextFieldWithAutoCompletion<String>
    private lateinit var optionHint: JLabel
    private lateinit var excludesModel: DefaultListModel<String>
    private lateinit var excludesList: JBList<String>
    private lateinit var excludesScroll: JBScrollPane
    private lateinit var noExcludesLabel: JLabel
    private lateinit var removeExcludeLink: ActionLink
    private val fullChecks = mutableMapOf<String, ToggleSwitch>()
    private val onSaveChecks = mutableMapOf<String, ToggleSwitch>()

    init {
        layout = BoxLayout(this, BoxLayout.Y_AXIS)
        alignmentX = Component.LEFT_ALIGNMENT
    }

    fun initialize() {
        createHeader()
        createContent()
        add(header)
        add(Box.createVerticalStrut(4).apply { setAlignmentX(0f) })
        add(content)
        refresh()
        applyInstallerState(ApplicationManager.getApplication().getService(InstallerService::class.java).isInstalled())
        project.messageBus.connect()
            .subscribe(SCAN_CONFIG_CHANGED_TOPIC, object : ScanConfigChangeListener {
                override fun scanConfigChanged(project: Project?) {
                    ApplicationManager.getApplication().invokeLater { refresh() }
                }
            })
        ApplicationManager.getApplication().messageBus.connect()
            .subscribe(INSTALLER_STATE_TOPIC, object : InstallerStateListener {
                override fun installerStateChanged(project: Project?, installed: Boolean) {
                    ApplicationManager.getApplication().invokeLater { applyInstallerState(installed) }
                }
            })
    }

    /** The panel only makes sense with a scanner to run: hidden until it is installed. */
    private fun applyInstallerState(installed: Boolean) {
        isVisible = installed
        refreshLayout()
    }

    private fun createHeader() {
        header = Sections.subHeader("Scan configuration") { toggleContentVisibility() }.apply {
            toolTipText = "Which scan types run in this project. Saved in ${ScanConfig.CONFIG_DIR}/${ScanConfig.CONFIG_FILE} " +
                "at the project root, shared with the other Xygeni IDE plugins; commit it to share it with your team."
        }
    }

    private fun createContent() {
        content = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            alignmentX = Component.LEFT_ALIGNMENT
            border = JBUI.Borders.empty(2, Sections.BODY_LEFT, 6, 6)
            isVisible = false
        }

        val actions = linkRow(
            ActionLink("Select all") { save(ScanConfig.load(project).withAllScanTypes(true)) },
            ActionLink("Restore defaults") { save(ScanConfig.defaults()) }
        )

        rows = JPanel(GridBagLayout()).apply { alignmentX = Component.LEFT_ALIGNMENT }
        val constraints = GridBagConstraints().apply { anchor = GridBagConstraints.WEST; insets = JBUI.insets(2, 0, 2, 8) }
        fun header(text: String, column: Int, tooltip: String) {
            constraints.gridx = column; constraints.gridy = 0
            rows.add(JBLabel(text).apply { componentStyle = UIUtil.ComponentStyle.SMALL; fontColor = UIUtil.FontColor.BRIGHTER; toolTipText = tooltip }, constraints)
        }
        header("Scanner", 0, "")
        header("Full scan", 1, "The scan types of Run Scan. With SCA on, suspect dependencies also run. At least one is required.")
        header("On save", 2, "The scan types of Scan project on save. Only the scan types that can analyze just the changed files " +
            "are offered; the others would scan the whole project on every save. A type must be in the full scan to run on save.")

        ScanConfig.SWITCHABLE_SCANNERS.forEachIndexed { index, scanner ->
            constraints.gridy = index + 1
            constraints.gridx = 0
            constraints.weightx = 1.0
            constraints.fill = GridBagConstraints.HORIZONTAL
            rows.add(JPanel(GridBagLayout()).apply {
                isOpaque = false
                val c = GridBagConstraints().apply { anchor = GridBagConstraints.WEST; gridx = 0; gridy = 0; insets = JBUI.insets(0, 0, 0, 6) }
                add(JLabel(iconOf(scanner.key)), c.apply { gridheight = 2 })
                val textConstraints = GridBagConstraints().apply { anchor = GridBagConstraints.WEST; fill = GridBagConstraints.HORIZONTAL; gridx = 1; gridy = 0; weightx = 1.0 }
                add(JLabel(scanner.label), textConstraints)
                add(Sections.description(scanner.description), textConstraints.clone().let { (it as GridBagConstraints).apply { gridy = 1 } })
                toolTipText = scanner.description
                // Give the text column no minimum width: a long description is clipped with "…" rather than
                // pushing the switches out of the tool window.
                minimumSize = Dimension(0, preferredSize.height)
                preferredSize = Dimension(0, preferredSize.height)
            }, constraints)
            constraints.weightx = 0.0
            constraints.fill = GridBagConstraints.NONE
            constraints.gridx = 1
            rows.add(ToggleSwitch().apply {
                toolTipText = "Full scan"
                accessibleContext.accessibleName = "${scanner.label} in the full scan"
                addActionListener { save(ScanConfig.load(project).withScanType(scanner.key, isSelected)) }
                fullChecks[scanner.key] = this
            }, constraints)
            constraints.gridx = 2
            if (scanner.onSave) {
                rows.add(ToggleSwitch().apply {
                    toolTipText = "On save"
                    accessibleContext.accessibleName = "${scanner.label} on save"
                    addActionListener { toggleOnSave(scanner.key, isSelected) }
                    onSaveChecks[scanner.key] = this
                }, constraints)
            } else {
                rows.add(JBLabel("—").apply {
                    fontColor = UIUtil.FontColor.BRIGHTER
                    toolTipText = "${scanner.label} scans the whole project, not just the changed files: not available on save."
                }, constraints)
            }
        }

        fileLink = ActionLink("") { openFile() }.apply { alignmentX = Component.LEFT_ALIGNMENT }

        // Excluded folders: patterns added to the scanner's default excludes with --exclude-add (scanner 6.23+).
        val excludesHeader = JBLabel("Excluded folders").apply {
            font = font.deriveFont(java.awt.Font.BOLD)
            alignmentX = Component.LEFT_ALIGNMENT
            toolTipText = "Folders and patterns excluded from every scan, added to the scanner defaults (node_modules, target, " +
                "dist…). Glob patterns: * file wildcard, ** any depth, \"path:\" prefix for a sub-project. Needs scanner 6.23 or later."
        }
        excludesModel = DefaultListModel()
        excludesList = JBList(excludesModel).apply {
            selectionMode = ListSelectionModel.SINGLE_SELECTION
            visibleRowCount = 3
            addListSelectionListener { removeExcludeLink.isEnabled = selectedValue != null }
        }
        excludesScroll = JBScrollPane(excludesList).apply {
            alignmentX = Component.LEFT_ALIGNMENT
            maximumSize = Dimension(Int.MAX_VALUE, JBUI.scale(64))
            preferredSize = Dimension(JBUI.scale(260), JBUI.scale(56))
            isVisible = false
        }
        noExcludesLabel = Sections.description("None: the scanner defaults apply (node_modules, target, dist…)")
        removeExcludeLink = ActionLink("Remove") {
            excludesList.selectedValue?.let { save(ScanConfig.load(project).withoutExclude(it)) }
        }.apply { isEnabled = false }
        val excludeActions = linkRow(ActionLink("Add folder…") { addExcludeFolder() }, ActionLink("Add pattern…") { addExcludePattern() }, removeExcludeLink)

        content.add(Sections.flush(actions))
        content.add(Box.createVerticalStrut(4))
        content.add(Sections.flush(rows))
        content.add(Box.createVerticalStrut(8))
        content.add(Sections.flush(excludesHeader))
        content.add(Box.createVerticalStrut(2))
        content.add(Sections.flush(noExcludesLabel))
        content.add(Sections.flush(excludesScroll))
        content.add(Sections.flush(excludeActions))
        content.add(Box.createVerticalStrut(8))
        createOptionsSection(content)
        content.add(Box.createVerticalStrut(6))
        content.add(Sections.flush(fileLink))
    }

    /**
     * Scanner options: a command-line-like field with autocompletion and validation (#415). Only the options in
     * [ScanOptions.SPECS] are accepted; the ones the plugin sets itself are refused with the reason.
     */
    private fun createOptionsSection(content: JPanel) {
        val optionsHeader = JBLabel("Scanner options").apply {
            font = font.deriveFont(java.awt.Font.BOLD)
            alignmentX = Component.LEFT_ALIGNMENT
            toolTipText = "Options passed to xygeni scan in the full scan and on save. Type -- to see the ones the IDE can pass; " +
                "the options the plugin sets itself (--run, -o, excludes…) are refused."
        }
        optionsModel = DefaultListModel()
        optionsList = JBList(optionsModel).apply {
            selectionMode = ListSelectionModel.SINGLE_SELECTION
            visibleRowCount = 3
            addListSelectionListener { removeOptionLink.isEnabled = selectedValue != null }
        }
        optionsScroll = JBScrollPane(optionsList).apply {
            alignmentX = Component.LEFT_ALIGNMENT
            maximumSize = Dimension(Int.MAX_VALUE, JBUI.scale(64))
            preferredSize = Dimension(JBUI.scale(260), JBUI.scale(56))
            isVisible = false
        }
        noOptionsLabel = Sections.description("None: the scanner defaults apply")
        removeOptionLink = ActionLink("Remove") {
            optionsList.selectedValue?.let { save(ScanConfig.load(project).withoutScanOption(ScanOptions.nameOf(it))) }
        }.apply { isEnabled = false }

        // The whole text is the completion prefix, so a suggestion replaces it: `--ai-r` → `--ai-review=` → `--ai-review=all`.
        val provider = object : TextFieldWithAutoCompletionListProvider<String>(emptyList()) {
            override fun getLookupString(item: String): String = item
            override fun getPrefix(text: String, offset: Int): String = text.substring(0, offset)
            override fun getItems(prefix: String?, cached: Boolean, parameters: CompletionParameters?): Collection<String> =
                ScanOptions.suggestions(prefix ?: "")
        }
        optionField = TextFieldWithAutoCompletion(project, provider, true, null).apply {
            alignmentX = Component.LEFT_ALIGNMENT
            setPlaceholder("--option=value  (type -- to see them)")
            // The editor behind the field only exists once shown, so its preferred height is 0 while the block is
            // laid out and the row would collapse to a line: give it a fixed height from the start.
            minimumSize = Dimension(JBUI.scale(120), JBUI.scale(28))
            preferredSize = Dimension(JBUI.scale(260), JBUI.scale(28))
            maximumSize = Dimension(Int.MAX_VALUE, JBUI.scale(28))
            addDocumentListener(object : DocumentListener {
                override fun documentChanged(event: DocumentEvent) = validateOption()
            })
            addSettingsProvider { editor ->
                editor.contentComponent.addKeyListener(object : KeyAdapter() {
                    override fun keyPressed(e: KeyEvent) {
                        if (e.keyCode == KeyEvent.VK_ENTER && LookupManager.getActiveLookup(editor) == null) {
                            e.consume()
                            addOption()
                        }
                    }
                })
            }
        }
        addOptionLink = ActionLink("Add") { addOption() }.apply { isEnabled = false }
        val inputRow = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.X_AXIS)
            alignmentX = Component.LEFT_ALIGNMENT
            isOpaque = false
            add(optionField)
            add(Box.createHorizontalStrut(JBUI.scale(8)))
            add(addOptionLink)
            preferredSize = Dimension(JBUI.scale(300), JBUI.scale(32))
            maximumSize = Dimension(Int.MAX_VALUE, JBUI.scale(32))
        }
        optionHint = Sections.description(" ")
        val optionActions = linkRow(removeOptionLink)

        content.add(Sections.flush(optionsHeader))
        content.add(Box.createVerticalStrut(2))
        content.add(Sections.flush(noOptionsLabel))
        content.add(Sections.flush(optionsScroll))
        content.add(Sections.flush(optionActions))
        content.add(Box.createVerticalStrut(4))
        content.add(Sections.flush(inputRow))
        content.add(Sections.flush(optionHint))
    }

    /** Live validation of the typed option: the hint shows the description, or the reason it is refused, in red. */
    private fun validateOption() {
        val text = optionField.text
        if (text.isBlank()) {
            optionHint.text = " "
            optionHint.foreground = UIUtil.getContextHelpForeground()
            addOptionLink.isEnabled = false
            return
        }
        when (val result = ScanOptions.validate(text)) {
            is ScanOptions.Validation.Ok -> {
                optionHint.text = result.spec.description
                optionHint.foreground = UIUtil.getContextHelpForeground()
                addOptionLink.isEnabled = true
            }
            is ScanOptions.Validation.Error -> {
                // An option still waiting for its value is not an error yet.
                val pending = result.message.contains("needs a value")
                optionHint.text = result.message
                optionHint.foreground = if (pending) UIUtil.getContextHelpForeground() else JBColor.RED
                addOptionLink.isEnabled = false
            }
        }
    }

    private fun addOption() {
        when (val result = ScanOptions.validate(optionField.text)) {
            is ScanOptions.Validation.Ok -> {
                save(ScanConfig.load(project).withScanOption(result.option))
                optionField.text = ""
            }
            is ScanOptions.Validation.Error -> {
                optionHint.text = result.message
                optionHint.foreground = JBColor.RED
            }
        }
    }

    /** Picks folders of the project and excludes everything under them (the folder plus a double star). */
    private fun addExcludeFolder() {
        val root = project.basePath?.let { File(it) } ?: return
        val descriptor = FileChooserDescriptorFactory.createMultipleFoldersDescriptor().apply {
            title = "Folders to Exclude from the Xygeni Scan"
            description = "Everything under the chosen folders is left out of every scan"
        }
        val chosen = FileChooser.chooseFiles(descriptor, project, LocalFileSystem.getInstance().findFileByIoFile(root))
        if (chosen.isEmpty()) return
        var config = ScanConfig.load(project)
        val outside = mutableListOf<String>()
        for (folder in chosen) {
            val pattern = ScanConfig.excludePatternForFolder(root, File(folder.path))
            if (pattern != null) config = config.withExclude(pattern) else outside.add(folder.path)
        }
        if (outside.isNotEmpty()) {
            Messages.showWarningDialog(project, "Only folders inside the project can be excluded: ${outside.joinToString(", ")}", "Xygeni")
        }
        save(config)
    }

    /** Types a glob pattern (scanner syntax: `*` file wildcard, `**` any depth, `path:` prefix for a sub-project). */
    private fun addExcludePattern() {
        val pattern = Messages.showInputDialog(
            project,
            "Pattern to exclude from the Xygeni scan (added to the scanner defaults). Example: **/fixtures/**",
            "Exclude a Pattern",
            null
        )?.trim()
        if (pattern.isNullOrEmpty()) return
        if (pattern.contains(',')) {
            Messages.showWarningDialog(project, "One pattern at a time (no commas).", "Xygeni")
            return
        }
        save(ScanConfig.load(project).withExclude(pattern))
    }

    /** Links in a row starting at the block's left edge (a FlowLayout would indent the first one by its gap). */
    private fun linkRow(vararg links: ActionLink): JPanel = JPanel().apply {
        layout = BoxLayout(this, BoxLayout.X_AXIS)
        alignmentX = Component.LEFT_ALIGNMENT
        isOpaque = false
        links.forEachIndexed { index, link ->
            if (index > 0) add(Box.createHorizontalStrut(JBUI.scale(10)))
            add(link)
        }
        add(Box.createHorizontalGlue())
        maximumSize = Dimension(Int.MAX_VALUE, preferredSize.height)
    }

    private fun iconOf(key: String): Icon = when (key) {
        "secrets" -> Icons.SECRET_ICON
        "sast" -> Icons.SAST_ICON
        "deps", "suspectdeps" -> Icons.SCA_ICON
        "iac" -> Icons.IAC_ICON
        "misconf" -> Icons.CICD_ICON
        "malware" -> Icons.MALWARE_ICON
        "quality" -> Icons.QUALITY_ICON
        "apisec" -> Icons.APISEC_ICON
        "ai" -> Icons.AI_ICON
        else -> Icons.XYGENI_ICON
    }

    private fun save(config: ScanConfig) {
        ScanConfig.save(project, config)
        refresh()
    }

    /**
     * The on-save column and "Scan project on save" help each other (#415): switching a type on turns the
     * global check on; switching the last one off turns it off, as nothing would run on save.
     */
    private fun toggleOnSave(type: String, enabled: Boolean) {
        val next = ScanConfig.load(project).withOnSaveType(type, enabled)
        ScanConfig.save(project, next)
        val settings = XygeniSettings.getInstance()
        if (type in next.onSave && !settings.autoScan) settings.autoScan = true
        else if (next.onSave.isEmpty() && settings.autoScan) settings.autoScan = false
        project.messageBus.syncPublisher(SCAN_CONFIG_CHANGED_TOPIC).scanConfigChanged(project)
        refresh()
    }

    /** Reads the file again and paints it: the checkboxes never hold state of their own. */
    fun refresh() {
        val config = ScanConfig.load(project)
        for ((key, check) in fullChecks) check.isSelected = key in config.scans
        val autoScan = XygeniSettings.getInstance().autoScan
        for ((key, check) in onSaveChecks) {
            check.isSelected = key in config.onSave
            check.isEnabled = key in config.scans
            // Dimmed, not disabled, while Scan project on save is off: a click switches it on.
            check.dimmed = !autoScan && key in config.scans
            check.toolTipText = when {
                key !in config.scans -> "Runs on save only while it is in the full scan."
                !autoScan -> "Scan project on save is off: switching this on turns it on."
                else -> "On save"
            }
        }
        optionsModel.clear()
        config.options.forEach { optionsModel.addElement(it) }
        optionsScroll.isVisible = config.options.isNotEmpty()
        optionsScroll.parent?.isVisible = config.options.isNotEmpty()
        noOptionsLabel.isVisible = config.options.isEmpty()
        noOptionsLabel.parent?.isVisible = config.options.isEmpty()
        removeOptionLink.isEnabled = false
        excludesModel.clear()
        config.excludes.forEach { excludesModel.addElement(it) }
        excludesScroll.isVisible = config.excludes.isNotEmpty()
        excludesScroll.parent?.isVisible = config.excludes.isNotEmpty()
        noExcludesLabel.isVisible = config.excludes.isEmpty()
        noExcludesLabel.parent?.isVisible = config.excludes.isEmpty()
        removeExcludeLink.isEnabled = false
        val switched = config.scans.count { it != "suspectdeps" }
        val excluded = if (config.excludes.isEmpty()) "" else ", ${config.excludes.size} excluded"
        val options = if (config.options.isEmpty()) "" else ", ${config.options.size} option${if (config.options.size > 1) "s" else ""}"
        header.text = "Scan configuration  ·  $switched/${ScanConfig.SWITCHABLE_SCANNERS.size} scanners, ${config.onSave.size} on save$excluded$options"
        fileLink.text = if (config.isDefault) "Defaults (nothing written). Open ${ScanConfig.CONFIG_DIR}/${ScanConfig.CONFIG_FILE}"
            else "Saved in ${ScanConfig.CONFIG_DIR}/${ScanConfig.CONFIG_FILE}. Open it"
        refreshLayout()
    }

    /** Opens `.xygeni/ide.json`, writing the current (maybe default) configuration first when it does not exist. */
    private fun openFile() {
        val file = ScanConfig.file(project) ?: return
        if (!file.exists()) {
            file.parentFile.mkdirs()
            file.writeText(ScanConfig.load(project).toJson())
        }
        val virtualFile = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(file) ?: return
        FileEditorManager.getInstance(project).openFile(virtualFile, true)
    }

    private fun toggleContentVisibility() {
        content.isVisible = !content.isVisible
        header.icon = if (content.isVisible) Icons.CHEVRON_DOWN_ICON else Icons.CHEVRON_RIGHT_ICON
        if (content.isVisible) refresh() else refreshLayout()
    }

    /** The panel is capped at its preferred height: recompute it whenever rows are shown or hidden. */
    private fun refreshLayout() {
        revalidate()
        maximumSize = Dimension(Int.MAX_VALUE, preferredSize.height)
        repaint()
    }

}
