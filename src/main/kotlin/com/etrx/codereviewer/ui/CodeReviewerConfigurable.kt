package com.etrx.codereviewer.ui

import com.etrx.codereviewer.model.AIModelConfig
import com.etrx.codereviewer.model.PromptTemplate
import com.etrx.codereviewer.service.CodeReviewerSettingsService
import com.etrx.codereviewer.service.OllamaReviewService
import com.etrx.codereviewer.service.OpenRouterReviewService
import com.etrx.codereviewer.util.I18nUtil
import com.etrx.codereviewer.model.Provider
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.Messages
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextField
import com.intellij.ui.components.JBPasswordField
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

    // Init/refresh coordination
    private var initSessionId: Long = 0L
    private var initIntendedModel: String? = null
    private var refreshGeneration: Long = 0L
    private var latestAppliedGeneration: Long = 0L
    private var refreshInFlight: Boolean = false
    
    // AI Model Configuration Fields
    private val providerCombo = JComboBox(arrayOf("Ollama", "OpenRouter"))
    private val apiKeyField = JBPasswordField()
    private val modelNameCombo = JComboBox<String>()
    private val modelNameField = JBTextField()
    private val modelCard = JPanel(java.awt.CardLayout())
    private val refreshModelsButton = JButton(I18nUtil.message("button.refresh"))
    private val resetToDefaultsButton = JButton(I18nUtil.message("button.resetToDefaults"))
    private val endpointField = JBTextField()
    private val apiPathField = JBTextField()
    private val temperatureSpinner = JSpinner(SpinnerNumberModel(0.7, 0.0, 2.0, 0.1))
    private val maxTokensSpinner = JSpinner(SpinnerNumberModel(2048, 1, 8192, 100))
    private val timeoutSpinner = JSpinner(SpinnerNumberModel(30000, 1000, 300000, 1000))
    private val retryCountSpinner = JSpinner(SpinnerNumberModel(3, 0, 10, 1))

    // Prompt Template Fields
    private val promptTemplateCombo = JComboBox<PromptTemplate>()
    private val customPromptArea = JTextArea(10, 50)
    private val resetTemplateButton = JButton(I18nUtil.message("button.reset"))
    
    // Review Result File Configuration
    private val reviewResultFilePathField = JBTextField()

    // API Key row controls for conditional visibility
    private val apiKeyLabel = JBLabel(I18nUtil.message("label.apiKey"))
    private val apiKeyPanel = JPanel(BorderLayout())

    override fun getDisplayName(): String = I18nUtil.message("configurable.displayName")

    override fun createComponent(): JComponent {
        // Start a new init session and reset coordination flags
        initSessionId += 1
        initIntendedModel = null
        refreshInFlight = false
        initializeFields()

        val aiConfigPanel = createAIConfigPanel()
        val promptConfigPanel = createPromptConfigPanel()

        mainPanel = JPanel(BorderLayout()).apply {
            val tabPane = JTabbedPane()
            tabPane.addTab(I18nUtil.message("tab.aiConfiguration"), aiConfigPanel)
            tabPane.addTab(I18nUtil.message("tab.promptTemplates"), promptConfigPanel)
            tabPane.addTab(I18nUtil.message("tab.resultOutput"), createResultConfigPanel())
            add(tabPane, BorderLayout.CENTER)
        }

        return mainPanel!!
    }

    private fun createAIConfigPanel(): JPanel {
        // Provider selection + API key panel
        val providerPanel = JPanel(BorderLayout()).apply {
            add(providerCombo, BorderLayout.CENTER)
        }
        apiKeyPanel.layout = BorderLayout()
        apiKeyPanel.removeAll()
        // Add show/hide toggle for API key
        val toggleVisibility = JCheckBox(I18nUtil.message("checkbox.show")).apply {
            toolTipText = I18nUtil.message("tooltip.showHideApiKey")
            isSelected = false
            addActionListener {
                val def = UIManager.getLookAndFeelDefaults().get("PasswordField.echoChar")
                val echo = (def as? Char) ?: '*'
                apiKeyField.echoChar = if (isSelected) 0.toChar() else echo
            }
        }
        // Initialize echo char properly (in case LAF default is zero)
        if (apiKeyField.echoChar.toInt() == 0) {
            val def = UIManager.getLookAndFeelDefaults().get("PasswordField.echoChar")
            apiKeyField.echoChar = (def as? Char) ?: '*'
        }
        apiKeyPanel.add(apiKeyField, BorderLayout.CENTER)
        apiKeyPanel.add(toggleVisibility, BorderLayout.EAST)
        apiKeyField.toolTipText = I18nUtil.message("tooltip.apiKey")

        // Model selection/input using CardLayout
        modelCard.add(modelNameCombo, "OLLAMA")
        modelCard.add(modelNameField, "OPENROUTER")
        val modelPanel = JPanel(BorderLayout()).apply {
            add(modelCard, BorderLayout.CENTER)
            add(refreshModelsButton, BorderLayout.EAST)
        }

        refreshModelsButton.addActionListener {
            refreshModelList()
        }

        providerCombo.addActionListener {
            onProviderChanged()
        }

        // Setup endpoint panel with reset button
        val endpointPanel = JPanel(BorderLayout()).apply {
            add(endpointField, BorderLayout.CENTER)
            val endpointResetBtn = JButton(I18nUtil.message("button.default")).apply {
                addActionListener {
                    endpointField.text = I18nUtil.message("default.endpoint.ollama")
                }
            }
            add(endpointResetBtn, BorderLayout.EAST)
        }

        // Setup connection test panel
        val testButton = JButton(I18nUtil.message("button.testConnection")).apply {
            addActionListener { testConnection() }
        }
        val testResultArea = JTextArea(3, 50).apply {
            isEditable = false
            text = I18nUtil.message("test.connection.description")
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
            val apiPathResetBtn = JButton(I18nUtil.message("button.default")).apply {
                addActionListener {
                    apiPathField.text = I18nUtil.message("default.apiPath.ollama")
                }
            }
            add(apiPathResetBtn, BorderLayout.EAST)
        }

        // Setup temperature panel with reset button
        val temperaturePanel = JPanel(BorderLayout()).apply {
            add(temperatureSpinner, BorderLayout.CENTER)
            val tempResetBtn = JButton(I18nUtil.message("button.default")).apply {
                addActionListener {
                    temperatureSpinner.value = 0.7
                }
            }
            add(tempResetBtn, BorderLayout.EAST)
        }

        // Setup max tokens panel with reset button
        val maxTokensPanel = JPanel(BorderLayout()).apply {
            add(maxTokensSpinner, BorderLayout.CENTER)
            val tokensResetBtn = JButton(I18nUtil.message("button.default")).apply {
                addActionListener {
                    maxTokensSpinner.value = 2048
                }
            }
            add(tokensResetBtn, BorderLayout.EAST)
        }

        // Setup timeout panel with reset button
        val timeoutPanel = JPanel(BorderLayout()).apply {
            add(timeoutSpinner, BorderLayout.CENTER)
            val timeoutResetBtn = JButton(I18nUtil.message("button.default")).apply {
                addActionListener {
                    timeoutSpinner.value = 120000
                }
            }
            add(timeoutResetBtn, BorderLayout.EAST)
        }

        // Setup retry count panel with reset button
        val retryPanel = JPanel(BorderLayout()).apply {
            add(retryCountSpinner, BorderLayout.CENTER)
            val retryResetBtn = JButton(I18nUtil.message("button.default")).apply {
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

        // Ensure initial visibility of API Key row based on provider
            val isOpenRouterInit = (providerCombo.selectedItem as? String) == I18nUtil.message("provider.openRouter")
        apiKeyLabel.isVisible = isOpenRouterInit
        apiKeyPanel.isVisible = isOpenRouterInit

        return FormBuilder.createFormBuilder()
                .addLabeledComponent(JBLabel(I18nUtil.message("label.provider")), providerPanel)
                .addLabeledComponent(apiKeyLabel, apiKeyPanel)
                .addLabeledComponent(JBLabel(I18nUtil.message("label.endpointUrl")), endpointPanel)
                .addLabeledComponent(JBLabel(I18nUtil.message("label.connectionTest")), testPanel)
                .addLabeledComponent(JBLabel(I18nUtil.message("label.model")), modelPanel)
                .addLabeledComponent(JBLabel(I18nUtil.message("label.apiPath")), apiPathPanel)
                .addLabeledComponent(JBLabel(I18nUtil.message("label.temperature")), temperaturePanel)
                .addLabeledComponent(JBLabel(I18nUtil.message("label.maxTokens")), maxTokensPanel)
                .addLabeledComponent(JBLabel(I18nUtil.message("label.timeout")), timeoutPanel)
                .addLabeledComponent(JBLabel(I18nUtil.message("label.retryCount")), retryPanel)
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
                // ${I18nUtil.message("settings.comment.allow.edit.default")}
                customPromptArea.isEditable = true
                updateResetButtonState()
            }
        }

        customPromptArea.lineWrap = true
        customPromptArea.wrapStyleWord = true
        val scrollPane = JBScrollPane(customPromptArea)

        val addTemplateButton = JButton(I18nUtil.message("button.addCustomTemplate")).apply {
            addActionListener { showAddTemplateDialog() }
        }

        val removeTemplateButton = JButton(I18nUtil.message("button.removeTemplate")).apply {
            addActionListener { removeSelectedTemplate() }
        }

        // Reset button for default templates
        resetTemplateButton.addActionListener {
            val selected = promptTemplateCombo.selectedItem as? PromptTemplate ?: return@addActionListener
            val original = settingsService.getOriginalDefaultTemplate(selected.name)
            if (original != null) {
                customPromptArea.text = original.template
            }
            updateResetButtonState()
        }

        // Update reset button enablement when text changes
        customPromptArea.document.addDocumentListener(object : javax.swing.event.DocumentListener {
            override fun insertUpdate(e: javax.swing.event.DocumentEvent?) = updateResetButtonState()
            override fun removeUpdate(e: javax.swing.event.DocumentEvent?) = updateResetButtonState()
            override fun changedUpdate(e: javax.swing.event.DocumentEvent?) = updateResetButtonState()
        })

        val buttonPanel = JPanel().apply {
            add(addTemplateButton)
            add(removeTemplateButton)
            add(resetTemplateButton)
        }

        return FormBuilder.createFormBuilder()
            .addLabeledComponent(JBLabel(I18nUtil.message("label.selectTemplate")), promptTemplateCombo)
                .addLabeledComponent(JBLabel(I18nUtil.message("label.templateContent")), scrollPane)
            .addComponent(buttonPanel)
            .addComponentFillVertically(JPanel(), 0)
            .panel
    }
    
    private fun createResultConfigPanel(): JPanel {
        // Setup result file path panel with reset button
        val filePathPanel = JPanel(BorderLayout()).apply {
            add(reviewResultFilePathField, BorderLayout.CENTER)
            val filePathResetBtn = JButton(I18nUtil.message("button.default")).apply {
                addActionListener {
                    reviewResultFilePathField.text = I18nUtil.message("default.resultFilePath")
                }
            }
            add(filePathResetBtn, BorderLayout.EAST)
        }
        
        val helpText = JLabel("<html><font color='gray'><i>" + I18nUtil.message("label.resultFilePath.help") + "<br/>" +
                "</font></html>")
        
        return FormBuilder.createFormBuilder()
            .addLabeledComponent(JBLabel(I18nUtil.message("label.resultFilePath")), filePathPanel)
            .addComponent(helpText)
            .addComponentFillVertically(JPanel(), 0)
            .panel
    }

    private fun initializeFields() {
            // Initialize provider and model input cards
            val saved = settingsService.getAIModelConfig()
            providerCombo.selectedItem = if (saved.provider == Provider.OPENROUTER) I18nUtil.message("provider.openRouter") else I18nUtil.message("provider.ollama")
            apiKeyField.text = saved.apiKey
            val cl = modelCard.layout as java.awt.CardLayout
            if (saved.provider == Provider.OPENROUTER) {
                cl.show(modelCard, "OPENROUTER")
                modelNameField.text = if (saved.modelName.isNotBlank()) saved.modelName else I18nUtil.message("default.model.openRouter")
                refreshModelsButton.isEnabled = false
                if (saved.endpoint.isBlank()) endpointField.text = I18nUtil.message("default.endpoint.openRouter")
                if (saved.apiPath.isBlank()) apiPathField.text = I18nUtil.message("default.apiPath.openRouter")
            } else {
                cl.show(modelCard, "OLLAMA")
                refreshModelsButton.isEnabled = true
            }
        val config = settingsService.getAIModelConfig()

        // Set other fields first
        endpointField.text = config.endpoint
        apiPathField.text = config.apiPath
        temperatureSpinner.value = config.temperature
        maxTokensSpinner.value = config.maxTokens
        timeoutSpinner.value = config.timeout
        retryCountSpinner.value = config.retryCount
        
        // Set result file path
        reviewResultFilePathField.text = settingsService.getReviewResultFilePath()

        // Initialize model combo with saved selection (only for Ollama)
        if (config.provider == Provider.OLLAMA) {
            val intended = initIntendedModel ?: config.modelName.also { initIntendedModel = it }
            if (refreshInFlight) {
                logger.info("初始化会话#$initSessionId：已有刷新进行中，跳过重复刷新，预选=$intended")
            } else {
                logger.info("初始化会话#$initSessionId：提供方为 OLLAMA，执行模型列表刷新，预选=$intended")
                refreshModelList(intended)
            }
        } else {
            logger.info("初始化会话#$initSessionId：提供方为 OPENROUTER，跳过模型列表刷新")
        }

        updatePromptTemplateCombo()

        // Ensure provider-dependent UI visibility updated when UI already constructed
        if (apiKeyPanel.parent != null) {
            onProviderChanged()
        }
    }

    private fun updateResetButtonState() {
        val selected = promptTemplateCombo.selectedItem as? PromptTemplate
        if (selected == null) {
            resetTemplateButton.isEnabled = false
            return
        }
        val original = settingsService.getOriginalDefaultTemplate(selected.name)
        if (original == null) {
            // Custom template: reset button not applicable
            resetTemplateButton.isEnabled = false
            return
        }
        // Enable when current text differs from original
        resetTemplateButton.isEnabled = customPromptArea.text != original.template
    }

    private fun updatePromptTemplateCombo() {
        val templates = settingsService.getAvailablePromptTemplates()
        val selectedTemplate = settingsService.getSelectedPromptTemplate()

        promptTemplateCombo.removeAllItems()
        templates.forEach { promptTemplateCombo.addItem(it) }

        templates.find { it.name == selectedTemplate.name }?.let {
            promptTemplateCombo.selectedItem = it
            customPromptArea.text = it.template
            customPromptArea.isEditable = true
            updateResetButtonState()
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
                        I18nUtil.message("error.missing.template.name"),
                    I18nUtil.message("dialog.title.missingTemplateName")
                    )
                }
                template.isEmpty() -> {
                    Messages.showErrorDialog(
                        I18nUtil.message("error.missing.template.content"),
                    I18nUtil.message("dialog.title.missingTemplateContent")
                    )
                }
                !template.contains(PromptTemplate.CODE_PLACEHOLDER) -> {
                    Messages.showErrorDialog(
                        I18nUtil.message("error.missing.code.placeholder"),
                    I18nUtil.message("dialog.title.missingCodePlaceholder")
                    )
                }
                settingsService.getAvailablePromptTemplates().any { it.name == name } -> {
                    Messages.showErrorDialog(
                        I18nUtil.message("error.duplicate.template.name", name),
                    I18nUtil.message("dialog.title.duplicateTemplateName")
                    )
                }
                else -> {
                    val newTemplate = PromptTemplate(name, template, description)
                    settingsService.addCustomPromptTemplate(newTemplate)
                    updatePromptTemplateCombo()
                    promptTemplateCombo.selectedItem = newTemplate
                    
                    Messages.showInfoMessage(
                        I18nUtil.message("message.template.added", name),
                    I18nUtil.message("dialog.title.templateAdded")
                    )
                }
            }
        }
    }

    private fun removeSelectedTemplate() {
        val selected = promptTemplateCombo.selectedItem as? PromptTemplate
        if (selected != null && !selected.isDefault) {
            val result = Messages.showYesNoDialog(
                I18nUtil.message("message.confirm.remove.template", selected.name),
                    I18nUtil.message("dialog.title.removeTemplate"),
                Messages.getQuestionIcon()
            )

            if (result == Messages.YES) {
                settingsService.removeCustomPromptTemplate(selected.name)
                updatePromptTemplateCombo()
            }
        } else {
            Messages.showInfoMessage(
                I18nUtil.message("error.cannot.remove.default.template"),
                    I18nUtil.message("dialog.title.removeTemplate")
            )
        }
    }

    private fun testConnection() {
            // Enforce API key when OpenRouter is selected
            if ((providerCombo.selectedItem as? String) == I18nUtil.message("provider.openRouter") && getApiKeyText().isBlank()) {
                Messages.showErrorDialog(I18nUtil.message("message.openRouterRequiresApiKey"), I18nUtil.message("dialog.title.missingApiKey"))
                return
            }
        val testPanel = findTestPanel()
        val testResultArea = testPanel?.getClientProperty("testResultArea") as? JTextArea

        testResultArea?.text = I18nUtil.message("test.connection.testing")

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

                val reviewService = if (tempConfig.provider == Provider.OLLAMA) OllamaReviewService() else OpenRouterReviewService()

                val result = runBlocking {
                    if (progressIndicator?.isCanceled == true) {
                        logger.info("用户在测试开始前取消了连接测试")
                        return@runBlocking false
                    }
                    
                    logger.info(I18nUtil.message("log.startConnectionTest"))
                    val testResult = reviewService.testConnection(progressIndicator)
                    
                    if (progressIndicator?.isCanceled == true) {
                        logger.info(I18nUtil.message("log.userCancelledTest"))
                        return@runBlocking false
                    }
                    
                    testResult
                }

                val totalTime = System.currentTimeMillis() - startTime

                SwingUtilities.invokeLater {
                    testResultArea?.text = if (progressIndicator?.isCanceled == true) {
                        logger.info(I18nUtil.message("log.uiDisplayCancelledTest"))
                        I18nUtil.message("connection.test.cancelled.message")
                    } else if (result) {
                        logger.info("配置界面连接测试成功 - 总耗时: ${totalTime}ms")
                        I18nUtil.message("connection.test.success.message")
                    } else {
                        logger.info("配置界面连接测试失败 - 总耗时: ${totalTime}ms")
                        I18nUtil.message("connection.test.failed.message")
                    }
                }
                
                logger.info("=== 配置界面连接测试结束 ===\n")
            }, I18nUtil.message("progress.title.testingConnection"), true, null)
                } catch (e: Exception) {
            // Handle cancellation or other exceptions
            logger.error("配置界面连接测试发生异常 - 类型: ${e.javaClass.simpleName}, 消息: ${e.message}", e)
            
            SwingUtilities.invokeLater {
                testResultArea?.text = I18nUtil.message("connection.test.cancelled.message")
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

    private fun getApiKeyText(): String = String(apiKeyField.password).trim()

    private fun getCurrentConfig(): AIModelConfig {
        val provider = if ((providerCombo.selectedItem as? String) == I18nUtil.message("provider.openRouter")) Provider.OPENROUTER else Provider.OLLAMA
        val modelName = if (provider == Provider.OLLAMA) (modelNameCombo.selectedItem as? String ?: I18nUtil.message("default.model.qwen3")) else (modelNameField.text.ifBlank { I18nUtil.message("default.model.openRouter") })
        return AIModelConfig(
            modelName = modelName,
            endpoint = endpointField.text,
            apiPath = apiPathField.text,
            temperature = temperatureSpinner.value as Double,
            maxTokens = maxTokensSpinner.value as Int,
            timeout = timeoutSpinner.value as Int,
            retryCount = retryCountSpinner.value as Int,
            provider = provider,
            apiKey = getApiKeyText()
        )
    }

    override fun isModified(): Boolean {
        val currentConfig = getCurrentConfig()
        val savedConfig = settingsService.getAIModelConfig()

        val selectedTemplate = promptTemplateCombo.selectedItem as? PromptTemplate
        val savedTemplate = settingsService.getSelectedPromptTemplate()
        
        val currentFilePath = reviewResultFilePathField.text
        val savedFilePath = settingsService.getReviewResultFilePath()

        return currentConfig != savedConfig ||
                selectedTemplate?.name != savedTemplate.name ||
                customPromptArea.text != selectedTemplate?.template ||
                currentFilePath != savedFilePath
    }

    override fun apply() {
            // Validate API key when provider is OpenRouter
            val cfg = getCurrentConfig()
            if (cfg.provider == Provider.OPENROUTER && cfg.apiKey.isBlank()) {
                Messages.showErrorDialog(I18nUtil.message("message.openRouterRequiresApiKey"), I18nUtil.message("dialog.title.configurationError"))
                return
            }
        val config = getCurrentConfig()
        if (config.isValid()) {
            settingsService.updateAIModelConfig(config)

            val selectedTemplate = promptTemplateCombo.selectedItem as? PromptTemplate
            selectedTemplate?.let { selected ->
                settingsService.setSelectedPromptTemplate(selected.name)
                val original = settingsService.getOriginalDefaultTemplate(selected.name)
                if (original != null) {
                    // Default template: write override if changed; clear if same
                    val edited = customPromptArea.text
                    if (edited == original.template) {
                        settingsService.clearDefaultTemplateOverride(selected.name)
                    } else {
                        settingsService.setDefaultTemplateOverride(selected.name, edited)
                    }
                } else {
                    // Custom template: update if content changed
                    if (customPromptArea.text != selected.template) {
                        settingsService.updateCustomPromptTemplate(
                            PromptTemplate(selected.name, customPromptArea.text, selected.description, isDefault = false)
                        )
                    }
                }
            }
            
            // Save result file path
            val filePath = reviewResultFilePathField.text.trim()
            if (filePath.isNotEmpty()) {
                settingsService.setReviewResultFilePath(filePath)
            }
            
            // Refresh templates to reflect overrides and maintain selection
            updatePromptTemplateCombo()
        } else {
            Messages.showErrorDialog(
                I18nUtil.message("message.invalidConfiguration"),
                I18nUtil.message("dialog.title.configurationError")
            )
        }
    }

    override fun reset() {
        initializeFields()
    }

    private fun refreshModelList(preselectedModel: String? = null) {
        // Provider guard at call-time too
        val currentProviderIsOllamaAtStart = (providerCombo.selectedItem as? String) != I18nUtil.message("provider.openRouter")
        if (!currentProviderIsOllamaAtStart) {
            logger.info("刷新请求被忽略：当前提供方为 OPENROUTER，未触发模型刷新")
            return
        }

        val gen = (++refreshGeneration)
        refreshInFlight = true
        logger.info("=== 配置界面刷新模型列表开始 (gen=$gen) ===")

        refreshModelsButton.isEnabled = false
        refreshModelsButton.text = I18nUtil.message("button.loading")

        ApplicationManager.getApplication().executeOnPooledThread {
            val startTime = System.currentTimeMillis()
            
            try {
                val config = getCurrentConfig()
                // keep legacy log for context but include generation
                logger.info("=== 配置界面刷新模型列表开始 === (gen=$gen)")
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
                    // Skip outdated generations
                    if (gen != refreshGeneration) {
                        logger.info("跳过过期刷新结果 (gen=$gen, 最新=${refreshGeneration})")
                        return@invokeLater
                    }
                    // Ensure provider still OLLAMA before applying
                    val currentProviderIsOllama = (providerCombo.selectedItem as? String) != I18nUtil.message("provider.openRouter")
                    if (!currentProviderIsOllama) {
                        logger.info("刷新结果返回时提供方已切换为 OPENROUTER，跳过模型下拉更新")
                    } else {
                        val currentSelectionRaw = preselectedModel ?: (modelNameCombo.selectedItem as? String)
                        val currentSelection = currentSelectionRaw?.trim()
                        modelNameCombo.removeAllItems()

                        models.forEach { model ->
                            modelNameCombo.addItem(model)
                        }

                        // Selection restoration logic
                        val modelsSet = models.toSet()
                        var selected: String? = null
                        var note = ""
                        if (currentSelection != null) {
                            if (modelsSet.contains(currentSelection)) {
                                selected = currentSelection
                                note = "exact"
                            } else {
                                val ci = models.firstOrNull { it.equals(currentSelection, ignoreCase = true) }
                                if (ci != null) {
                                    selected = ci
                                    note = "case-insensitive"
                                } else {
                                    val base = currentSelection.substringBefore('@').trim()
                                    val prefix = base.substringBefore(':').ifBlank { base }
                                    val prefixHit = models.firstOrNull { it.startsWith(prefix, ignoreCase = true) }
                                    if (prefixHit != null) {
                                        selected = prefixHit
                                        note = "prefix"
                                    }
                                }
                            }
                        }
                        if (selected == null && models.isNotEmpty()) {
                            selected = models.first()
                            note = if (currentSelection == null) "default(no-preselect)" else "default(fallback)"
                        }

                        if (selected != null) {
                            modelNameCombo.selectedItem = selected
                            when (note) {
                                "exact" -> logger.info("恢复模型选择: $selected")
                                "case-insensitive" -> logger.info("恢复模型选择(忽略大小写): $selected (原值: ${currentSelection})")
                                "prefix" -> logger.info("恢复模型选择(前缀匹配): $selected (原值: ${currentSelection})")
                                else -> logger.info("设置默认模型: $selected (原值: ${currentSelection ?: "无"})")
                            }
                        }

                        refreshModelsButton.isEnabled = true
                        refreshModelsButton.text = I18nUtil.message("button.refresh")
                        logger.info("模型下拉框更新完成 (gen=$gen)")
                    }
                    latestAppliedGeneration = gen
                    if (gen == refreshGeneration) {
                        refreshInFlight = false
                    }
                }
                
                logger.info("=== 配置界面刷新模型列表完成 === (gen=$gen)\n")
            } catch (e: Exception) {
                val totalTime = System.currentTimeMillis() - startTime
                
                logger.info("刷新模型列表异常 - 类型: ${e.javaClass.simpleName}, 消息: ${e.message}, 耗时: ${totalTime}ms")
                
                SwingUtilities.invokeLater {
                    // Only apply fallback if still on OLLAMA
                    val currentProviderIsOllama = (providerCombo.selectedItem as? String) != I18nUtil.message("provider.openRouter")
                    if (!currentProviderIsOllama) {
                        logger.info("刷新异常时提供方已为 OPENROUTER，跳过默认模型回退更新")
                    } else {
                        // Fallback to default models
                        val defaultModels = listOf(
                            I18nUtil.message("default.model.qwen3"),
                            I18nUtil.message("default.model.qwen"),
                            I18nUtil.message("default.model.qwenLarge"),
                            I18nUtil.message("default.model.llama3"),
                            I18nUtil.message("default.model.llama3Large"),
                            I18nUtil.message("default.model.codeLlama"),
                            I18nUtil.message("default.model.codeLlamaLarge"),
                            I18nUtil.message("default.model.mistral"),
                            I18nUtil.message("default.model.deepseek")
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
                            modelNameCombo.selectedItem = I18nUtil.message("default.model.qwen3")
                            logger.info("使用默认模型列表，设置默认选择: ${I18nUtil.message("default.model.qwen3")}")
                        }

                        refreshModelsButton.isEnabled = true
                        refreshModelsButton.text = I18nUtil.message("button.refresh")
                        
                        logger.info("已降级到默认模型列表: ${defaultModels.joinToString(", ")}")
                    }
                    if (gen == refreshGeneration) {
                        refreshInFlight = false
                    }
                }
                
                logger.error("=== 配置界面刷新模型列表异常结束 === (gen=$gen)\n")
            }
        }
    }

    /**
     * Reset all configuration fields to their default values
     */
    private fun resetToDefaults() {
            providerCombo.selectedItem = I18nUtil.message("provider.ollama")
            apiKeyField.text = ""
            val cl = modelCard.layout as java.awt.CardLayout
            cl.show(modelCard, "OLLAMA")
            onProviderChanged()
        val result = Messages.showYesNoDialog(
            I18nUtil.message("message.resetConfirmation"),
            I18nUtil.message("dialog.title.resetToDefaults"),
            Messages.getQuestionIcon()
        )

        if (result == Messages.YES) {
            // Reset AI configuration to defaults
            modelNameCombo.selectedItem = I18nUtil.message("default.model.qwen3")
            endpointField.text = I18nUtil.message("default.endpoint.ollama")
            apiPathField.text = I18nUtil.message("default.apiPath.ollama")
            temperatureSpinner.value = 0.7
            maxTokensSpinner.value = 2048
            timeoutSpinner.value = 120000 // 更新默认超时时间
            retryCountSpinner.value = 3
            
            // Reset result file path
            reviewResultFilePathField.text = I18nUtil.message("default.resultFilePath")

            // Reset prompt template selection
            val defaultTemplate = settingsService.getAvailablePromptTemplates()
                .find { it.name == PromptTemplate.DEFAULT_TEMPLATE.name }
            if (defaultTemplate != null) {
                promptTemplateCombo.selectedItem = defaultTemplate
                customPromptArea.text = defaultTemplate.template
                customPromptArea.isEditable = true
                updateResetButtonState()
            }

            Messages.showInfoMessage(
                I18nUtil.message("message.resetComplete"),
                I18nUtil.message("dialog.title.resetComplete")
            )
        }
    }

    private fun onProviderChanged() {
        val selected = providerCombo.selectedItem as? String
        val isOpenRouter = selected == I18nUtil.message("provider.openRouter")

        // Switch model input card
        val cl = modelCard.layout as java.awt.CardLayout
        if (isOpenRouter) {
            cl.show(modelCard, "OPENROUTER")
            // Default model for OpenRouter if empty
            if (modelNameField.text.isBlank()) {
                modelNameField.text = I18nUtil.message("default.model.openRouter")
            }
        } else {
            cl.show(modelCard, "OLLAMA")
            // Ensure model list is available; leave selection as-is
            if ((modelNameCombo.model.size == 0)) {
                // Populate with a minimal fallback list; real refresh happens via button
                listOf(
                    I18nUtil.message("default.model.qwen3"),
                    I18nUtil.message("default.model.llama3"),
                    I18nUtil.message("default.model.mistral")
                ).forEach { modelNameCombo.addItem(it) }
                modelNameCombo.selectedItem = I18nUtil.message("default.model.qwen3")
            }
        }

        // Toggle controls by provider
        refreshModelsButton.isEnabled = !isOpenRouter
        apiKeyField.isEnabled = isOpenRouter
        apiKeyLabel.isVisible = isOpenRouter
        apiKeyPanel.isVisible = isOpenRouter

        // Adjust endpoint and api path defaults if fields look incompatible/blank
        if (isOpenRouter) {
            if (endpointField.text.isBlank() || endpointField.text.startsWith("http://")) {
                endpointField.text = I18nUtil.message("default.endpoint.openRouter")
            }
            if (apiPathField.text.isBlank() || apiPathField.text == "/api/generate") {
                apiPathField.text = I18nUtil.message("default.apiPath.openRouter")
            }
        } else {
            if (endpointField.text.isBlank() || endpointField.text.contains("openrouter", ignoreCase = true)) {
                endpointField.text = I18nUtil.message("default.endpoint.ollama")
            }
            if (apiPathField.text.isBlank() || apiPathField.text.contains("chat/completions", ignoreCase = true)) {
                apiPathField.text = I18nUtil.message("default.apiPath.ollama")
            }
        }
    }
}

