package com.etrx.codereviewer.service

import com.etrx.codereviewer.model.AIModelConfig
import com.etrx.codereviewer.model.CodeChange
import com.etrx.codereviewer.model.ReviewResult
import com.etrx.codereviewer.model.Provider
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.progress.ProgressIndicator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.*
import java.util.concurrent.TimeUnit

/**
 * Implementation of AIReviewService using OpenRouter API (OpenAI-compatible)
 */
@Service(Service.Level.PROJECT)
class OpenRouterReviewService : AIReviewService {

    private val logger = Logger.getInstance(OpenRouterReviewService::class.java)
    private val objectMapper = jacksonObjectMapper()
    private val settingsService = CodeReviewerSettingsService.getInstance()

    private fun createHttpClient(timeoutMs: Int): OkHttpClient {
        val timeoutSeconds = (timeoutMs / 1000L).coerceAtLeast(60L)
        return OkHttpClient.Builder()
            .readTimeout(timeoutSeconds, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()
    }

    override suspend fun reviewCode(
        codeChanges: List<CodeChange>,
        prompt: String,
        templateName: String,
        progressIndicator: ProgressIndicator?
    ): ReviewResult = withContext(Dispatchers.IO) {
        val reviewId = UUID.randomUUID().toString()
        val config = getModelConfig()
        val startTime = System.currentTimeMillis()

        try {
            require(config.provider == Provider.OPENROUTER) { "Invalid provider for OpenRouterReviewService" }
            if (config.apiKey.isBlank()) {
                return@withContext ReviewResult(
                    id = reviewId,
                    reviewContent = "",
                    modelUsed = config.modelName,
                    promptTemplate = templateName,
                    codeChanges = codeChanges,
                    status = ReviewResult.ReviewStatus.ERROR,
                    errorMessage = "OpenRouter API Key is required."
                )
            }

            progressIndicator?.text = "[$templateName] Preparing request for OpenRouter..."
            progressIndicator?.fraction = 0.2

            val codeText = buildCodeContent(codeChanges)

            var fullPrompt = """
# $templateName
${prompt.replace("{code}", codeText)}
""".trimIndent()

            val url = config.getFullUrl()
            logger.info("OpenRouter 请求URL: $url, 模型: ${config.modelName}, 超时: ${config.timeout}")

            val client = createHttpClient(config.timeout)
            val mediaType = "application/json".toMediaType()

            val payload = mapOf(
                "model" to config.modelName,
                "messages" to listOf(mapOf("role" to "user", "content" to fullPrompt)),
                "temperature" to config.temperature,
                "max_tokens" to config.maxTokens
            )
            val body = objectMapper.writeValueAsString(payload).toRequestBody(mediaType)

            val request = Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer ${config.apiKey}")
                .post(body)
                .build()

            progressIndicator?.text = "[$templateName] Waiting for OpenRouter response..."
            progressIndicator?.fraction = 0.6

            val call = client.newCall(request)
            // cancel HTTP call if user cancels the progress
            val watcherJob = if (progressIndicator != null) {
                kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
                    while (true) {
                        if (progressIndicator.isCanceled) {
                            try { call.cancel() } catch (_: Throwable) {}
                            break
                        }
                        kotlinx.coroutines.delay(100)
                    }
                }
            } else null

            try {
                call.execute().use { response ->
                    val responseBody = response.body?.string().orEmpty()
                    if (!response.isSuccessful) {
                    logger.warn("OpenRouter 响应失败: ${response.code} - $responseBody")
                    return@withContext ReviewResult(
                        id = reviewId,
                        reviewContent = "",
                        modelUsed = config.modelName,
                        promptTemplate = templateName,
                        codeChanges = codeChanges,
                        status = ReviewResult.ReviewStatus.ERROR,
                        errorMessage = "OpenRouter request failed: ${response.code}"
                    )
                }
                val parsed: Map<String, Any?> = objectMapper.readValue(responseBody)
                val content = try {
                    val choices = parsed["choices"] as? List<*>
                    val first = choices?.firstOrNull() as? Map<*, *>
                    val msg = first?.get("message") as? Map<*, *>
                    msg?.get("content") as? String ?: ""
                } catch (e: Exception) { responseBody }

                return@withContext ReviewResult(
                    id = reviewId,
                    reviewContent = content,
                    modelUsed = config.modelName,
                    promptTemplate = templateName,
                    codeChanges = codeChanges,
                    status = ReviewResult.ReviewStatus.SUCCESS
                )
            }
            } finally {
                watcherJob?.cancel()
            }
        } catch (e: Exception) {
            // If the user cancelled the progress (or the HTTP call was cancelled), treat as CANCELLED
            if (progressIndicator?.isCanceled == true || (e is java.io.IOException && e.message?.contains("canceled", ignoreCase = true) == true)) {
                logger.info("OpenRouter 请求因用户取消而中止: ${e.message}")
                return@withContext ReviewResult(
                    id = reviewId,
                    reviewContent = "",
                    modelUsed = getModelConfig().modelName,
                    promptTemplate = templateName,
                    codeChanges = codeChanges,
                    status = ReviewResult.ReviewStatus.CANCELLED,
                    errorMessage = "Code review was cancelled by user"
                )
            }
            logger.error("OpenRouter 评审异常", e)
            return@withContext ReviewResult(
                id = reviewId,
                reviewContent = "",
                modelUsed = getModelConfig().modelName,
                promptTemplate = templateName,
                codeChanges = codeChanges,
                status = ReviewResult.ReviewStatus.ERROR,
                errorMessage = e.message
            )
        }
    }

    override suspend fun testConnection(progressIndicator: ProgressIndicator?): Boolean = withContext(Dispatchers.IO) {
        val config = getModelConfig()
        if (config.apiKey.isBlank()) return@withContext false
        val client = createHttpClient(config.timeout)
        val url = config.getFullUrl()
        val payload = mapOf(
            "model" to config.modelName,
            "messages" to listOf(mapOf("role" to "user", "content" to "ping")),
            "max_tokens" to 4
        )
        val body = objectMapper.writeValueAsString(payload).toRequestBody("application/json".toMediaType())
        val request = Request.Builder()
            .url(url)
            .addHeader("Authorization", "Bearer ${config.apiKey}")
            .post(body)
            .build()

        val call = client.newCall(request)
        // 监听取消，及时终止 HTTP 调用
        val watcherJob = if (progressIndicator != null) {
            kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
                while (true) {
                    if (progressIndicator.isCanceled) {
                        try { call.cancel() } catch (_: Throwable) {}
                        break
                    }
                    kotlinx.coroutines.delay(100)
                }
            }
        } else null

        try {
            call.execute().use { resp ->
                return@withContext resp.isSuccessful
            }
        } catch (e: Exception) {
            if (progressIndicator?.isCanceled == true) {
                logger.info("OpenRouter 测试连接因用户取消而中止: ${e.message}")
                return@withContext false
            }
            logger.warn("OpenRouter 测试失败: ${e.message}")
            return@withContext false
        } finally {
            watcherJob?.cancel()
        }
    }

    override fun getModelConfig(): AIModelConfig = settingsService.getAIModelConfig()

    override fun updateModelConfig(config: AIModelConfig) = settingsService.updateAIModelConfig(config)

    private fun buildCodeContent(codeChanges: List<CodeChange>): String {
        val builder = StringBuilder()
        codeChanges.forEachIndexed { index, change ->
            builder.append("### File #${index + 1}: ${change.filePath} (${change.changeType})\n")
            builder.append(change.getFormattedChange())
            builder.append("\n")
        }
        return builder.toString()
    }
}
