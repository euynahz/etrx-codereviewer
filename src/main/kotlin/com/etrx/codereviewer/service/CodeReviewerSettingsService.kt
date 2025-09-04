package com.etrx.codereviewer.service

import com.etrx.codereviewer.model.AIModelConfig
import com.etrx.codereviewer.model.PromptTemplate
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.util.xmlb.XmlSerializerUtil

/**
 * Persistent state for plugin settings
 */
data class CodeReviewerState(
    var aiModelName: String = "qwen3:8b",
    var aiEndpoint: String = "http://192.168.66.181:11434",
    var aiApiPath: String = "/api/generate",
    var aiTemperature: Double = 0.7,
    var aiMaxTokens: Int = 2048,
    var aiTimeout: Int = 30000,
    var aiRetryCount: Int = 3,
    var selectedPromptTemplate: String = "简洁代码评审",
    var customPromptTemplates: MutableList<PromptTemplateData> = mutableListOf()
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
    
    companion object {
        fun getInstance(): CodeReviewerSettingsService {
            return ApplicationManager.getApplication().getService(CodeReviewerSettingsService::class.java)
        }
    }
    
    override fun getState(): CodeReviewerState = state
    
    override fun loadState(state: CodeReviewerState) {
        XmlSerializerUtil.copyBean(state, this.state)
        ensureDefaultValues()
        initializeDefaultTemplates()
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
        if (state.selectedPromptTemplate.isBlank() || state.selectedPromptTemplate == "Default Code Review") {
            state.selectedPromptTemplate = "简洁代码评审"
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
        val defaultTemplates = listOf(
            PromptTemplate.DEFAULT_TEMPLATE,
            PromptTemplate.BE_TEMPLATE,
            PromptTemplate.FE_TEMPLATE,
            PromptTemplate.DOC_TEMPLATE,
            PromptTemplate.DETAILED_TEMPLATE
        )
        
        val customTemplates = state.customPromptTemplates.map { data ->
            PromptTemplate(
                name = data.name,
                template = data.template,
                description = data.description,
                isDefault = data.isDefault
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
    
    fun getSelectedPromptTemplate(): PromptTemplate {
        return getAvailablePromptTemplates().find { it.name == state.selectedPromptTemplate }
            ?: PromptTemplate.DEFAULT_TEMPLATE
    }
    
    fun setSelectedPromptTemplate(templateName: String) {
        state.selectedPromptTemplate = templateName
    }

    
    private fun initializeDefaultTemplates() {
        if (state.customPromptTemplates.isEmpty()) {
            // Ensure we have some default state
            state.selectedPromptTemplate = PromptTemplate.DEFAULT_TEMPLATE.name
        }
    }
}