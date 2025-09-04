package com.etrx.codereviewer.action

import com.etrx.codereviewer.model.CodeChange
import com.etrx.codereviewer.service.CodeReviewerSettingsService
import com.etrx.codereviewer.service.OllamaReviewService
import com.etrx.codereviewer.ui.CodeReviewResultDialog
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import kotlinx.coroutines.runBlocking

/**
 * Base class for code review actions
 */
abstract class BaseCodeReviewAction : AnAction(), DumbAware {
    
    private val logger = Logger.getInstance(BaseCodeReviewAction::class.java)
    
    protected fun performCodeReview(project: Project, codeChanges: List<CodeChange>) {
        if (codeChanges.isEmpty()) {
            logger.info("没有找到可评审的代码变更")
            Messages.showInfoMessage(
                project,
                "No code changes found to review.",
                "Code Review"
            )
            return
        }
        
        val actionId = this.javaClass.simpleName
        logger.info("=== Action $actionId 发起代码评审 ===")
        logger.info("项目: ${project.name}")
        logger.info("代码变更数量: ${codeChanges.size}")
        
        codeChanges.forEachIndexed { index, change ->
            logger.info("变更 ${index + 1}: ${change.filePath} (${change.changeType})")
        }
        
        val settingsService = CodeReviewerSettingsService.getInstance()
        val reviewService = project.getService(OllamaReviewService::class.java)
        val promptTemplate = settingsService.getSelectedPromptTemplate()
        
        logger.info("使用提示模板: ${promptTemplate.name}")
        logger.info("模板描述: ${promptTemplate.description}")
        
        ProgressManager.getInstance().run(object : Task.Backgroundable(
            project, "AI Code Review (${promptTemplate.name})", true
        ) {
            override fun run(indicator: ProgressIndicator) {
                val taskStartTime = System.currentTimeMillis()
                
                try {
                    logger.info("开始执行代码评审任务...")
                    indicator.text = "Starting code review with template: ${promptTemplate.name}..."
                    indicator.fraction = 0.0
                    
                    val result = runBlocking {
                        reviewService.reviewCode(
                            codeChanges = codeChanges,
                            prompt = promptTemplate.template,
                            templateName = promptTemplate.name,
                            progressIndicator = indicator
                        )
                    }
                    
                    val taskDuration = System.currentTimeMillis() - taskStartTime
                    
                    ApplicationManager.getApplication().invokeLater {
                        logger.info("代码评审任务完成 - 状态: ${result.status}, 耗时: ${taskDuration}ms")
                        logger.info("评审内容长度: ${result.reviewContent.length} 字符")
                        showReviewResult(project, result)
                        
                        logger.info("=== Action $actionId 代码评审完成 ===\n")
                    }
                    
                } catch (e: Exception) {
                    val taskDuration = System.currentTimeMillis() - taskStartTime
                    
                    logger.error("代码评审任务异常 - 类型: ${e.javaClass.simpleName}, 消息: ${e.message}", e)
                    logger.info("任务耗时: ${taskDuration}ms")
                    
                    ApplicationManager.getApplication().invokeLater {
                        Messages.showErrorDialog(
                            project,
                            "Code review failed: ${e.message}",
                            "Review Error"
                        )
                        
                        logger.info("=== Action $actionId 代码评审异常结束 ===\n")
                    }
                }
            }
        })
    }
    
    private fun showReviewResult(project: Project, result: com.etrx.codereviewer.model.ReviewResult) {
        logger.info("显示评审结果 - ID: ${result.id}, 状态: ${result.status}")
        
        // Show result in popup dialog
        CodeReviewResultDialog.showDialog(project, result)
        
        // Only show error notifications, not success notifications
        if (!result.isSuccessful()) {
            logger.warn("评审结果显示错误通知: ${result.errorMessage}")
            Messages.showErrorDialog(
                project,
                result.errorMessage ?: "Review failed with unknown error.",
                "Review Error"
            )
        } else {
            logger.info("评审结果对话框已显示")
        }
    }
    
    override fun getActionUpdateThread(): ActionUpdateThread {
        return ActionUpdateThread.BGT
    }
}