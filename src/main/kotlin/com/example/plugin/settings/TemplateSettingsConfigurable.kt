package com.example.plugin.settings

import com.etrx.codereviewer.model.PromptTemplate
import com.etrx.codereviewer.service.CodeReviewerSettingsService
import com.etrx.codereviewer.util.I18nUtil
import com.intellij.openapi.options.SearchableConfigurable
import com.intellij.openapi.ui.Messages
import com.intellij.ui.ToolbarDecorator
import com.intellij.ui.components.JBList
import com.intellij.ui.components.JBScrollPane
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import java.awt.Component
import javax.swing.DefaultListCellRenderer
import javax.swing.DefaultListModel
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JList
import javax.swing.JPanel
import javax.swing.ListSelectionModel

/**
 * Settings page for "Prompt Templates".
 * Provides a list of templates with a working Remove action.
 */
class TemplateSettingsConfigurable : SearchableConfigurable {

    private var component: JComponent? = null
    private val settings = CodeReviewerSettingsService.getInstance()

    private val listModel = DefaultListModel<PromptTemplate>()
    private val templateList = JBList(listModel).apply {
        selectionMode = ListSelectionModel.SINGLE_SELECTION
        visibleRowCount = 10
        border = JBUI.Borders.empty(6)
        cellRenderer = PromptTemplateListCellRenderer()
    }

    override fun getId(): String = "com.etrx.codereviewer.settings.promptTemplates"

    override fun getDisplayName(): String = I18nUtil.message("template.settings.displayName")

    override fun createComponent(): JComponent {
        if (component == null) {
            reloadTemplates()

            val listPanel = JPanel(BorderLayout()).apply {
                add(JBScrollPane(templateList), BorderLayout.CENTER)
            }

            val decorator = ToolbarDecorator.createDecorator(templateList)
                .disableUpDownActions()
                .setRemoveAction { _ ->
                    onRemoveSelected()
                }
                .setRemoveActionUpdater {
                    val t = templateList.selectedValue
                    // Only allow removing non-default templates
                    t != null && !t.isDefault
                }

            val root = JPanel(BorderLayout())
            root.border = JBUI.Borders.empty(10)
            root.add(decorator.createPanel(), BorderLayout.CENTER)

            component = root
        }
        return component!!
    }

    override fun getPreferredFocusedComponent(): JComponent? = templateList

    override fun isModified(): Boolean = false

    override fun apply() {
        // All actions are applied immediately via the service
    }

    override fun reset() {
        reloadTemplates()
    }

    override fun disposeUIResources() {
        component = null
        listModel.clear()
    }

    private fun reloadTemplates() {
        listModel.clear()
        // Show both default and custom templates; remove allowed only for custom
        val templates = settings.getAvailablePromptTemplates()
        templates.forEach { listModel.addElement(it) }
        if (!listModel.isEmpty) {
            templateList.selectedIndex = 0
        }
    }

    private fun onRemoveSelected() {
        val selected = templateList.selectedValue ?: return
        if (selected.isDefault) {
            // Should be disabled already, but double-guard
            Messages.showInfoMessage(
                templateList,
                I18nUtil.message("message.cannotRemoveDefaultTemplate"),
                I18nUtil.message("dialog.title.removeTemplate")
            )
            return
        }

        val result = Messages.showYesNoDialog(
            templateList,
            I18nUtil.message("message.removeTemplateConfirmation", selected.name),
            I18nUtil.message("dialog.title.removeTemplate"),
            null
        )
        if (result != Messages.YES) return

        // Remove via settings service (also deletes file and resets selection if needed)
        settings.removeCustomPromptTemplate(selected.name)
        reloadTemplates()
    }

    private class PromptTemplateListCellRenderer : DefaultListCellRenderer() {
        override fun getListCellRendererComponent(
            list: JList<*>?,
            value: Any?,
            index: Int,
            isSelected: Boolean,
            cellHasFocus: Boolean
        ): Component {
            val comp = super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus) as JLabel
            val t = value as? PromptTemplate
            if (t != null) {
                comp.text = if (t.isDefault) "${t.name} (built-in)" else t.name
            }
            return comp
        }
    }
}
