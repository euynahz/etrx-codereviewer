package com.etrx.codereviewer.ui

import com.etrx.codereviewer.model.ReviewResult
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.ui.components.JBList
import com.intellij.ui.components.JBScrollPane
import com.intellij.util.ui.JBUI
import org.commonmark.parser.Parser
import org.commonmark.renderer.html.HtmlRenderer
import java.awt.BorderLayout
import java.awt.Dimension
import java.text.SimpleDateFormat
import javax.swing.*

/**
 * Tool window for displaying code review results
 * @deprecated This class is no longer used. Use CodeReviewResultDialog instead.
 */
@Deprecated("Use CodeReviewResultDialog instead")
class CodeReviewResultToolWindow(private val project: Project) {
    
    companion object {
        val TOOL_WINDOW_KEY = Key.create<CodeReviewResultToolWindow>("CodeReviewResultToolWindow")
    }
    
    private val mainPanel = JPanel(BorderLayout())
    private val reviewListModel = DefaultListModel<ReviewResult>()
    private val reviewList = JBList(reviewListModel)
    private val markdownViewer = MarkdownViewer()
    private val emptyLabel = JLabel("No review results to display", SwingConstants.CENTER)
    
    init {
        setupUI()
        setupListeners()
    }
    
    private fun setupUI() {
        // Configure list
        reviewList.selectionMode = ListSelectionModel.SINGLE_SELECTION
        reviewList.cellRenderer = ReviewResultListCellRenderer()
        
        // Create split pane
        val listScrollPane = JBScrollPane(reviewList).apply {
            preferredSize = Dimension(300, 200)
        }
        
        val rightPanel = JPanel(BorderLayout()).apply {
            add(markdownViewer.getComponent(), BorderLayout.CENTER)
        }
        
        val splitPane = JSplitPane(JSplitPane.HORIZONTAL_SPLIT, listScrollPane, rightPanel).apply {
            dividerLocation = 300
            resizeWeight = 0.3
        }
        
        // Setup main panel
        mainPanel.add(splitPane, BorderLayout.CENTER)
        
        // Show empty message initially
        showEmptyState()
    }
    
    private fun setupListeners() {
        reviewList.addListSelectionListener { e ->
            if (!e.valueIsAdjusting) {
                val selectedResult = reviewList.selectedValue
                if (selectedResult != null) {
                    markdownViewer.displayResult(selectedResult)
                } else {
                    markdownViewer.clear()
                }
            }
        }
    }
    
    fun displayResult(result: ReviewResult) {
        ApplicationManager.getApplication().invokeLater {
            // Add to list if not already present
            if (!reviewListModel.contains(result)) {
                reviewListModel.add(0, result) // Add to top
            }
            
            // Show content if we were showing empty state
            if (reviewListModel.size() == 1) {
                hideEmptyState()
            }
            
            // Select the new result
            reviewList.selectedIndex = 0
            
            // Explicitly display the result in the markdown viewer
            markdownViewer.displayResult(result)
            
            // Limit the number of results to keep memory usage reasonable
            while (reviewListModel.size() > 50) {
                reviewListModel.remove(reviewListModel.size() - 1)
            }
        }
    }
    
    private fun showEmptyState() {
        mainPanel.removeAll()
        mainPanel.add(emptyLabel, BorderLayout.CENTER)
        mainPanel.revalidate()
        mainPanel.repaint()
    }
    
    private fun hideEmptyState() {
        mainPanel.removeAll()
        
        val listScrollPane = JBScrollPane(reviewList).apply {
            preferredSize = Dimension(300, 200)
        }
        
        val rightPanel = JPanel(BorderLayout()).apply {
            add(markdownViewer.getComponent(), BorderLayout.CENTER)
        }
        
        val splitPane = JSplitPane(JSplitPane.HORIZONTAL_SPLIT, listScrollPane, rightPanel).apply {
            dividerLocation = 300
            resizeWeight = 0.3
        }
        
        mainPanel.add(splitPane, BorderLayout.CENTER)
        mainPanel.revalidate()
        mainPanel.repaint()
    }
    
    fun getContent(): JComponent = mainPanel
    
    /**
     * Custom cell renderer for review results
     */
    private class ReviewResultListCellRenderer : DefaultListCellRenderer() {
        private val dateFormat = SimpleDateFormat("MM/dd HH:mm")
        
        override fun getListCellRendererComponent(
            list: JList<*>?,
            value: Any?,
            index: Int,
            isSelected: Boolean,
            cellHasFocus: Boolean
        ): java.awt.Component {
            
            super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus)
            
            if (value is ReviewResult) {
                val statusIcon = when (value.status) {
                    ReviewResult.ReviewStatus.SUCCESS -> "✓"
                    ReviewResult.ReviewStatus.ERROR -> "✗"
                    ReviewResult.ReviewStatus.IN_PROGRESS -> "⏳"
                    ReviewResult.ReviewStatus.CANCELLED -> "⏹"
                }
                
                text = """
                    <html>
                    <b>$statusIcon ${value.id.take(8)}</b><br/>
                    <small>${dateFormat.format(java.sql.Timestamp.valueOf(value.timestamp))}</small><br/>
                    <small>${value.codeChanges.size} changes</small>
                    </html>
                """.trimIndent()
                
                border = JBUI.Borders.empty(8)
            }
            
            return this
        }
    }
}