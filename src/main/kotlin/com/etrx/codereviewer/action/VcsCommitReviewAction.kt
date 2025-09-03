package com.etrx.codereviewer.action

import com.etrx.codereviewer.model.CodeChange
import com.etrx.codereviewer.util.VcsChangeExtractor
import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.changes.ChangeListManager

/**
 * Action for VCS commit panel to review changes with AI
 */
class VcsCommitReviewAction : BaseCodeReviewAction() {
    
    init {
        templatePresentation.text = "Review with AI"
        templatePresentation.description = "Review code changes with AI before committing"
        templatePresentation.icon = AllIcons.Actions.IntentionBulb
    }
    
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val codeChanges = extractChangesFromCommitPanel(project)
        performCodeReview(project, codeChanges)
    }
    
    override fun update(e: AnActionEvent) {
        val project = e.project
        val hasVcs = project != null && 
                    ChangeListManager.getInstance(project).areChangeListsEnabled()
        
        val hasChanges = if (hasVcs) {
            val changeListManager = ChangeListManager.getInstance(project!!)
            changeListManager.allChanges.isNotEmpty()
        } else {
            false
        }
        
        e.presentation.isEnabledAndVisible = hasVcs && hasChanges
    }
    
    private fun extractChangesFromCommitPanel(project: Project): List<CodeChange> {
        val changeListManager = ChangeListManager.getInstance(project)
        val allChanges = changeListManager.allChanges.toList()
        
        return VcsChangeExtractor.extractCodeChanges(allChanges)
    }
}