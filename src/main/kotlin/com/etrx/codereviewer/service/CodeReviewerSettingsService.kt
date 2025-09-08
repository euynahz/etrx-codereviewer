package com.etrx.codereviewer.service

import com.etrx.codereviewer.model.AIModelConfig
import com.etrx.codereviewer.model.PromptTemplate
import com.etrx.codereviewer.model.Provider
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
    var aiTimeout: Int = 300000,
    var aiRetryCount: Int = 3,
    var provider: String = Provider.OLLAMA.name,
    var openRouterApiKey: String = "",
    var selectedPromptTemplate: String = "简洁代码评审",
    var templatesDir: String = System.getProperty("user.home") + "\\.etrx-ai-codereview\\jetbrains\\etrx-ai-templates",
    var customPromptTemplates: MutableList<PromptTemplateData> = mutableListOf(),
    // Overrides for built-in default templates: identified by name, content replaces bundled template
    var defaultTemplateOverrides: MutableList<PromptTemplateData> = mutableListOf(),
    var reviewResultFilePath: String = ".ai-codereview",
    // 记录最近一次选择的 Ollama 模型，用于设置界面回显
    var lastSelectedOllamaModel: String = ""
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
    private fun saveTemplateToDirectory(template: PromptTemplate) {
        try {
            val dir = java.io.File(state.templatesDir)
            if (!dir.exists()) dir.mkdirs()
            val file = java.io.File(dir, template.name + ".md")
            file.writeText(template.template)
        } catch (e: Exception) {
            logger.warn("保存模板到目录失败: ${e.message}")
        }
    }
    
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
            state.aiEndpoint = if (state.provider == Provider.OPENROUTER.name) "https://openrouter.ai" else "http://192.168.66.181:11434"
        }
        if (state.aiApiPath.isBlank()) {
            // 统一为带前导斜杠的路径，避免与 UI 回显差异导致的 isModified 误判或覆盖
            state.aiApiPath = if (state.provider == Provider.OPENROUTER.name) "/api/v1/chat/completions" else "/api/generate"
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
        val providerEnum = Provider.valueOf(state.provider)
        val effectiveModelName =
            if (providerEnum == Provider.OLLAMA && state.lastSelectedOllamaModel.isNotBlank())
                state.lastSelectedOllamaModel
            else
                state.aiModelName

        return AIModelConfig(
            modelName = effectiveModelName,
            endpoint = state.aiEndpoint,
            apiPath = state.aiApiPath,
            temperature = state.aiTemperature,
            maxTokens = state.aiMaxTokens,
            timeout = state.aiTimeout,
            retryCount = state.aiRetryCount,
            provider = providerEnum,
            apiKey = state.openRouterApiKey
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
        state.provider = config.provider.name

        when (config.provider) {
            Provider.OLLAMA -> {
                // 记录最近选择的 Ollama 模型，确保设置界面重开时能够正确回显
                state.lastSelectedOllamaModel = config.modelName
            }
            Provider.OPENROUTER -> {
                state.openRouterApiKey = config.apiKey
            }
        }
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
        
        // Load templates from templatesDir (*.md)
        val fileTemplates = mutableListOf<PromptTemplate>()
        try {
            val dir = java.io.File(state.templatesDir)
            if (dir.exists() && dir.isDirectory) {
                dir.listFiles { f -> f.isFile && f.name.endsWith(".md", ignoreCase = true) }?.forEach { file ->
                    val name = file.name.removeSuffix(".md")
                    val content = file.readText()
                    fileTemplates.add(PromptTemplate(name = name, template = content, description = "", isDefault = false))
                }
            }
        } catch (e: Exception) {
            logger.warn("加载模板目录失败: ${state.templatesDir}, ${e.message}")
        }
        
        val customTemplates = state.customPromptTemplates.map { data ->
            PromptTemplate(
                name = data.name,
                template = data.template,
                description = data.description,
                isDefault = false
            )
        }
        
        // Merge: fileTemplates override defaults/custom by same name
        val merged = linkedMapOf<String, PromptTemplate>()
        (defaultTemplates + customTemplates + fileTemplates).forEach { t ->
            merged[t.name] = t
        }
        
        return merged.values.toList()
    }
    
    fun addCustomPromptTemplate(template: PromptTemplate) {
        val templateData = PromptTemplateData(
            name = template.name,
            template = template.template,
            description = template.description,
            isDefault = false
        )
        state.customPromptTemplates.add(templateData)
        // Persist to templates directory
        saveTemplateToDirectory(template)
    }
    
    fun removeCustomPromptTemplate(templateName: String) {
        // 1) 移除状态中的记录（如果存在）
        state.customPromptTemplates.removeIf { it.name == templateName }

        // 2) 无论状态中是否存在，都尝试删除 templatesDir 下对应文件
        try {
            val dir = java.io.File(state.templatesDir)
            val file = java.io.File(dir, "$templateName.md")
            if (file.exists()) {
                val deleted = file.delete()
                if (!deleted) {
                    // 文件可能被占用，退化为 JVM 退出时删除
                    file.deleteOnExit()
                    logger.warn("立即删除模板文件失败，已标记为退出时删除: ${file.absolutePath}")
                } else {
                    logger.info("已删除模板文件: ${file.absolutePath}")
                }
            } else {
                logger.info("模板文件不存在，无需删除: ${file.absolutePath}")
            }
        } catch (e: Exception) {
            logger.warn("删除模板文件失败: ${e.message}")
        }

        // 3) 如果被删除的模板当前被选中，恢复为默认模板
        if (state.selectedPromptTemplate == templateName) {
            state.selectedPromptTemplate = PromptTemplate.DEFAULT_TEMPLATE.name
        }
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
            return
        }
        saveTemplateToDirectory(template)
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
        // Do not override user's selected template here.
        // This method should only ensure directories exist or seed data if truly first run.
        // Ensure templates directory exists
        try {
            val dir = java.io.File(state.templatesDir)
            if (!dir.exists()) {
                dir.mkdirs()
            }
        } catch (e: Exception) {
            logger.warn("创建模板目录失败: ${state.templatesDir}, ${e.message}")
        }
    }
}