/**
 * Dialog for adding custom templates
 */
private class AddTemplateDialog : DialogWrapper(true) {
    
    private val nameField = JBTextField(20).apply {
        toolTipText = I18nUtil.message("dialog.template.name.tooltip")
    }
    
    private val descField = JBTextField(30).apply {
        toolTipText = I18nUtil.message("dialog.template.description.tooltip")
    }
    
    private val templateArea = JTextArea(12, 60).apply {
        lineWrap = true
        wrapStyleWord = true
        toolTipText = I18nUtil.message("dialog.template.content.tooltip")
        text = """${I18nUtil.message("default.template.prompt")}

## ${I18nUtil.message("default.template.summary")}
${I18nUtil.message("default.template.summary.placeholder")}

## ${I18nUtil.message("default.template.issues")}
${I18nUtil.message("default.template.issues.placeholder")}

## ${I18nUtil.message("default.template.suggestions")}
${I18nUtil.message("default.template.suggestions.placeholder")}

${I18nUtil.message("default.template.code.content")}
{code}"""
    }
    
    val templateName: String get() = nameField.text
    val templateDescription: String get() = descField.text  
    val templateContent: String get() = templateArea.text
    
    init {
        title = I18nUtil.message("dialog.title.addCustomTemplate")
        init()
    }
    
    override fun createCenterPanel(): JComponent {
        val scrollPane = JBScrollPane(templateArea).apply {
            preferredSize = java.awt.Dimension(600, 300)
        }

        val panel = FormBuilder.createFormBuilder()
            .addLabeledComponent(I18nUtil.message("dialog.template.name.label"), nameField)
            .addLabeledComponent(I18nUtil.message("dialog.template.description.label"), descField)
            .addLabeledComponent(I18nUtil.message("dialog.template.content.label"), scrollPane)
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