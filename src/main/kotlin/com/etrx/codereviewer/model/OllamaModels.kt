package com.etrx.codereviewer.model

import com.fasterxml.jackson.annotation.JsonIgnoreProperties

/**
 * Request object for AI code review
 */
data class OllamaRequest(
    val model: String,
    val prompt: String,
    val stream: Boolean = false,
    val options: OllamaOptions = OllamaOptions()
)

/**
 * Options for Ollama API requests
 */
data class OllamaOptions(
    val temperature: Double = 0.7,
    val top_p: Double = 0.9,
    val top_k: Int = 40,
    val num_predict: Int = 2048
)

/**
 * Message object from Ollama Chat API
 */
@JsonIgnoreProperties(ignoreUnknown = true)
data class OllamaMessage(
    val role: String,
    val content: String
)

/**
 * Response object from Ollama API
 * 兼容Generate API (response字段) 和 Chat API (message字段)
 */
@JsonIgnoreProperties(ignoreUnknown = true)
data class OllamaResponse(
    val model: String,
    val created_at: String,
    val response: String? = null, // Generate API
    val message: OllamaMessage? = null, // Chat API
    val done: Boolean,
    val done_reason: String? = null,
    val context: List<Int>? = null,
    val total_duration: Long? = null,
    val load_duration: Long? = null,
    val prompt_eval_count: Int? = null,
    val prompt_eval_duration: Long? = null,
    val eval_count: Int? = null,
    val eval_duration: Long? = null
) {
    /**
     * 获取响应内容，兼容两种API格式
     */
    fun getContent(): String {
        return response ?: message?.content ?: ""
    }
}

/**
 * Model information from Ollama API
 */
@JsonIgnoreProperties(ignoreUnknown = true)
data class OllamaModel(
    val name: String,
    val size: Long? = null,
    val digest: String? = null,
    val modified_at: String? = null
)

/**
 * Response for Ollama models list API
 */
@JsonIgnoreProperties(ignoreUnknown = true)
data class OllamaModelsResponse(
    val models: List<OllamaModel>
)