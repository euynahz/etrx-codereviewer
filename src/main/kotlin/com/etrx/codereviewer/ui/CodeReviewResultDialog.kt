package com.etrx.codereviewer.ui

import com.etrx.codereviewer.model.ReviewResult
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.components.JBScrollPane
import com.intellij.util.ui.JBUI
import com.intellij.openapi.ide.CopyPasteManager
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.datatransfer.StringSelection
import javax.swing.*

/**
 * Dialog for displaying code review results in a popup
 */
class CodeReviewResultDialog(
    private val project: Project,
    private val result: ReviewResult
) : DialogWrapper(project) {

    private val markdownViewer = MarkdownViewer()

    init {
        title = "AI Code Review Results - ${result.getDisplayTitle()}"
        setOKButtonText("Close")
        
        // Display the result immediately
        markdownViewer.displayResult(result)
        
        init()
        
        // Set appropriate size for the dialog - made larger and taller
        setSize(1200, 928)
        setResizable(true)
    }

    override fun createCenterPanel(): JComponent {
        val mainPanel = JPanel(BorderLayout()).apply {
            preferredSize = Dimension(1150, 800)
            border = JBUI.Borders.empty(16)
        }

        // Add markdown viewer directly without header
        mainPanel.add(markdownViewer.getComponent(), BorderLayout.CENTER)
        
        return JBScrollPane(mainPanel).apply {
            border = null
        }
    }

    // Removed createHeaderPanel method as header info is no longer needed

    override fun createActions(): Array<Action> {
        val copyAction = object : AbstractAction("Copy Raw Content") {
            override fun actionPerformed(e: java.awt.event.ActionEvent?) {
                val rawContent = result.getMarkdownContent()
                val selection = StringSelection(rawContent)
                CopyPasteManager.getInstance().setContents(selection)
            }
        }
        return arrayOf(copyAction, okAction)
    }

    override fun getPreferredFocusedComponent(): JComponent? {
        return markdownViewer.getComponent()
    }

    companion object {
        /**
         * Show the review result dialog
         */
        fun showDialog(project: Project, result: ReviewResult) {
            val dialog = CodeReviewResultDialog(project, result)
            dialog.show()
        }
    }
}