package com.etrx.codereviewer.action

import com.etrx.codereviewer.model.CodeChange
import com.etrx.codereviewer.util.VcsChangeExtractor
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.vcs.VcsDataKeys
import com.intellij.openapi.vcs.changes.Change
import com.intellij.openapi.vcs.changes.ChangeListManager

/**
 * Action to review selected changes in VCS
 */
class ReviewSelectedChangesAction : BaseCodeReviewAction() {
    
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val selectedChanges = getSelectedChanges(e)
        
        val codeChanges = if (selectedChanges.isNotEmpty()) {
            // 用户选中了具体的文件，使用选中的文件
            VcsChangeExtractor.extractCodeChanges(selectedChanges)
        } else {
            // 没有选中文件，询问用户是否评审所有变更
            val result = Messages.showYesNoDialog(
                project,
                "没有选中任何文件。是否要评审变更区中的所有变更？",
                "代码评审",
                "评审所有变更",
                "取消",
                Messages.getQuestionIcon()
            )
            
            if (result == Messages.YES) {
                extractAllChanges(project)
            } else {
                emptyList()
            }
        }
        
        if (codeChanges.isNotEmpty()) {
            performCodeReview(project, codeChanges)
        }
    }
    
    override fun update(e: AnActionEvent) {
        val project = e.project
        val hasVcs = project != null && 
                    ChangeListManager.getInstance(project).areChangeListsEnabled()
        
        e.presentation.isEnabledAndVisible = hasVcs
    }
    
    private fun getSelectedChanges(e: AnActionEvent): List<Change> {
        // 尝试从AnActionEvent获取选中的Change对象
        val changes = e.getData(VcsDataKeys.SELECTED_CHANGES)
        return changes?.toList() ?: emptyList()
    }
    
    private fun extractAllChanges(project: Project): List<CodeChange> {
        val changeListManager = ChangeListManager.getInstance(project)
        val allChanges = changeListManager.allChanges.toList()
        
        return VcsChangeExtractor.extractCodeChanges(allChanges)
    }
}