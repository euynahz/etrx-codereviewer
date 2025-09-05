package com.etrx.codereviewer.service

import com.etrx.codereviewer.model.ReviewResult
import com.etrx.codereviewer.model.CodeChange
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import java.io.File
import java.nio.file.Paths
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * Service for handling review result file operations
 */
@Service(Service.Level.PROJECT)
class ReviewResultFileService {
    
    private val logger = Logger.getInstance(ReviewResultFileService::class.java)
    private val settingsService = CodeReviewerSettingsService.getInstance()
    
    /**
     * Save review result to file and open it
     */
    fun saveAndOpenReviewResult(project: Project, result: ReviewResult): Boolean {
        return try {
            logger.info("=== 开始保存评审结果到文件 ===")
            logger.info("Project: ${project.name}")
            logger.info("Project BasePath: ${project.basePath}")
            logger.info("Review ID: ${result.id}")
            logger.info("Review Status: ${result.status}")
            logger.info("Review Content Length: ${result.reviewContent.length}")
            
            val configuredPath = settingsService.getReviewResultFilePath()
            logger.info("配置的文件路径: '$configuredPath'")
            
            val filePath = resolveFilePath(project)
            logger.info("解析后的完整文件路径: '$filePath'")
            
            val content = formatReviewResultContent(result)
            logger.info("格式化后的内容长度: ${content.length} 字符")
            logger.info("内容预览（前200字符）: ${content.take(200)}...")
            
            // Save to file
            val file = File(filePath)
            logger.info("目标文件对象: ${file.absolutePath}")
            logger.info("文件父目录: ${file.parentFile?.absolutePath}")
            logger.info("父目录是否存在: ${file.parentFile?.exists()}")
            
            val parentCreated = file.parentFile?.mkdirs() ?: false
            logger.info("父目录创建结果: $parentCreated（true=新创建，false=已存在或无需创建）")
            
            file.writeText(content, Charsets.UTF_8)
            logger.info("文件写入完成")
            logger.info("文件是否存在: ${file.exists()}")
            logger.info("文件大小: ${file.length()} bytes")
            logger.info("文件可读: ${file.canRead()}")
            logger.info("文件可写: ${file.canWrite()}")
            
            // Open file in editor
            logger.info("开始在IDE中打开文件...")
            val virtualFile = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(file)
            if (virtualFile != null) {
                logger.info("虚拟文件找到: ${virtualFile.path}")
                logger.info("虚拟文件是否有效: ${virtualFile.isValid}")
                logger.info("虚拟文件是否可写: ${virtualFile.isWritable}")
                
                val fileEditorManager = FileEditorManager.getInstance(project)
                logger.info("获取FileEditorManager: ${fileEditorManager.javaClass.simpleName}")
                
                val openedFiles = fileEditorManager.openFile(virtualFile, true)
                logger.info("打开文件结果: 打开了 ${openedFiles.size} 个编辑器")
                openedFiles.forEach { editor ->
                    logger.info("编辑器类型: ${editor.javaClass.simpleName}")
                }
            } else {
                logger.warn("无法在虚拟文件系统中找到文件: $filePath")
                logger.info("尝试刷新虚拟文件系统...")
                LocalFileSystem.getInstance().refresh(false)
                val retryVirtualFile = LocalFileSystem.getInstance().findFileByIoFile(file)
                if (retryVirtualFile != null) {
                    logger.info("重试后找到虚拟文件: ${retryVirtualFile.path}")
                    val openedFiles = FileEditorManager.getInstance(project).openFile(retryVirtualFile, true)
                    logger.info("重试打开文件结果: 打开了 ${openedFiles.size} 个编辑器")
                } else {
                    logger.error("重试后仍无法找到虚拟文件")
                }
            }
            
            logger.info("=== 评审结果保存完成 ===")
            true
        } catch (e: Exception) {
            logger.error("保存评审结果失败 - 异常类型: ${e.javaClass.simpleName}", e)
            logger.error("异常消息: ${e.message}")
            logger.error("异常堆栈: ${e.stackTraceToString()}")
            false
        }
    }
    
