package com.etrx.codereviewer.ui

import com.etrx.codereviewer.model.ReviewResult
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.components.JBScrollPane
import com.intellij.util.ui.JBUI
import com.intellij.openapi.ide.CopyPasteManager
import com.intellij.openapi.diagnostic.Logger
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

    private val logger = Logger.getInstance(CodeReviewResultDialog::class.java)
    private val markdownViewer = MarkdownViewer()

    init {
        logger.info("=== 创建 CodeReviewResultDialog ===")
        logger.info("Review ID: ${result.id}")
        logger.info("Review Status: ${result.status}")
        logger.info("Review Content Length: ${result.reviewContent.length}")
        
        title = "AI Code Review Results - ${result.getDisplayTitle()}"
        setOKButtonText("Close")
        
        // Display the result immediately
        logger.info("开始显示评审结果...")
        markdownViewer.displayResult(result)
        logger.info("评审结果已设置到 MarkdownViewer")
        
        init()
        
        // Set appropriate size for the dialog - made larger and taller
        setSize(1200, 928)
        setResizable(true)
        
        logger.info("=== CodeReviewResultDialog 初始化完成 ===")
    }

    override fun createCenterPanel(): JComponent {
        val mainPanel = JPanel(BorderLayout()).apply {
            preferredSize = Dimension(1150, 800)
            border = JBUI.Borders.empty(16)
        }

        // Add markdown viewer directly - it already has JBScrollPane internally
        mainPanel.add(markdownViewer.getComponent(), BorderLayout.CENTER)
        
        return mainPanel
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
    
    override fun dispose() {
        // Clean up markdown viewer resources
        markdownViewer.dispose()
        super.dispose()
    }

    companion object {
        private val logger = Logger.getInstance(CodeReviewResultDialog::class.java)
        
        /**
         * Show the review result dialog
         */
        fun showDialog(project: Project, result: ReviewResult) {
            logger.info("=== 开始显示评审结果对话框 ===")
            logger.info("Project: ${project.name}")
            logger.info("Result ID: ${result.id}")
            logger.info("Result Status: ${result.status}")
            
            try {
                val dialog = CodeReviewResultDialog(project, result)
                logger.info("对话框已创建，即将显示...")
                dialog.show()
                logger.info("对话框显示完成")
            } catch (e: Exception) {
                logger.error("显示评审结果对话框失败", e)
                throw e
            }
            
            logger.info("=== 评审结果对话框显示结束 ===")
        }
    }
}