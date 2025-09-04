package com.etrx.codereviewer.ui

import com.etrx.codereviewer.model.AIModelConfig
import com.etrx.codereviewer.model.PromptTemplate
import com.etrx.codereviewer.service.CodeReviewerSettingsService
import com.etrx.codereviewer.service.OllamaReviewService
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.Messages
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextField
import com.intellij.util.ui.FormBuilder
import kotlinx.coroutines.runBlocking
import java.awt.BorderLayout
import javax.swing.*

/**
 * Settings configurable for the Code Reviewer plugin
 */
class CodeReviewerConfigurable : Configurable {

    private val logger = Logger.getInstance(CodeReviewerConfigurable::class.java)
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

        mainPanel = JPanel(BorderLayout()).apply {
            val tabPane = JTabbedPane()
            tabPane.addTab("AI Configuration", aiConfigPanel)
            tabPane.addTab("Prompt Templates", promptConfigPanel)
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

        // Setup connection test panel
        val testButton = JButton("Test Connection").apply {
            addActionListener { testConnection() }
        }
        val testResultArea = JTextArea(3, 50).apply {
            isEditable = false
            text = "Click 'Test Connection' to verify AI service connectivity."
            lineWrap = true
            wrapStyleWord = true
        }
        val testScrollPane = JScrollPane(testResultArea)
        
        val testPanel = JPanel(BorderLayout()).apply {
            add(testButton, BorderLayout.NORTH)
            add(testScrollPane, BorderLayout.CENTER)
            putClientProperty("testResultArea", testResultArea)
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
            .addLabeledComponent(JBLabel("Endpoint URL:"), endpointPanel)
            .addLabeledComponent(JBLabel("Connection Test:"), testPanel)
            .addLabeledComponent(JBLabel("Model Name:"), modelPanel)
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
        val scrollPane = JBScrollPane(customPromptArea)

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

    private fun initializeFields() {
        val config = settingsService.getAIModelConfig()

        // Set other fields first
        endpointField.text = config.endpoint
        apiPathField.text = config.apiPath
        temperatureSpinner.value = config.temperature
        maxTokensSpinner.value = config.maxTokens
        timeoutSpinner.value = config.timeout
        retryCountSpinner.value = config.retryCount

        // Initialize model combo with saved selection
        refreshModelList(config.modelName)

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
        val dialog = AddTemplateDialog()
        if (dialog.showAndGet()) {
            val name = dialog.templateName.trim()
            val description = dialog.templateDescription.trim()
            val template = dialog.templateContent.trim()

            when {
                name.isEmpty() -> {
                    Messages.showErrorDialog(
                        "Template name is required. Please provide a unique name for your template.",
                        "Missing Template Name"
                    )
                }
                template.isEmpty() -> {
                    Messages.showErrorDialog(
                        "Template content is required. Please provide the template content.",
                        "Missing Template Content"
                    )
                }
                !template.contains(PromptTemplate.CODE_PLACEHOLDER) -> {
                    Messages.showErrorDialog(
                        "Template must contain {code} placeholder where the code will be inserted.\n\nExample: \"Please review the following code: {code}\"",
                        "Missing Code Placeholder"
                    )
                }
                settingsService.getAvailablePromptTemplates().any { it.name == name } -> {
                    Messages.showErrorDialog(
                        "A template with name '$name' already exists. Please choose a different name.",
                        "Duplicate Template Name"
                    )
                }
                else -> {
                    val newTemplate = PromptTemplate(name, template, description)
                    settingsService.addCustomPromptTemplate(newTemplate)
                    updatePromptTemplateCombo()
                    promptTemplateCombo.selectedItem = newTemplate
                    
                    Messages.showInfoMessage(
                        "Custom template '$name' has been added successfully.",
                        "Template Added"
                    )
                }
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

        try {
            ProgressManager.getInstance().runProcessWithProgressSynchronously({
                val progressIndicator = ProgressManager.getInstance().progressIndicator
                val startTime = System.currentTimeMillis()
                
                val tempConfig = getCurrentConfig()
                logger.info("=== 配置界面连接测试开始 ===")
                logger.info("测试配置详情:")
                logger.info("  - Endpoint: ${tempConfig.endpoint}")
                logger.info("  - API Path: ${tempConfig.apiPath}")
                logger.info("  - Full URL: ${tempConfig.getFullUrl()}")
                logger.info("  - Model: ${tempConfig.modelName}")
                logger.info("  - Timeout: ${tempConfig.timeout}ms")
                logger.info("  - Temperature: ${tempConfig.temperature}")
                logger.info("  - Max Tokens: ${tempConfig.maxTokens}")
                logger.info("  - Retry Count: ${tempConfig.retryCount}")
                
                settingsService.updateAIModelConfig(tempConfig)

                val reviewService = OllamaReviewService()

                val result = runBlocking {
                    if (progressIndicator?.isCanceled == true) {
                        logger.info("用户在测试开始前取消了连接测试")
                        return@runBlocking false
                    }
                    
                    logger.info("开始执行连接测试...")
                    val testResult = reviewService.testConnection()
                    
                    if (progressIndicator?.isCanceled == true) {
                        logger.info("用户在测试过程中取消了连接测试")
                        return@runBlocking false
                    }
                    
                    testResult
                }

                val totalTime = System.currentTimeMillis() - startTime

                SwingUtilities.invokeLater {
                    testResultArea?.text = if (progressIndicator?.isCanceled == true) {
                        logger.info("配置界面显示: 连接测试已取消")
                        "❌ Connection test was cancelled."
                    } else if (result) {
                        logger.info("配置界面连接测试成功 - 总耗时: ${totalTime}ms")
                        "✓ Connection successful! AI service is reachable."
                    } else {
                        logger.info("配置界面连接测试失败 - 总耗时: ${totalTime}ms")
                        "✗ Connection failed. Please check your configuration."
                    }
                }
                
                logger.info("=== 配置界面连接测试结束 ===\n")
            }, "Testing AI Service Connection", true, null)
                } catch (e: Exception) {
            // Handle cancellation or other exceptions
            logger.error("配置界面连接测试发生异常 - 类型: ${e.javaClass.simpleName}, 消息: ${e.message}", e)
            
            SwingUtilities.invokeLater {
                testResultArea?.text = "❌ Connection test was cancelled or failed."
            }
            
            logger.info("=== 配置界面连接测试异常结束 ===\n")
        }
    }

    private fun findTestPanel(): JPanel? {
        return mainPanel?.let { panel ->
            // Find the test panel in the AI Configuration tab
            val tabPane = panel.getComponent(0) as? JTabbedPane
            val aiConfigPanel = tabPane?.getComponentAt(0) as? JPanel
            
            // Search for the test panel by its client property
            fun findPanelWithProperty(component: java.awt.Component?): JPanel? {
                if (component is JPanel && component.getClientProperty("testResultArea") != null) {
                    return component
                }
                if (component is java.awt.Container) {
                    for (child in component.components) {
                        val result = findPanelWithProperty(child)
                        if (result != null) return result
                    }
                }
                return null
            }
            
            findPanelWithProperty(aiConfigPanel)
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

    private fun refreshModelList(preselectedModel: String? = null) {
        refreshModelsButton.isEnabled = false
        refreshModelsButton.text = "Loading..."

        ApplicationManager.getApplication().executeOnPooledThread {
            val startTime = System.currentTimeMillis()
            
            try {
                val config = getCurrentConfig()
                logger.info("=== 配置界面刷新模型列表开始 ===")
                logger.info("刷新配置 - Endpoint: ${config.endpoint}, API: ${config.apiPath}")
                logger.info("预选模型: ${preselectedModel ?: "无"}")
                
                val tempService = OllamaReviewService()
                tempService.updateModelConfig(config)

                val models = runBlocking {
                    tempService.getAvailableModels()
                }

                val totalTime = System.currentTimeMillis() - startTime
                logger.info("刷新模型列表成功 - 获取到 ${models.size} 个模型，耗时: ${totalTime}ms")
                logger.info("模型列表: ${models.joinToString(", ")}")

                SwingUtilities.invokeLater {
                    val currentSelection = preselectedModel ?: (modelNameCombo.selectedItem as? String)
                    modelNameCombo.removeAllItems()

                    models.forEach { model ->
                        modelNameCombo.addItem(model)
                    }

                    // Restore selection or set default
                    if (currentSelection != null && models.contains(currentSelection)) {
                        modelNameCombo.selectedItem = currentSelection
                        logger.info("恢复模型选择: $currentSelection")
                    } else if (models.isNotEmpty()) {
                        modelNameCombo.selectedItem = models.first()
                        logger.info("设置默认模型: ${models.first()}")
                    }

                    refreshModelsButton.isEnabled = true
                    refreshModelsButton.text = "Refresh"
                    
                    logger.info("模型下拉框更新完成")
                }
                
                logger.info("=== 配置界面刷新模型列表完成 ===\n")
            } catch (e: Exception) {
                val totalTime = System.currentTimeMillis() - startTime
                
                logger.info("刷新模型列表异常 - 类型: ${e.javaClass.simpleName}, 消息: ${e.message}, 耗时: ${totalTime}ms")
                
                SwingUtilities.invokeLater {
                    // Fallback to default models
                    val defaultModels = listOf(
                        "qwen3:8b", "qwen:7b", "qwen:14b", "llama3:8b", "llama3:70b",
                        "codellama:7b", "codellama:13b", "mistral:7b", "deepseek-coder:6.7b"
                    )

                    val currentSelection = preselectedModel ?: (modelNameCombo.selectedItem as? String)
                    modelNameCombo.removeAllItems()

                    defaultModels.forEach { model ->
                        modelNameCombo.addItem(model)
                    }

                    if (currentSelection != null && defaultModels.contains(currentSelection)) {
                        modelNameCombo.selectedItem = currentSelection
                        logger.info("使用默认模型列表，恢复选择: $currentSelection")
                    } else {
                        modelNameCombo.selectedItem = "qwen3:8b"
                        logger.info("使用默认模型列表，设置默认选择: qwen3:8b")
                    }

                    refreshModelsButton.isEnabled = true
                    refreshModelsButton.text = "Refresh"
                    
                    logger.info("已降级到默认模型列表: ${defaultModels.joinToString(", ")}")
                }
                
                logger.error("=== 配置界面刷新模型列表异常结束 ===\n")
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

/**
 * Dialog for adding custom templates
 */
private class AddTemplateDialog : DialogWrapper(true) {
    
    private val nameField = JBTextField(20).apply {
        toolTipText = "Enter a unique name for your custom template"
    }
    
    private val descField = JBTextField(30).apply {
        toolTipText = "Optional: Brief description of what this template does"
    }
    
    private val templateArea = JTextArea(12, 60).apply {
        lineWrap = true
        wrapStyleWord = true
        toolTipText = "Template content must include {code} placeholder where the code will be inserted"
        text = """请对以下代码进行评审：

## 📝 评审总结
[简短总结]

## 🔍 发现的问题
[列出问题或写"未发现明显问题"]

## 💡 优化建议
[具体建议或写"代码质量良好"]

代码内容：
{code}"""
    }
    
    val templateName: String get() = nameField.text
    val templateDescription: String get() = descField.text  
    val templateContent: String get() = templateArea.text
    
    init {
        title = "Add Custom Template"
        init()
    }
    
    override fun createCenterPanel(): JComponent {
        val scrollPane = JBScrollPane(templateArea).apply {
            preferredSize = java.awt.Dimension(600, 300)
        }

        val panel = FormBuilder.createFormBuilder()
            .addLabeledComponent("Template Name*:", nameField)
            .addLabeledComponent("Description:", descField)
            .addLabeledComponent("Template Content*:", scrollPane)
            .panel

        val instructionLabel = JLabel("<html><font color='gray'><i>* Required fields. Template must contain {code} placeholder.</i></font></html>")
        
        return JPanel(BorderLayout()).apply {
            add(panel, BorderLayout.CENTER)
            add(instructionLabel, BorderLayout.SOUTH)
            border = BorderFactory.createEmptyBorder(10, 10, 10, 10)
        }
    }
    
    override fun getPreferredFocusedComponent(): JComponent = nameField
}