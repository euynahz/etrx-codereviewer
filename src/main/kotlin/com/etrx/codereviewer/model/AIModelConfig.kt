package com.etrx.codereviewer.model

/**
 * Configuration for AI model settings
 */
data class AIModelConfig(
    val modelName: String = "qwen3:8b",
    val endpoint: String = "http://192.168.66.181:11434",
    val apiPath: String = "/api/generate",
    val temperature: Double = 0.7,
    val maxTokens: Int = 2048,
    val timeout: Int = 30000, // milliseconds
    val retryCount: Int = 3
) {
    fun getFullUrl(): String = "${endpoint.trimEnd('/')}${apiPath}"
    
    fun isValid(): Boolean {
        return modelName.isNotBlank() && 
               endpoint.isNotBlank() && 
               apiPath.isNotBlank() &&
               temperature in 0.0..2.0 &&
               maxTokens > 0 &&
               timeout > 0 &&
               retryCount >= 0
    }
}