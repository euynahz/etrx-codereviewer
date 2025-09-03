package com.etrx.codereviewer.ui

import com.etrx.codereviewer.model.AIModelConfig
import com.etrx.codereviewer.model.PromptTemplate
import com.etrx.codereviewer.service.CodeReviewerSettingsService
import com.etrx.codereviewer.service.OllamaReviewService
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.ui.Messages
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBTextField
import com.intellij.util.ui.FormBuilder
import kotlinx.coroutines.runBlocking
import java.awt.BorderLayout
import javax.swing.*

/**
 * Settings configurable for the Code Reviewer plugin
 */
class CodeReviewerConfigurable : Configurable {

    private val settingsService = CodeReviewerSettingsService.getInstance()
    private var mainPanel: JPanel? = null

    // AI Model Configuration Fields
    private val modelNameCombo = JComboBox<String>()
    private val refreshModelsButton = JButton("Refresh")
    private val resetToDefaultsButton = JButton("Reset to Defaults")
    private val endpointField = JBTextField()
    private val apiPathField = JBTextField()
    private val temperatureSpinner = JSpinner(SpinnerNumberModel(0.7, 0.0, 2.0, 0.1))
    private val maxTokensSpinner = JSpinner(SpinnerNumberModel(2048, 1, 8192, 100))
    private val timeoutSpinner = JSpinner(SpinnerNumberModel(30000, 1000, 300000, 1000))
    private val retryCountSpinner = JSpinner(SpinnerNumberModel(3, 0, 10, 1))

    // Prompt Template Fields
    private val promptTemplateCombo = JComboBox<PromptTemplate>()
    private val customPromptArea = JTextArea(10, 50)

    override fun getDisplayName(): String = "Code Reviewer"

    override fun createComponent(): JComponent {
        initializeFields()

        val aiConfigPanel = createAIConfigPanel()
        val promptConfigPanel = createPromptConfigPanel()
        val testPanel = createTestPanel()

        mainPanel = JPanel(BorderLayout()).apply {
            val tabPane = JTabbedPane()
            tabPane.addTab("AI Configuration", aiConfigPanel)
            tabPane.addTab("Prompt Templates", promptConfigPanel)
            tabPane.addTab("Connection Test", testPanel)
            add(tabPane, BorderLayout.CENTER)
        }

        return mainPanel!!
    }

