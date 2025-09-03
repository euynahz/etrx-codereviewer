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
    
    protected fun performCodeReview(project: Project, codeChanges: List<CodeChange>) {
        if (codeChanges.isEmpty()) {
            Messages.showInfoMessage(
                project,
                "No code changes found to review.",
                "Code Review"
            )
            return
        }
        
        val settingsService = CodeReviewerSettingsService.getInstance()
        val reviewService = project.getService(OllamaReviewService::class.java)
        val promptTemplate = settingsService.getSelectedPromptTemplate()
        
        ProgressManager.getInstance().run(object : Task.Backgroundable(
            project, "AI Code Review in Progress", true
        ) {
            override fun run(indicator: ProgressIndicator) {
                try {
                    indicator.text = "Starting code review..."
                    indicator.fraction = 0.0
                    
                    val result = runBlocking {
                        reviewService.reviewCode(
                            codeChanges = codeChanges,
                            prompt = promptTemplate.template,
                            progressIndicator = indicator
                        )
                    }
                    
                    ApplicationManager.getApplication().invokeLater {
                        showReviewResult(project, result)
                    }
                    
                } catch (e: Exception) {
                    ApplicationManager.getApplication().invokeLater {
                        Messages.showErrorDialog(
                            project,
                            "Code review failed: ${e.message}",
                            "Review Error"
                        )
                    }
                }
            }
        })
    }
    
    private fun showReviewResult(project: Project, result: com.etrx.codereviewer.model.ReviewResult) {
        // Show result in popup dialog
        CodeReviewResultDialog.showDialog(project, result)
        
        // Only show error notifications, not success notifications
        if (!result.isSuccessful()) {
            Messages.showErrorDialog(
                project,
                result.errorMessage ?: "Review failed with unknown error.",
                "Review Error"
            )
        }
    }
    
    override fun getActionUpdateThread(): ActionUpdateThread {
        return ActionUpdateThread.BGT
    }
}