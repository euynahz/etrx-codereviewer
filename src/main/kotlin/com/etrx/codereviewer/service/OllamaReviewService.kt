package com.etrx.codereviewer.service

import com.etrx.codereviewer.model.*
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.progress.ProgressIndicator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.*
import java.util.concurrent.TimeUnit

/**
 * Implementation of AIReviewService using Ollama API
 */
@Service(Service.Level.PROJECT)
class OllamaReviewService : AIReviewService {
    
    private val logger = Logger.getInstance(OllamaReviewService::class.java)
    private val objectMapper = jacksonObjectMapper()
    private val settingsService = CodeReviewerSettingsService.getInstance()
    
    private val httpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(120, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()
    }
    
    override suspend fun reviewCode(
        codeChanges: List<CodeChange>,
        prompt: String,
        progressIndicator: ProgressIndicator?
    ): ReviewResult = withContext(Dispatchers.IO) {
        
        val reviewId = UUID.randomUUID().toString()
        val config = getModelConfig()
        
        try {
            progressIndicator?.text = "Preparing code for review..."
            progressIndicator?.fraction = 0.1
            
            val codeContent = buildCodeContent(codeChanges)
            val fullPrompt = prompt.replace(PromptTemplate.CODE_PLACEHOLDER, codeContent)
            
            progressIndicator?.text = "Sending request to AI service..."
            progressIndicator?.fraction = 0.3
            
            val ollamaRequest = OllamaRequest(
                model = config.modelName,
                prompt = fullPrompt,
                stream = false,
                options = OllamaOptions(
                    temperature = config.temperature,
                    top_p = 0.9,
                    top_k = 40,
                    num_predict = config.maxTokens
                )
            )
            
            logger.info("Prepared Ollama request - model: ${config.modelName}, prompt length: ${fullPrompt.length}, temperature: ${config.temperature}, maxTokens: ${config.maxTokens}")
            
            val response = sendOllamaRequest(ollamaRequest, config)
            
            progressIndicator?.text = "Processing AI response..."
            progressIndicator?.fraction = 0.8
            
            if (response.isSuccessful) {
                val responseBody = response.body?.string() ?: ""
                val ollamaResponse = objectMapper.readValue<OllamaResponse>(responseBody)
                
                progressIndicator?.fraction = 1.0
                
                return@withContext ReviewResult(
                    id = reviewId,
                    reviewContent = ollamaResponse.response,
                    modelUsed = config.modelName,
                    promptTemplate = prompt,
                    codeChanges = codeChanges,
                    status = ReviewResult.ReviewStatus.SUCCESS
                )
            } else {
                val errorBody = response.body?.string() ?: "Unknown error"
                val errorMessage = "HTTP ${response.code}: ${response.message} - $errorBody"
                logger.warn("Ollama request failed: $errorMessage")
                throw IOException(errorMessage)
            }
            
        } catch (e: Exception) {
            logger.warn("Code review failed", e)
            return@withContext ReviewResult(
                id = reviewId,
                reviewContent = "",
                modelUsed = config.modelName,
                promptTemplate = prompt,
                codeChanges = codeChanges,
                status = ReviewResult.ReviewStatus.ERROR,
                errorMessage = e.message ?: "Unknown error occurred"
            )
        }
    }
    
    override suspend fun testConnection(): Boolean = withContext(Dispatchers.IO) {
        try {
            val config = getModelConfig()
            val testRequest = OllamaRequest(
                model = config.modelName,
                prompt = "Hello, please respond with 'OK' if you can see this message.",
                stream = false,
                options = OllamaOptions(num_predict = 10)
            )
            
            val response = sendOllamaRequest(testRequest, config)
            response.isSuccessful
        } catch (e: Exception) {
            logger.warn("Connection test failed", e)
            false
        }
    }
    
    override fun getModelConfig(): AIModelConfig {
        return settingsService.getAIModelConfig()
    }
    
    override fun updateModelConfig(config: AIModelConfig) {
        settingsService.updateAIModelConfig(config)
    }
    
    /**
     * Get available models from Ollama API
     */
    suspend fun getAvailableModels(): List<String> = withContext(Dispatchers.IO) {
        try {
            val config = getModelConfig()
            val modelsUrl = "${config.endpoint.trimEnd('/')}/api/tags"
            
            val request = Request.Builder()
                .url(modelsUrl)
                .get()
                .build()
            
            val response = httpClient.newCall(request).execute()
            
            if (response.isSuccessful) {
                val responseBody = response.body?.string() ?: ""
                val modelsResponse = objectMapper.readValue<OllamaModelsResponse>(responseBody)
                return@withContext modelsResponse.models.map { it.name }.sorted()
            } else {
                logger.warn("Failed to get models: HTTP ${response.code}: ${response.message}")
                return@withContext getDefaultModels()
            }
        } catch (e: Exception) {
            logger.warn("Failed to get available models", e)
            return@withContext getDefaultModels()
        }
    }
    
    private fun getDefaultModels(): List<String> {
        return listOf(
            "qwen3:8b",
            "qwen:7b",
            "qwen:14b",
            "llama3:8b",
            "llama3:70b",
            "codellama:7b",
            "codellama:13b",
            "mistral:7b",
            "deepseek-coder:6.7b",
            "deepseek-coder:33b"
        )
    }
    
    private fun buildCodeContent(codeChanges: List<CodeChange>): String {
        if (codeChanges.isEmpty()) {
            return "No code changes to review."
        }
        
        val builder = StringBuilder()
        codeChanges.forEachIndexed { index, change ->
            if (index > 0) {
                builder.append("\n\n---\n\n")
            }
            builder.append(change.getFormattedChange())
        }
        return builder.toString()
    }
    
    private suspend fun sendOllamaRequest(
        request: OllamaRequest,
        config: AIModelConfig
    ): Response = withContext(Dispatchers.IO) {
        
        val jsonBody = objectMapper.writeValueAsString(request)
        logger.info("Sending request to Ollama: ${config.getFullUrl()}")
        logger.info("Request body: $jsonBody")
        
        val requestBody = jsonBody.toRequestBody("application/json".toMediaType())
        
        val httpRequest = Request.Builder()
            .url(config.getFullUrl())
            .post(requestBody)
            .build()
        
        val response = httpClient.newCall(httpRequest).execute()
        
        if (!response.isSuccessful) {
            logger.warn("Ollama request failed: HTTP ${response.code} - ${response.message}")
            // Don't consume response body here, let caller handle it
        }
        
        response
    }
}