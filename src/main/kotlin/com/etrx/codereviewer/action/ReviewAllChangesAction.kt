package com.etrx.codereviewer.action

import com.etrx.codereviewer.model.CodeChange
import com.etrx.codereviewer.util.VcsChangeExtractor
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.changes.Change
import com.intellij.openapi.vcs.changes.ChangeListManager

/**
 * Action to review all changes in VCS
 */
class ReviewAllChangesAction : BaseCodeReviewAction() {
    
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val changes = super.extractAllChanges(project)
        val codeChanges = VcsChangeExtractor.extractCodeChanges(changes)
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
}