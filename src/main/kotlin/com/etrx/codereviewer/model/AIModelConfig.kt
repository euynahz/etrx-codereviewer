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
    val timeout: Int = 120000, // milliseconds (2 minutes)
    val retryCount: Int = 3
) {
    fun getFullUrl(): String {
        // 安全构建URL，移除可能的多余字符
        val cleanEndpoint = endpoint.trimEnd('/').trimEnd(',') // 移除末尾的斜杠和逗号
        val cleanApiPath = apiPath.trimStart('/') // 确保API路径以斜杠开头
        return "$cleanEndpoint/$cleanApiPath"
    }
    
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