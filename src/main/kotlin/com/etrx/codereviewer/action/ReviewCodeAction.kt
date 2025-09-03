package com.etrx.codereviewer.action

import com.etrx.codereviewer.model.CodeChange
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile

/**
 * Action to review selected code in editor
 */
class ReviewCodeAction : BaseCodeReviewAction() {
    
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val editor = e.getData(CommonDataKeys.EDITOR) ?: return
        val psiFile = e.getData(CommonDataKeys.PSI_FILE) ?: return
        
        val codeChanges = extractSelectedCode(editor, psiFile)
        performCodeReview(project, codeChanges)
    }
    
    override fun update(e: AnActionEvent) {
        val project = e.project
        val editor = e.getData(CommonDataKeys.EDITOR)
        val psiFile = e.getData(CommonDataKeys.PSI_FILE)
        
        val hasSelection = editor?.selectionModel?.hasSelection() == true
        val hasCode = editor != null && psiFile != null
        
        e.presentation.isEnabledAndVisible = project != null && hasCode && hasSelection
    }
    
    private fun extractSelectedCode(editor: Editor, psiFile: PsiFile): List<CodeChange> {
        val selectionModel = editor.selectionModel
        val selectedText = selectionModel.selectedText ?: return emptyList()
        
        val startLine = editor.document.getLineNumber(selectionModel.selectionStart)
        val endLine = editor.document.getLineNumber(selectionModel.selectionEnd)
        
        val codeChange = CodeChange(
            filePath = psiFile.virtualFile.path,
            oldContent = null,
            newContent = selectedText,
            changeType = CodeChange.ChangeType.MODIFIED,
            lineRange = startLine..endLine
        )
        
        return listOf(codeChange)
    }
}