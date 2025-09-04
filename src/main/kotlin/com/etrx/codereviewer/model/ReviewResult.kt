package com.etrx.codereviewer.model

import com.etrx.codereviewer.service.CodeReviewerSettingsService
import java.time.LocalDateTime

/**
 * Result of AI code review
 */
data class ReviewResult(
    val id: String,
    val reviewContent: String,
    val timestamp: LocalDateTime = LocalDateTime.now(),
    val modelUsed: String,
    val promptTemplate: String,
    val codeChanges: List<CodeChange> = emptyList(),
    val status: ReviewStatus = ReviewStatus.SUCCESS,
    val errorMessage: String? = null
) {
    enum class ReviewStatus {
        SUCCESS, ERROR, IN_PROGRESS, CANCELLED
    }
    
    fun isSuccessful(): Boolean = status == ReviewStatus.SUCCESS
    
    fun getDisplayTitle(): String {
        return "Review ${id.take(8)} - ${timestamp.toLocalDate()}"
    }
    
    fun getMarkdownContent(): String {
        val builder = StringBuilder()
        
        builder.append("# Code Review Results\n\n")
        
        if (status == ReviewStatus.ERROR && errorMessage != null) {
            builder.append("## Error\n\n")
            builder.append("```\n$errorMessage\n```\n\n")
            return builder.toString()
        }

        builder.append("## Review Feedback\n\n")
        builder.append(reviewContent)

        if (codeChanges.isNotEmpty()) {
            builder.append("\n\n## Reviewed Changes\n\n")
            codeChanges.forEach { change ->
                builder.append("- ${change.filePath} (${change.changeType})\n")
            }
            builder.append("\n")
        }
        
        return builder.toString()
    }
    

}