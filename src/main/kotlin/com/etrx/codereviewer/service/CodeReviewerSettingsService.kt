package com.etrx.codereviewer.service

import com.etrx.codereviewer.model.AIModelConfig
import com.etrx.codereviewer.model.PromptTemplate
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.diagnostic.Logger
import com.intellij.util.xmlb.XmlSerializerUtil

/**
 * Persistent state for plugin settings
 */
data class CodeReviewerState(
    var aiModelName: String = "qwen3:8b",
    var aiEndpoint: String = "http://192.168.66.181:11434",
    var aiApiPath: String = "/api/generate",
    var aiTemperature: Double = 0.7,
    var aiMaxTokens: Int = 20480,
    var aiTimeout: Int = 300000, // 减少超时时间到30秒
    var aiRetryCount: Int = 3,
    var selectedPromptTemplate: String = "简洁代码评审",
    var customPromptTemplates: MutableList<PromptTemplateData> = mutableListOf(),
    // Overrides for built-in default templates: identified by name, content replaces bundled template
    var defaultTemplateOverrides: MutableList<PromptTemplateData> = mutableListOf(),
    var reviewResultFilePath: String = ".ai-codereview" // 默认为.ai-codereview文件夹
)

/**
 * Serializable prompt template data
 */
data class PromptTemplateData(
    var name: String = "",
    var template: String = "",
    var description: String = "",
    var isDefault: Boolean = false
)

/**
 * Service for managing plugin settings and configuration
 */
@Service(Service.Level.APP)
@State(
    name = "CodeReviewerSettings",
    storages = [Storage("codereviewer-settings.xml")]
)
class CodeReviewerSettingsService : PersistentStateComponent<CodeReviewerState> {
    
    private var state = CodeReviewerState()
    private val logger = Logger.getInstance(CodeReviewerSettingsService::class.java)
    
    companion object {
        fun getInstance(): CodeReviewerSettingsService {
            return ApplicationManager.getApplication().getService(CodeReviewerSettingsService::class.java)
        }
    }
    
    override fun getState(): CodeReviewerState = state
    
    override fun loadState(state: CodeReviewerState) {
        logger.info("=== 加载设置状态 ===")
        logger.info("加载前的模板选择: ${this.state.selectedPromptTemplate}")
        XmlSerializerUtil.copyBean(state, this.state)
        logger.info("加载后的模板选择: ${this.state.selectedPromptTemplate}")
        ensureDefaultValues()
        logger.info("验证后的模板选择: ${this.state.selectedPromptTemplate}")
        initializeDefaultTemplates()
        logger.info("=== 设置状态加载完成 ===")
    }
    
    /**
     * Ensure all default values are properly set
     */
    private fun ensureDefaultValues() {
        if (state.aiEndpoint.isBlank()) {
            state.aiEndpoint = "http://192.168.66.181:11434"
        }
        if (state.aiApiPath.isBlank()) {
            state.aiApiPath = "/api/generate"
        }
        if (state.aiModelName.isBlank()) {
            state.aiModelName = "qwen3:8b"
        }
        if (state.reviewResultFilePath.isBlank()) {
            state.reviewResultFilePath = "ai-code-review.md"
        }
        // Only reset template selection if it's truly blank or invalid
        if (state.selectedPromptTemplate.isBlank()) {
            state.selectedPromptTemplate = PromptTemplate.DEFAULT_TEMPLATE.name
        } else {
            // Validate that the selected template actually exists
            val availableTemplates = getAvailablePromptTemplates()
            val exists = availableTemplates.any { it.name == state.selectedPromptTemplate }
            if (!exists) {
                // Only reset if the template doesn't exist anymore
                state.selectedPromptTemplate = PromptTemplate.DEFAULT_TEMPLATE.name
            }
        }
    }
    
    fun getAIModelConfig(): AIModelConfig {
        return AIModelConfig(
            modelName = state.aiModelName,
            endpoint = state.aiEndpoint,
            apiPath = state.aiApiPath,
            temperature = state.aiTemperature,
            maxTokens = state.aiMaxTokens,
            timeout = state.aiTimeout,
            retryCount = state.aiRetryCount
        )
    }
    
    fun updateAIModelConfig(config: AIModelConfig) {
        state.aiModelName = config.modelName
        state.aiEndpoint = config.endpoint
        state.aiApiPath = config.apiPath
        state.aiTemperature = config.temperature
        state.aiMaxTokens = config.maxTokens
        state.aiTimeout = config.timeout
        state.aiRetryCount = config.retryCount
    }
    