    private fun createAIConfigPanel(): JPanel {
        // Setup model selection panel
        val modelPanel = JPanel(BorderLayout()).apply {
            add(modelNameCombo, BorderLayout.CENTER)
            add(refreshModelsButton, BorderLayout.EAST)
        }

        refreshModelsButton.addActionListener {
            refreshModelList()
        }

        // Setup endpoint panel with reset button
        val endpointPanel = JPanel(BorderLayout()).apply {
            add(endpointField, BorderLayout.CENTER)
            val endpointResetBtn = JButton("Default").apply {
                addActionListener {
                    endpointField.text = "http://192.168.66.181:11434"
                }
            }
            add(endpointResetBtn, BorderLayout.EAST)
        }

        // Setup API path panel with reset button
        val apiPathPanel = JPanel(BorderLayout()).apply {
            add(apiPathField, BorderLayout.CENTER)
            val apiPathResetBtn = JButton("Default").apply {
                addActionListener {
                    apiPathField.text = "/api/generate"
                }
            }
            add(apiPathResetBtn, BorderLayout.EAST)
        }

        // Setup temperature panel with reset button
        val temperaturePanel = JPanel(BorderLayout()).apply {
            add(temperatureSpinner, BorderLayout.CENTER)
            val tempResetBtn = JButton("Default").apply {
                addActionListener {
                    temperatureSpinner.value = 0.7
                }
            }
            add(tempResetBtn, BorderLayout.EAST)
        }

        // Setup max tokens panel with reset button
        val maxTokensPanel = JPanel(BorderLayout()).apply {
            add(maxTokensSpinner, BorderLayout.CENTER)
            val tokensResetBtn = JButton("Default").apply {
                addActionListener {
                    maxTokensSpinner.value = 2048
                }
            }
            add(tokensResetBtn, BorderLayout.EAST)
        }

        // Setup timeout panel with reset button
        val timeoutPanel = JPanel(BorderLayout()).apply {
            add(timeoutSpinner, BorderLayout.CENTER)
            val timeoutResetBtn = JButton("Default").apply {
                addActionListener {
                    timeoutSpinner.value = 30000
                }
            }
            add(timeoutResetBtn, BorderLayout.EAST)
        }

        // Setup retry count panel with reset button
        val retryPanel = JPanel(BorderLayout()).apply {
            add(retryCountSpinner, BorderLayout.CENTER)
            val retryResetBtn = JButton("Default").apply {
                addActionListener {
                    retryCountSpinner.value = 3
                }
            }
            add(retryResetBtn, BorderLayout.EAST)
        }

        // Setup reset button
        resetToDefaultsButton.addActionListener {
            resetToDefaults()
        }

        val resetPanel = JPanel(BorderLayout()).apply {
            add(resetToDefaultsButton, BorderLayout.WEST)
        }

        return FormBuilder.createFormBuilder()
            .addLabeledComponent(JBLabel("Model Name:"), modelPanel)
            .addLabeledComponent(JBLabel("Endpoint URL:"), endpointPanel)
            .addLabeledComponent(JBLabel("API Path:"), apiPathPanel)
            .addLabeledComponent(JBLabel("Temperature:"), temperaturePanel)
            .addLabeledComponent(JBLabel("Max Tokens:"), maxTokensPanel)
            .addLabeledComponent(JBLabel("Timeout (ms):"), timeoutPanel)
            .addLabeledComponent(JBLabel("Retry Count:"), retryPanel)
            .addComponent(resetPanel)
            .addComponentFillVertically(JPanel(), 0)
            .panel
    }

    private fun createPromptConfigPanel(): JPanel {
        promptTemplateCombo.renderer = object : DefaultListCellRenderer() {
            override fun getListCellRendererComponent(
                list: JList<*>?, value: Any?, index: Int,
                isSelected: Boolean, cellHasFocus: Boolean
            ): JComponent {
                super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus)
                if (value is PromptTemplate) {
                    text = value.name
                }
                return this
            }
        }

        promptTemplateCombo.addActionListener {
            val selected = promptTemplateCombo.selectedItem as? PromptTemplate
            selected?.let {
                customPromptArea.text = it.template
                customPromptArea.isEditable = !it.isDefault
            }
        }

        customPromptArea.lineWrap = true
        customPromptArea.wrapStyleWord = true
        val scrollPane = JScrollPane(customPromptArea)

        val addTemplateButton = JButton("Add Custom Template").apply {
            addActionListener { showAddTemplateDialog() }
        }

        val removeTemplateButton = JButton("Remove Template").apply {
            addActionListener { removeSelectedTemplate() }
        }

        val buttonPanel = JPanel().apply {
            add(addTemplateButton)
            add(removeTemplateButton)
        }

