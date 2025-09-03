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

        val settingsService = CodeReviewerSettingsService.getInstance()
        
        // 根据设置决定是否进行内容提取
        val displayContent = if (settingsService.isContentExtractionEnabled()) {
            val extracted = extractReviewContent(reviewContent)
            
            // 如果开启了调试模式，显示对比
            if (settingsService.isDebugModeEnabled() && extracted != reviewContent.trim()) {
                """
                ## 🔧 调试信息
                **原始内容长度:** ${reviewContent.length} 字符
                **提取内容长度:** ${extracted.length} 字符
                **压缩比例:** ${String.format("%.1f", (1 - extracted.length.toDouble() / reviewContent.length) * 100)}%
                
                ---
                
                $extracted
                
                ---
                
                ## 📝 原始内容
                ```
                $reviewContent
                ```
                """.trimIndent()
            } else {
                extracted
            }
        } else {
            reviewContent
        }
        
        builder.append("## Review Feedback\n\n")
        builder.append(displayContent)

        if (codeChanges.isNotEmpty()) {
            builder.append("\n\n## Reviewed Changes\n\n")
            codeChanges.forEach { change ->
                builder.append("- ${change.filePath} (${change.changeType})\n")
            }
            builder.append("\n")
        }
        
        return builder.toString()
    }
    
    /**
     * 智能提取评审内容，过滤掉AI的思考过程和冗余内容
     */
    private fun extractReviewContent(content: String): String {
        val lines = content.lines()
        val reviewKeywords = listOf(
            "📝 评审总结", "评审总结", "## 📝", "## 评审总结",
            "🔍 发现的问题", "发现的问题", "## 🔍", "## 发现的问题", 
            "💡 优化建议", "优化建议", "## 💡", "## 优化建议",
            "## Code Review", "## 代码评审", "代码评审报告", "Review Feedback",
            "## 问题", "## 建议", "## Problem", "## Suggestion"
        )
        
        // 查找评审内容开始的位置
        var startIndex = -1
        for (i in lines.indices) {
            val line = lines[i].trim()
            if (reviewKeywords.any { keyword -> line.contains(keyword, ignoreCase = true) }) {
                // 找到评审关键词，从这里开始提取
                startIndex = i
                break
            }
        }
        
        // 如果找到了评审内容的开始位置
        if (startIndex != -1) {
            val extractedLines = lines.drop(startIndex)
            val result = extractedLines.joinToString("\n").trim()
            
            // 如果提取的内容太短，可能提取有误，返回原内容
            return if (result.length > 50) {
                result
            } else {
                content.trim()
            }
        }
        
        // 如果没有找到关键词，尝试其他策略
        return fallbackExtraction(content)
    }
    
    /**
     * 后备提取策略：移除明显的思考过程内容
     */
    private fun fallbackExtraction(content: String): String {
        val thinkingPatterns = listOf(
            "好的，我现在", "我现在任务", "首先，我会", "让我来", "我需要",
            "接下来我", "现在我来", "我将对", "让我分析", "我来审查",
            "好的，我来", "我先", "现在开始", "让我检查", "我观察到",
            "好的，", "现在，", "首先，", "然后，", "接下来，",
            "我来对", "我会检查", "我来检查", "我来看看", "让我看看",
            "我发现", "我注意到", "我看到", "我分析", "我检查",
            "我会对", "我对", "我来", "我现在", "我先来",
            "让我们", "我们来", "我将", "这里我", "在这里",
            "我理解", "我明白", "根据代码", "通过分析", "经过检查"
        )
        
        val lines = content.lines()
        val filteredLines = mutableListOf<String>()
        var skipCurrentParagraph = false
        
        for (line in lines) {
            val trimmedLine = line.trim()
            
            // 空行重置跳过状态
            if (trimmedLine.isEmpty()) {
                skipCurrentParagraph = false
                filteredLines.add(line)
                continue
            }
            
            // 如果遇到markdown标题，立即包含并重置状态
            if (trimmedLine.startsWith("#")) {
                skipCurrentParagraph = false
                filteredLines.add(line)
                continue
            }
            
            // 检查是否是思考过程的开始
            val isThinkingLine = thinkingPatterns.any { pattern ->
                trimmedLine.startsWith(pattern, ignoreCase = true)
            }
            
            // 如果是思考过程行，跳过整个段落
            if (isThinkingLine) {
                skipCurrentParagraph = true
                continue
            }
            
            // 检查是否是有用的内容行（包含代码、问题描述等）
            val isUsefulContent = trimmedLine.contains("问题") || trimmedLine.contains("建议") || 
                    trimmedLine.contains("优化") || trimmedLine.contains("修改") ||
                    trimmedLine.contains("错误") || trimmedLine.contains("注意") ||
                    trimmedLine.contains("```") || trimmedLine.startsWith("-") ||
                    trimmedLine.startsWith("*") || trimmedLine.startsWith("1.") ||
                    trimmedLine.startsWith("2.") || trimmedLine.startsWith("3.") ||
                    trimmedLine.matches(Regex("^\\d+\\.")) ||
                    trimmedLine.contains(":") && (trimmedLine.length < 100)
            
            // 如果是有用内容，重置跳过状态
            if (isUsefulContent) {
                skipCurrentParagraph = false
            }
            
            // 如果不在跳过模式，则保留这行
            if (!skipCurrentParagraph) {
                filteredLines.add(line)
            }
        }
        
        val result = filteredLines.joinToString("\n").trim()
        
        // 如果过滤后内容太少，返回原内容
        return if (result.length > content.length * 0.2) {
            result
        } else {
            content.trim()
        }
    }
}