    fun getAvailablePromptTemplates(): List<PromptTemplate> {
        // Base default templates
        val defaultTemplates = mutableListOf(
            PromptTemplate.DEFAULT_TEMPLATE,
            PromptTemplate.BE_TEMPLATE,
            PromptTemplate.FE_TEMPLATE,
            PromptTemplate.DOC_TEMPLATE,
            PromptTemplate.DETAILED_TEMPLATE
        )

        // Apply overrides if any
        if (state.defaultTemplateOverrides.isNotEmpty()) {
            val overrideMap = state.defaultTemplateOverrides.associateBy { it.name }
            for (i in defaultTemplates.indices) {
                val t = defaultTemplates[i]
                val ov = overrideMap[t.name]
                if (ov != null) {
                    defaultTemplates[i] = PromptTemplate(
                        name = t.name,
                        template = ov.template,
                        description = if (ov.description.isNotBlank()) ov.description else t.description,
                        isDefault = true
                    )
                }
            }
        }
        
        val customTemplates = state.customPromptTemplates.map { data ->
            PromptTemplate(
                name = data.name,
                template = data.template,
                description = data.description,
                isDefault = false
            )
        }
        
        return defaultTemplates + customTemplates
    }
    
    fun addCustomPromptTemplate(template: PromptTemplate) {
        val templateData = PromptTemplateData(
            name = template.name,
            template = template.template,
            description = template.description,
            isDefault = false
        )
        state.customPromptTemplates.add(templateData)
    }
    
    fun removeCustomPromptTemplate(templateName: String) {
        state.customPromptTemplates.removeIf { it.name == templateName }
    }
    
    /**
     * 更新已存在的自定义提示词模板
     */
    fun updateCustomPromptTemplate(template: PromptTemplate) {
        logger.info("更新自定义模板: ${template.name}")
        val existingTemplate = state.customPromptTemplates.find { it.name == template.name }
        if (existingTemplate != null) {
            // 更新现有模板
            existingTemplate.template = template.template
            existingTemplate.description = template.description
            logger.info("模板更新完成: ${template.name}")
        } else {
            // 如果不存在，则添加为新模板
            logger.warn("模板不存在，添加为新模板: ${template.name}")
            addCustomPromptTemplate(template)
        }
    }
    
    fun setDefaultTemplateOverride(name: String, template: String, description: String = "") {
        // Replace or add override
        val existing = state.defaultTemplateOverrides.find { it.name == name }
        if (existing != null) {
            existing.template = template
            if (description.isNotBlank()) existing.description = description
            existing.isDefault = true
        } else {
            state.defaultTemplateOverrides.add(
                PromptTemplateData(name = name, template = template, description = description, isDefault = true)
            )
        }
    }

    fun clearDefaultTemplateOverride(name: String) {
        state.defaultTemplateOverrides.removeIf { it.name == name }
    }

    fun getOriginalDefaultTemplate(name: String): PromptTemplate? {
        return listOf(
            PromptTemplate.DEFAULT_TEMPLATE,
            PromptTemplate.BE_TEMPLATE,
            PromptTemplate.FE_TEMPLATE,
            PromptTemplate.DOC_TEMPLATE,
            PromptTemplate.DETAILED_TEMPLATE
        ).find { it.name == name }
    }

    fun getSelectedPromptTemplate(): PromptTemplate {
        return getAvailablePromptTemplates().find { it.name == state.selectedPromptTemplate }
            ?: PromptTemplate.DEFAULT_TEMPLATE
    }
    
    fun setSelectedPromptTemplate(templateName: String) {
        logger.info("设置选中的模板: 从 '${state.selectedPromptTemplate}' 切换到 '$templateName'")
        state.selectedPromptTemplate = templateName
        logger.info("模板设置完成: ${state.selectedPromptTemplate}")
    }
    
    fun getReviewResultFilePath(): String {
        return state.reviewResultFilePath
    }
    
    fun setReviewResultFilePath(filePath: String) {
        logger.info("设置评审结果文件路径: 从 '${state.reviewResultFilePath}' 切换到 '$filePath'")
        state.reviewResultFilePath = filePath
        logger.info("文件路径设置完成: ${state.reviewResultFilePath}")
    }

    
    private fun initializeDefaultTemplates() {
        if (state.customPromptTemplates.isEmpty()) {
            // Ensure we have some default state
            state.selectedPromptTemplate = PromptTemplate.DEFAULT_TEMPLATE.name
        }
    }
}