        return FormBuilder.createFormBuilder()
            .addLabeledComponent(JBLabel("Select Template:"), promptTemplateCombo)
            .addLabeledComponent(JBLabel("Template Content:"), scrollPane)
            .addComponent(buttonPanel)
            .addComponentFillVertically(JPanel(), 0)
            .panel
    }

    private fun createTestPanel(): JPanel {
        val testButton = JButton("Test Connection").apply {
            addActionListener { testConnection() }
        }

        val testResultArea = JTextArea(5, 50).apply {
            isEditable = false
            text = "Click 'Test Connection' to verify AI service connectivity."
        }

        return FormBuilder.createFormBuilder()
            .addComponent(testButton)
            .addLabeledComponent(JBLabel("Test Results:"), JScrollPane(testResultArea))
            .addComponentFillVertically(JPanel(), 0)
            .panel.apply {
                putClientProperty("testResultArea", testResultArea)
            }
    }

    private fun initializeFields() {
        val config = settingsService.getAIModelConfig()

        // Initialize model combo
        refreshModelList()
        modelNameCombo.selectedItem = config.modelName

        endpointField.text = config.endpoint
        apiPathField.text = config.apiPath
        temperatureSpinner.value = config.temperature
        maxTokensSpinner.value = config.maxTokens
        timeoutSpinner.value = config.timeout
        retryCountSpinner.value = config.retryCount

        updatePromptTemplateCombo()
    }

    private fun updatePromptTemplateCombo() {
        val templates = settingsService.getAvailablePromptTemplates()
        val selectedTemplate = settingsService.getSelectedPromptTemplate()

        promptTemplateCombo.removeAllItems()
        templates.forEach { promptTemplateCombo.addItem(it) }

        templates.find { it.name == selectedTemplate.name }?.let {
            promptTemplateCombo.selectedItem = it
            customPromptArea.text = it.template
            customPromptArea.isEditable = !it.isDefault
        }
    }

    private fun showAddTemplateDialog() {
        val nameField = JBTextField()
        val descField = JBTextField()
        val templateArea = JTextArea(8, 40)
        templateArea.lineWrap = true
        templateArea.wrapStyleWord = true

        val panel = FormBuilder.createFormBuilder()
            .addLabeledComponent("Template Name:", nameField)
            .addLabeledComponent("Description:", descField)
            .addLabeledComponent("Template Content:", JScrollPane(templateArea))
            .panel

        val result = Messages.showOkCancelDialog(
            panel, "Add Custom Template", "Add Template",
            Messages.getQuestionIcon()
        )

        if (result == Messages.OK) {
            val name = nameField.text.trim()
            val description = descField.text.trim()
            val template = templateArea.text.trim()

            if (name.isNotEmpty() && template.isNotEmpty() &&
                template.contains(PromptTemplate.CODE_PLACEHOLDER)
            ) {

                val newTemplate = PromptTemplate(name, template, description)
                settingsService.addCustomPromptTemplate(newTemplate)
                updatePromptTemplateCombo()
                promptTemplateCombo.selectedItem = newTemplate
            } else {
                Messages.showErrorDialog(
                    "Please provide a name, template content, and ensure the template contains {code} placeholder.",
                    "Invalid Template"
                )
            }
        }
    }

    private fun removeSelectedTemplate() {
        val selected = promptTemplateCombo.selectedItem as? PromptTemplate
        if (selected != null && !selected.isDefault) {
            val result = Messages.showYesNoDialog(
                "Are you sure you want to remove the template '${selected.name}'?",
                "Remove Template",
                Messages.getQuestionIcon()
            )

            if (result == Messages.YES) {
                settingsService.removeCustomPromptTemplate(selected.name)
                updatePromptTemplateCombo()
            }
        } else {
            Messages.showInfoMessage(
                "Cannot remove default templates.",
                "Remove Template"
            )
        }
    }

    private fun testConnection() {
        val testPanel = findTestPanel()
        val testResultArea = testPanel?.getClientProperty("testResultArea") as? JTextArea

        testResultArea?.text = "Testing connection..."

        ProgressManager.getInstance().runProcessWithProgressSynchronously({
            val tempConfig = getCurrentConfig()
            settingsService.updateAIModelConfig(tempConfig)

            val reviewService = OllamaReviewService()

            val result = runBlocking {
                reviewService.testConnection()
            }

            SwingUtilities.invokeLater {
                testResultArea?.text = if (result) {
                    "✓ Connection successful! AI service is reachable."
                } else {
                    "✗ Connection failed. Please check your configuration."
                }
            }
        }, "Testing AI Service Connection", false, null)
    }

    private fun findTestPanel(): JPanel? {
        return mainPanel?.let { panel ->
            (panel.getComponent(0) as? JTabbedPane)?.getComponentAt(2) as? JPanel
        }
    }

    private fun getCurrentConfig(): AIModelConfig {
        return AIModelConfig(
            modelName = modelNameCombo.selectedItem as? String ?: "qwen3:8b",
            endpoint = endpointField.text,
            apiPath = apiPathField.text,
            temperature = temperatureSpinner.value as Double,
            maxTokens = maxTokensSpinner.value as Int,
            timeout = timeoutSpinner.value as Int,
            retryCount = retryCountSpinner.value as Int
        )
    }

    override fun isModified(): Boolean {
        val currentConfig = getCurrentConfig()
        val savedConfig = settingsService.getAIModelConfig()

        val selectedTemplate = promptTemplateCombo.selectedItem as? PromptTemplate
        val savedTemplate = settingsService.getSelectedPromptTemplate()

        return currentConfig != savedConfig ||
                selectedTemplate?.name != savedTemplate.name ||
                customPromptArea.text != selectedTemplate?.template
    }

    override fun apply() {
        val config = getCurrentConfig()
        if (config.isValid()) {
            settingsService.updateAIModelConfig(config)

            val selectedTemplate = promptTemplateCombo.selectedItem as? PromptTemplate
            selectedTemplate?.let {
                settingsService.setSelectedPromptTemplate(it.name)
            }
        } else {
            Messages.showErrorDialog(
                "Invalid configuration. Please check all fields.",
                "Configuration Error"
            )
        }
    }

    override fun reset() {
        initializeFields()
    }

    private fun refreshModelList() {
        refreshModelsButton.isEnabled = false
        refreshModelsButton.text = "Loading..."

        ApplicationManager.getApplication().executeOnPooledThread {
            try {
                val config = getCurrentConfig()
                val tempService = OllamaReviewService()
                tempService.updateModelConfig(config)

                val models = runBlocking {
                    tempService.getAvailableModels()
                }

                SwingUtilities.invokeLater {
                    val currentSelection = modelNameCombo.selectedItem as? String
                    modelNameCombo.removeAllItems()

                    models.forEach { model ->
                        modelNameCombo.addItem(model)
                    }

                    // Restore selection or set default
                    if (currentSelection != null && models.contains(currentSelection)) {
                        modelNameCombo.selectedItem = currentSelection
                    } else if (models.isNotEmpty()) {
                        modelNameCombo.selectedItem = models.first()
                    }

                    refreshModelsButton.isEnabled = true
                    refreshModelsButton.text = "Refresh"
                }
            } catch (e: Exception) {
                SwingUtilities.invokeLater {
                    // Fallback to default models
                    val defaultModels = listOf(
                        "qwen3:8b", "qwen:7b", "qwen:14b", "llama3:8b", "llama3:70b",
                        "codellama:7b", "codellama:13b", "mistral:7b", "deepseek-coder:6.7b"
                    )

                    val currentSelection = modelNameCombo.selectedItem as? String
                    modelNameCombo.removeAllItems()

                    defaultModels.forEach { model ->
                        modelNameCombo.addItem(model)
                    }

                    if (currentSelection != null && defaultModels.contains(currentSelection)) {
                        modelNameCombo.selectedItem = currentSelection
                    } else {
                        modelNameCombo.selectedItem = "qwen3:8b"
                    }

                    refreshModelsButton.isEnabled = true
                    refreshModelsButton.text = "Refresh"
                }
            }
        }
    }

    /**
     * Reset all configuration fields to their default values
     */
    private fun resetToDefaults() {
        val result = Messages.showYesNoDialog(
            "Are you sure you want to reset all settings to their default values?\nThis action cannot be undone.",
            "Reset to Defaults",
            Messages.getQuestionIcon()
        )

        if (result == Messages.YES) {
            // Reset AI configuration to defaults
            modelNameCombo.selectedItem = "qwen3:8b"
            endpointField.text = "http://192.168.66.181:11434"
            apiPathField.text = "/api/generate"
            temperatureSpinner.value = 0.7
            maxTokensSpinner.value = 2048
            timeoutSpinner.value = 30000
            retryCountSpinner.value = 3

            // Reset prompt template selection
            val defaultTemplate = settingsService.getAvailablePromptTemplates()
                .find { it.name == "Default Code Review" }
            if (defaultTemplate != null) {
                promptTemplateCombo.selectedItem = defaultTemplate
                customPromptArea.text = defaultTemplate.template
                customPromptArea.isEditable = !defaultTemplate.isDefault
            }

            Messages.showInfoMessage(
                "All settings have been reset to their default values.",
                "Reset Complete"
            )
        }
    }
}