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
    
    private fun createHttpClient(timeoutMs: Int): OkHttpClient {
        val timeoutSeconds = (timeoutMs / 1000L).coerceAtLeast(60L) // 最少60秒
        return OkHttpClient.Builder()
            .readTimeout(timeoutSeconds, TimeUnit.SECONDS) // 读取超时使用配置值
            .retryOnConnectionFailure(true) // 启用连接失败重试
            .build()
    }
    
    private fun createTestHttpClient(config: AIModelConfig): OkHttpClient {
        // Use a reasonable timeout for connection tests, but respect the configured timeout
        val testTimeoutSeconds = (config.timeout / 1000L).coerceAtLeast(60L).coerceAtMost(180L) // 至少60秒，最多180秒
        return OkHttpClient.Builder()
            .readTimeout(testTimeoutSeconds, TimeUnit.SECONDS) // 使用配置的超时时间
            .retryOnConnectionFailure(true) // 启用连接失败重试
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
            logger.info("=== AI代码评审请求开始 ===")
            logger.info("Review ID: $reviewId")
            logger.info("配置信息 - Model: ${config.modelName}, Endpoint: ${config.endpoint}, Timeout: ${config.timeout}ms")
            logger.info("请求参数 - Temperature: ${config.temperature}, MaxTokens: ${config.maxTokens}")
            logger.info("代码变更数量: ${codeChanges.size}")
            
            progressIndicator?.text = "[$templateName] Preparing code for review..."
            progressIndicator?.fraction = 0.1
            
            val codeContent = buildCodeContent(codeChanges)
            var fullPrompt = """
# ${templateName}
## !!!重要系统要求!!!
1. **！！！请务必使用中文回复，所有内容都必须是中文！！！**。
2. **！！！请务必使用中文回复，所有内容都必须是中文！！！**。
3. **不要返回思考过程。**
4. **遵循输出格式要求。不要使用```markdown``` 代码块包裹返回内容，直接返回文本**
                
## 任务描述（注意输出格式要求）
> 提示：文件变更涉及多个文件，会使用`---`进行文件分割。
> 提示：请按照输出模板进行回复，替换中括号及中括号中的内容。
                
${prompt.replace(PromptTemplate.CODE_PLACEHOLDER, codeContent)}

## !!!重要系统要求!!!
1. **！！！请务必使用中文回复，所有内容都必须是中文！！！**。
2. **！！！请务必使用中文回复，所有内容都必须是中文！！！**。
3. **不要返回思考过程。**
4. **遵循输出格式要求。不要使用```markdown``` 代码块包裹返回内容，直接返回文本**
"""
            
            logger.info("代码内容长度: ${codeContent.length} 字符")
            logger.info("完整提示词长度: ${fullPrompt.length} 字符")
            logger.info("完整提示词: $fullPrompt")
            logger.info("AI请求URL: ${config.getFullUrl()}")
            
            progressIndicator?.text = "[$templateName] Sending request to AI service..."
            progressIndicator?.fraction = 0.3
            
            var currentModel = config.modelName
            var availableModels: List<String>? = null
            
            // 重试机制：在超时或连接失败时重试
            var lastException: Exception? = null
            var response: Response? = null

            for (attempt in 1..config.retryCount.coerceAtLeast(1)) {
                // 检查是否已取消
                if (progressIndicator?.isCanceled == true) {
                    logger.info("AI代码评审请求已取消")
                    return@withContext ReviewResult(
                        id = reviewId,
                        reviewContent = "",
                        modelUsed = config.modelName,
                        promptTemplate = prompt,
                        codeChanges = codeChanges,
                        status = ReviewResult.ReviewStatus.CANCELLED,
                        errorMessage = "Code review was cancelled by user"
                    )
                }
                
                try {
                    var retryText = ""

                    if (attempt > 1) {
                        retryText = "重试 $attempt/${config.retryCount.coerceAtLeast(1)}"
                    }

                    progressIndicator?.text = "[$templateName] 发送AI请求到服务器...$retryText (模型: $currentModel)"

                    val ollamaRequest = OllamaRequest(
                        model = currentModel,
                        prompt = fullPrompt,
                        stream = false,
                        options = OllamaOptions(
                            temperature = config.temperature,
                            top_p = 0.9,
                            top_k = 40,
                            num_predict = config.maxTokens
                        )
                    )

                    response = sendOllamaRequest(ollamaRequest, config)

                    if (response.isSuccessful) {
                        logger.info("请求成功 - 尝试次数: $attempt, 模型: $currentModel")
                        break
                    } else {
                        val errorMessage = "HTTP ${response.code}: ${response.message}"
                        logger.warn("请求失败 - 尝试 $attempt/${config.retryCount.coerceAtLeast(1)}: $errorMessage")

                        if (attempt < config.retryCount.coerceAtLeast(1)) {
                            val delay = 2000L * attempt // 指数退避：2s, 4s, 6s...
                            logger.info("等待 ${delay}ms 后进行重试...")
                            kotlinx.coroutines.delay(delay)
                        }
                    }
                } catch (e: Exception) {
                    lastException = e
                    val errorType = when (e) {
                        is java.net.SocketTimeoutException -> "超时"
                        is java.net.ConnectException -> "连接拒绝"
                        is java.net.UnknownHostException -> "主机不可达"
                        is IOException -> "IO异常"
                        else -> e.javaClass.simpleName
                    }

                    logger.warn("请求异常 - 尝试 $attempt/${config.retryCount.coerceAtLeast(1)}: $errorType - ${e.message}")

                    // 对于超时错误，提供额外的诊断信息并尝试切换模型
                    if (e is java.net.SocketTimeoutException) {
                        logger.warn("超时诊断 - 当前超时配置: ${config.timeout}ms, 建议增加超时时间或检查网络连接")
                        logger.warn("或者考虑使用更小的模型或减少代码内容长度")
                        
                        // 尝试切换模型
                        if (availableModels == null) {
                            availableModels = try {
                                getAvailableModels()
                            } catch (modelError: Exception) {
                                logger.warn("获取模型列表失败: ${modelError.message}")
                                getDefaultModels()
                            }
                        }
                        
                        // 如果有多个模型可用且当前模型不是最后一个，则切换到下一个模型
                        if (availableModels?.size ?: 0 > 1) {
                            val currentIndex = availableModels?.indexOf(currentModel) ?: 0
                            val nextIndex = if (currentIndex < (availableModels?.size ?: 0) - 1) currentIndex + 1 else 0
                            currentModel = availableModels?.get(nextIndex) ?: currentModel
                            logger.info("超时后切换模型: $currentModel")
                        }
                    }

                    if (attempt < config.retryCount.coerceAtLeast(1)) {
                        val delay = 2000L * attempt // 指数退避
                        logger.info("等待 ${delay}ms 后进行重试...")
                        kotlinx.coroutines.delay(delay)
                    }
                }
            }

            progressIndicator?.text = "[${templateName}] 处理AI响应..."
            progressIndicator?.fraction = 0.8
            
            // 检查是否有成功的响应
            if (response?.isSuccessful == true) {
                val responseBody = response.body?.string() ?: ""
                val totalTime = System.currentTimeMillis() - startTime
                
                logger.info("评审响应成功 - HTTP ${response.code}, 响应体长度: ${responseBody.length} 字符")
                logger.info("总耗时: ${totalTime}ms")
                
                val ollamaResponse = objectMapper.readValue<OllamaResponse>(responseBody)
                var reviewContent = ollamaResponse.getContent()

                logger.info("AI 相应数据：$reviewContent");
                
                // 处理AI响应，移除思考过程
                reviewContent = PromptTemplate.processAIResponse(reviewContent)
                
                logger.info("AI评审内容长度: ${reviewContent.length} 字符")
                logger.info("AI模型: ${ollamaResponse.model ?: "未知"}")
                logger.info("处理完成 - Done: ${ollamaResponse.done}")
                logger.info("=== AI代码评审请求完成 ===\n")
                
                progressIndicator?.fraction = 1.0
                
                return@withContext ReviewResult(
                    id = reviewId,
                    reviewContent = reviewContent,
                    modelUsed = currentModel, // 使用实际使用的模型名称
                    promptTemplate = prompt,
                    codeChanges = codeChanges,
                    status = ReviewResult.ReviewStatus.SUCCESS
                )
            } else {
                // 所有重试失败，抛出最后一次的异常或响应错误
                val totalTime = System.currentTimeMillis() - startTime
                
                if (lastException != null) {
                    logger.error("所有重试都失败，最后一次异常: ${lastException.message}")
                    throw lastException
                } else if (response != null) {
                    val errorBody = response.body?.string() ?: "Unknown error"
                    val errorMessage = "HTTP ${response.code}: ${response.message} - $errorBody"
                    logger.error("所有重试都失败 - $errorMessage")
                    logger.info("请求URL: ${config.getFullUrl()}")
                    logger.info("失败耗时: ${totalTime}ms")
                    logger.info("=== AI代码评审请求失败 ===\n")
                    throw IOException(errorMessage)
                } else {
                    val errorMessage = "所有重试都失败，无有收到任何响应"
                    logger.error(errorMessage)
                    throw IOException(errorMessage)
                }
            }
            
        } catch (e: Exception) {
            val totalTime = System.currentTimeMillis() - startTime
            val errorType = when (e) {
                is java.net.SocketTimeoutException -> "超时"
                is java.net.ConnectException -> "连接拒绝"
                is java.net.UnknownHostException -> "主机不可达"
                is IOException -> "IO异常"
                else -> e.javaClass.simpleName
            }
            
            logger.error("代码评审异常 - 类型: $errorType, 消息: ${e.message}", e)
            logger.info("请求配置 - URL: ${config.getFullUrl()}, Model: ${config.modelName}")
            logger.info("超时配置: ${config.timeout}ms, 重试次数: ${config.retryCount}")
            logger.info("异常耗时: ${totalTime}ms")
            
            // 根据错误类型提供针对性建议
            val userFriendlyMessage = when (e) {
                is java.net.SocketTimeoutException -> 
                    "请求超时。建议：1) 增加超时时间(${config.timeout}ms -> ${config.timeout * 2}ms); 2) 检查网络连接; 3) 使用更小的模型"
                is java.net.ConnectException -> 
                    "连接被拒绝。请检查：1) Ollama服务是否启动; 2) 端口配置是否正确(${config.endpoint}); 3) 防火墙设置"
                is java.net.UnknownHostException -> 
                    "主机不可达。请检查：1) 端点地址是否正确(${config.endpoint}); 2) 网络连接是否正常"
                else -> e.message ?: "Unknown error occurred"
            }
            
            logger.info("=== AI代码评审请求异常结束 ===\n")
            
            return@withContext ReviewResult(
                id = reviewId,
                reviewContent = "",
                modelUsed = config.modelName,
                promptTemplate = prompt,
                codeChanges = codeChanges,
                status = ReviewResult.ReviewStatus.ERROR,
                errorMessage = userFriendlyMessage
            )
        }
    }
    
    override suspend fun testConnection(): Boolean = withContext(Dispatchers.IO) {
        val startTime = System.currentTimeMillis()
        
        try {
            val config = getModelConfig()
            logger.info("=== AI服务连接测试开始 ===")
            logger.info("测试配置 - Endpoint: ${config.endpoint}, Model: ${config.modelName}")
            logger.info("测试URL: ${config.getFullUrl()}")
            logger.info("超时配置 - 连接: 30s, 读取: ${(config.timeout / 1000L).coerceAtLeast(60L).coerceAtMost(180L)}s, 写入: 30s")
            
            val testRequest = OllamaRequest(
                model = config.modelName,
                prompt = "Hello, please respond with 'OK' if you can see this message.",
                stream = false,
                options = OllamaOptions(num_predict = 10)
            )
            
            logger.info("发送测试请求 - 提示词: '${testRequest.prompt}'")
            
            val response = sendTestRequest(testRequest, config)
            val isSuccessful = response.isSuccessful
            val totalTime = System.currentTimeMillis() - startTime
            
            if (isSuccessful) {
                logger.info("连接测试成功 - HTTP ${response.code}")
                val responseBody = response.body?.string() ?: ""
                logger.info("测试响应体长度: ${responseBody.length} 字符")
                logger.info("测试耗时: ${totalTime}ms")
                
                if (responseBody.isNotEmpty()) {
                    try {
                        val ollamaResponse = objectMapper.readValue<OllamaResponse>(responseBody)
                        val testContent = ollamaResponse.getContent()
                        
                        // 简化的测试响应处理
                        if (testContent.isNotEmpty()) {
                            logger.info("AI响应内容: '${testContent.take(100)}${if(testContent.length > 100) "..." else ""}'")
                        } else {
                            logger.info("AI响应内容: '空响应'")
                        }
                    } catch (e: Exception) {
                        logger.warn("解析测试响应失败，但连接成功: ${e.message}")
                    }
                }
            } else {
                logger.error("连接测试失败 - HTTP ${response.code}: ${response.message}")
                logger.info("失败耗时: ${totalTime}ms")
            }
            
            logger.info("=== AI服务连接测试结束 ===\n")
            return@withContext isSuccessful
        } catch (e: Exception) {
            val totalTime = System.currentTimeMillis() - startTime
            
            logger.error("连接测试异常 - 类型: ${e.javaClass.simpleName}, 消息: ${e.message}", e)
            logger.info("异常耗时: ${totalTime}ms")
            logger.info("=== AI服务连接测试异常结束 ===\n")
            return@withContext false
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
        val config = getModelConfig()
        val modelsUrl = "${config.endpoint.trimEnd('/')}/api/tags"
        try {
            
            logger.info("=== 获取可用模型列表开始 ===")
            logger.info("模型列表API地址: $modelsUrl")
            
            val request = Request.Builder()
                .url(modelsUrl)
                .get()
                .build()
            
            // Create test client for simple API call
            val client = createTestHttpClient(config)
            val response = client.newCall(request).execute()
            
            if (response.isSuccessful) {
                val responseBody = response.body?.string() ?: ""
                logger.info("获取模型列表成功 - HTTP ${response.code}, 响应体长度: ${responseBody.length} 字符")
                
                val modelsResponse = objectMapper.readValue<OllamaModelsResponse>(responseBody)
                val modelNames = modelsResponse.models.map { it.name }.sorted()
                
                logger.info("解析到 ${modelNames.size} 个模型: ${modelNames.joinToString(", ")}")
                
                // 记录模型详细信息
                modelsResponse.models.forEach { model ->
                    logger.debug("模型详情 - 名称: ${model.name}, 大小: ${model.size ?: "未知"}, 修改时间: ${model.modified_at ?: "未知"}")
                }
                
                logger.info("=== 获取可用模型列表完成 ===\n")
                
                return@withContext modelNames
            } else {
                val errorBody = response.body?.string() ?: "无错误信息"
                logger.warn("获取模型列表失败 - HTTP ${response.code}: ${response.message}")
                logger.warn("错误响应体: $errorBody")
                logger.info("使用默认模型列表: ${getDefaultModels().joinToString(", ")}")
                logger.info("=== 获取可用模型列表结束(使用默认) ===\n")
                return@withContext getDefaultModels()
            }
        } catch (e: Exception) {
            logger.error("获取模型列表异常 - 类型: ${e.javaClass.simpleName}, 消息: ${e.message}", e)
            logger.info("请求URL: $modelsUrl")
            logger.info("使用默认模型列表: ${getDefaultModels().joinToString(", ")}")
            logger.info("=== 获取可用模型列表异常结束 ===\n")
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
            return "暂无代码需要评审。"
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
    
    private suspend fun sendTestRequest(
        request: OllamaRequest,
        config: AIModelConfig
    ): Response = withContext(Dispatchers.IO) {
        
        val jsonBody = objectMapper.writeValueAsString(request)
        logger.info("发送测试请求 - URL: ${config.getFullUrl()}")
        logger.info("测试请求参数 - Model: ${request.model}, Prompt: '${request.prompt}'")
        logger.debug("测试请求体: $jsonBody")
        
        val requestBody = jsonBody.toRequestBody("application/json".toMediaType())
        
        val httpRequest = Request.Builder()
            .url(config.getFullUrl())
            .post(requestBody)
            .build()
        
        // Use test client with configured timeout
        val client = createTestHttpClient(config)
        val response = client.newCall(httpRequest).execute()
        
        logger.info("测试请求响应 - HTTP ${response.code}")
        
        if (!response.isSuccessful) {
            val errorBody = response.body?.string() ?: "No error body"
            logger.error("测试请求失败 - HTTP ${response.code}: ${response.message}")
            logger.error("错误响应体: $errorBody")
        }
        
        response
    }

    private suspend fun sendOllamaRequest(
        request: OllamaRequest,
        config: AIModelConfig
    ): Response = withContext(Dispatchers.IO) {
        
        val jsonBody = objectMapper.writeValueAsString(request)
        logger.info("发送AI评审请求 - URL: ${config.getFullUrl()}")
        logger.info("请求参数 - Model: ${request.model}, Stream: ${request.stream}")
        logger.info("选项配置 - Temperature: ${request.options?.temperature}, TopP: ${request.options?.top_p}, MaxTokens: ${request.options?.num_predict}")
        logger.debug("请求体长度: ${jsonBody.length} 字符")
        // 注意: 不记录完整的请求体，因为可能包含大量代码内容
        
        val requestBody = jsonBody.toRequestBody("application/json".toMediaType())
        
        val httpRequest = Request.Builder()
            .url(config.getFullUrl())
            .post(requestBody)
            .build()
        
        // Create client with configured timeout
        val client = createHttpClient(config.timeout)
        val startTime = System.currentTimeMillis()
        val response = client.newCall(httpRequest).execute()
        val duration = System.currentTimeMillis() - startTime
        
        logger.info("评审请求响应 - HTTP ${response.code}, 耗时: ${duration}ms")
        
        if (!response.isSuccessful) {
            val errorBody = response.body?.string() ?: "No error body"
            logger.error("AI评审请求失败 - HTTP ${response.code}: ${response.message}")
            logger.error("错误响应体: $errorBody")
            // Don't consume response body here, let caller handle it
        }

        // 移除这个日志语句，因为它会消耗response body
        // logger.info("响应：${response.body?.string()}")
        
        response
    }
}