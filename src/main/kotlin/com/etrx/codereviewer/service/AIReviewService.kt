package com.etrx.codereviewer.service

import com.etrx.codereviewer.model.AIModelConfig
import com.etrx.codereviewer.model.CodeChange
import com.etrx.codereviewer.model.ReviewResult
import com.intellij.openapi.progress.ProgressIndicator

/**
 * Interface for AI-powered code review services
 */
interface AIReviewService {
    
    /**
     * Review code changes using AI
     * @param codeChanges List of code changes to review
     * @param prompt The prompt template to use
     * @param templateName Name of the template being used
     * @param progressIndicator Progress indicator for UI feedback
     * @return ReviewResult containing the AI's feedback
     */
    suspend fun reviewCode(
        codeChanges: List<CodeChange>,
        prompt: String,
        templateName: String = "Default",
        progressIndicator: ProgressIndicator? = null
    ): ReviewResult
    
    /**
     * Test connection to the AI service
     * @return true if connection is successful, false otherwise
     */
    suspend fun testConnection(progressIndicator: ProgressIndicator? = null): Boolean
    
    /**
     * Get the current model configuration
     */
    fun getModelConfig(): AIModelConfig
    
    /**
     * Update the model configuration
     */
    fun updateModelConfig(config: AIModelConfig)
}