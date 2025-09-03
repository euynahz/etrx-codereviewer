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
 * Response object from Ollama API
 */
@JsonIgnoreProperties(ignoreUnknown = true)
data class OllamaResponse(
    val model: String,
    val created_at: String,
    val response: String,
    val done: Boolean,
    val done_reason: String? = null,
    val context: List<Int>? = null,
    val total_duration: Long? = null,
    val load_duration: Long? = null,
    val prompt_eval_count: Int? = null,
    val prompt_eval_duration: Long? = null,
    val eval_count: Int? = null,
    val eval_duration: Long? = null
)

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