    /**
     * Resolve the full file path based on configuration
     */
    private fun resolveFilePath(project: Project): String {
        val configuredPath = settingsService.getReviewResultFilePath()
        logger.info("开始解析文件路径 - 配置路径: '$configuredPath'")
        
        // 生成日期时间格式化的文件名（精确到分钟）
        val dateTimeFormatter = DateTimeFormatter.ofPattern("yyyyMMdd-HHmm")
        val currentDateTime = LocalDateTime.now().format(dateTimeFormatter)
        val fileName = "ai-code-review-$currentDateTime.md"
        
        val resolvedPath = if (Paths.get(configuredPath).isAbsolute) {
            // Absolute path
            logger.info("识别为绝对路径")
            Paths.get(configuredPath, fileName).toString()
        } else {
            // Relative path (relative to project root)
            val projectPath = project.basePath ?: System.getProperty("user.dir")
            logger.info("识别为相对路径 - 项目根目录: '$projectPath'")
            logger.info("系统当前目录: '${System.getProperty("user.dir")}'")
            
            val resolved = Paths.get(projectPath, configuredPath, fileName).toString()
            logger.info("相对路径解析结果: '$resolved'")
            resolved
        }
        
        logger.info("最终解析的文件路径: '$resolvedPath'")
        return resolvedPath
    }
    
    /**
     * Format review result content for file output
     */
    private fun formatReviewResultContent(result: ReviewResult): String {
        logger.info("开始格式化评审结果内容")
        logger.info("原始评审内容长度: ${result.reviewContent.length}")
        logger.info("代码变更数量: ${result.codeChanges.size}")
        
        val timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
        val separator = "---"
        
        return buildString {
            appendLine("# AI代码评审报告")
            appendLine()
            appendLine("**生成时间**: $timestamp")
            appendLine("**评审ID**: ${result.id}")
            appendLine("**使用模型**: ${result.modelUsed}")
            appendLine("**评审状态**: ${getStatusDisplayName(result.status)}")
            appendLine()
            appendLine(separator)
            appendLine()
            
            if (result.status == ReviewResult.ReviewStatus.SUCCESS) {
                logger.info("处理成功的评审结果")
                // Process AI response to remove thinking process
                val processedContent = com.etrx.codereviewer.model.PromptTemplate.processAIResponse(result.reviewContent)
                logger.info("处理后的内容长度: ${processedContent.length}")
                logger.info("处理后内容预览（前100字符）: ${processedContent.take(100)}...")
                appendLine(processedContent)
            } else {
                logger.info("处理失败的评审结果")
                appendLine("## ❌ 评审失败")
                appendLine()
                appendLine("**错误信息**: ${result.errorMessage ?: "未知错误"}")
                appendLine()
                appendLine("请检查AI服务配置或网络连接，然后重试。")
            }
            
            appendLine()
            appendLine(separator)
            appendLine()
            
            logger.info("内容格式化完成，总长度: ${this.length} 字符")
        }
    }
    
    private fun getStatusDisplayName(status: ReviewResult.ReviewStatus): String {
        return when (status) {
            ReviewResult.ReviewStatus.SUCCESS -> "✅ 成功"
            ReviewResult.ReviewStatus.ERROR -> "❌ 失败"
            ReviewResult.ReviewStatus.IN_PROGRESS -> "⏳ 处理中"
            ReviewResult.ReviewStatus.CANCELLED -> "❎ 已取消"
        }
    }
    
    private fun getChangeTypeDisplayName(changeType: String): String {
        return when (changeType.lowercase()) {
            "add", "added" -> "➕ 新增"
            "modify", "modified", "edit", "edited" -> "📝 修改"
            "delete", "deleted", "remove", "removed" -> "🗑️ 删除"
            "rename", "renamed" -> "📂 重命名"
            else -> "📄 $changeType"
        }
    }
    
    private fun getFileExtension(fileName: String): String {
        val extension = fileName.substringAfterLast('.', "")
        return when (extension.lowercase()) {
            "kt" -> "kotlin"
            "java" -> "java"
            "js", "jsx" -> "javascript"
            "ts", "tsx" -> "typescript"
            "py" -> "python"
            "cpp", "cxx", "cc" -> "cpp"
            "c" -> "c"
            "h", "hpp" -> "cpp"
            "cs" -> "csharp"
            "go" -> "go"
            "rs" -> "rust"
            "php" -> "php"
            "rb" -> "ruby"
            "swift" -> "swift"
            "sql" -> "sql"
            "html" -> "html"
            "css" -> "css"
            "scss", "sass" -> "scss"
            "json" -> "json"
            "xml" -> "xml"
            "yaml", "yml" -> "yaml"
            "sh" -> "bash"
            "md" -> "markdown"
            else -> extension.ifEmpty { "text" }
        }
    }
}