package com.etrx.codereviewer.ui

import com.etrx.codereviewer.model.ReviewResult
import com.intellij.ui.jcef.JBCefBrowser
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import com.intellij.ide.ui.LafManager
import org.commonmark.parser.Parser
import org.commonmark.renderer.html.HtmlRenderer
import org.commonmark.ext.gfm.tables.TablesExtension
import org.commonmark.ext.gfm.strikethrough.StrikethroughExtension
import org.commonmark.ext.autolink.AutolinkExtension
import java.awt.BorderLayout
import java.awt.Color
import javax.swing.*

/**
 * Component for displaying markdown content with HTML rendering
 */
class MarkdownViewer {
    
    private val mainPanel = JPanel(BorderLayout())
    
    // Configure parser with GFM extensions
    private val extensions = listOf(
        TablesExtension.create(),
        StrikethroughExtension.create(),
        AutolinkExtension.create()
    )
    
    private val parser = Parser.builder()
        .extensions(extensions)
        .build()
        
    private val renderer = HtmlRenderer.builder()
        .extensions(extensions)
        .build()
    
    // Try to use JCEF browser for better rendering, fallback to JTextPane
    private val browser: JBCefBrowser? = try {
        JBCefBrowser()
    } catch (e: Exception) {
        null
    }
    
    private val textPane: JTextPane? = if (browser == null) {
        JTextPane().apply {
            contentType = "text/html"
            isEditable = false
            border = JBUI.Borders.empty(16)
        }
    } else null
    
    init {
        setupUI()
        showWelcomeMessage()
    }
    
    private fun setupUI() {
        when {
            browser != null -> {
                mainPanel.add(browser.component, BorderLayout.CENTER)
            }
            textPane != null -> {
                val scrollPane = JScrollPane(textPane).apply {
                    border = null
                }
                mainPanel.add(scrollPane, BorderLayout.CENTER)
            }
            else -> {
                val fallbackLabel = JLabel("Markdown viewer not available", SwingConstants.CENTER)
                mainPanel.add(fallbackLabel, BorderLayout.CENTER)
            }
        }
    }
    
    fun displayResult(result: ReviewResult) {
        try {
            val markdownContent = result.getMarkdownContent()
            val htmlContent = convertMarkdownToHtml(markdownContent)
            
            when {
                browser != null -> {
                    browser.loadHTML(htmlContent)
                }
                textPane != null -> {
                    textPane.text = htmlContent
                    textPane.caretPosition = 0
                }
            }
        } catch (e: Exception) {
            // Fallback to plain text display if markdown parsing fails
            val errorContent = """
                <html><body style="font-family: monospace; padding: 16px; color: #d32f2f;">
                <h3>⚠️ Markdown parsing error</h3>
                <p>Failed to parse markdown content. Displaying raw content:</p>
                <hr>
                <pre style="background: #f5f5f5; padding: 12px; border-radius: 4px; white-space: pre-wrap;">
                ${result.getMarkdownContent().replace("<", "&lt;").replace(">", "&gt;")}
                </pre>
                <hr>
                <p><em>Error: ${e.message}</em></p>
                </body></html>
            """.trimIndent()
            
            when {
                browser != null -> {
                    browser.loadHTML(errorContent)
                }
                textPane != null -> {
                    textPane.text = errorContent
                    textPane.caretPosition = 0
                }
            }
        }
    }
    
    fun clear() {
        showWelcomeMessage()
    }
    
    private fun showWelcomeMessage() {
        val welcomeMarkdown = """
            # Code Review Results
            
            Select a review from the list to view the results here.
            
            ## How to use:
            1. Make code changes in your project
            2. Use **Ctrl+Alt+R** to review selected changes
            3. Use **Ctrl+Alt+Shift+R** to review all changes
            4. Select code in editor and use context menu to review specific code
            
            ## Features:
            - AI-powered code analysis
            - Customizable review templates
            - Support for multiple AI models
            - Markdown formatted results
        """.trimIndent()
        
        val htmlContent = convertMarkdownToHtml(welcomeMarkdown)
        
        when {
            browser != null -> {
                browser.loadHTML(htmlContent)
            }
            textPane != null -> {
                textPane.text = htmlContent
                textPane.caretPosition = 0
            }
        }
    }
    
    private fun convertMarkdownToHtml(markdown: String): String {
        val document = parser.parse(markdown)
        val htmlBody = renderer.render(document)
        
        // Get IDE theme colors
        val isDarkTheme = UIUtil.isUnderDarcula()
        val backgroundColor = UIUtil.getPanelBackground()
        val textColor = UIUtil.getLabelForeground()
        val borderColor = UIUtil.getBoundsColor()
        val codeBackgroundColor = UIUtil.getTextFieldBackground()
        
        // Convert colors to hex strings
        val bgHex = colorToHex(backgroundColor)
        val textHex = colorToHex(textColor)
        val borderHex = colorToHex(borderColor)
        val codeBgHex = colorToHex(codeBackgroundColor)
        
        // Theme-specific colors
        val (headingColor, linkColor, blockquoteBg, preBackground, preTextColor) = if (isDarkTheme) {
            arrayOf(
                colorToHex(UIUtil.getLabelForeground()),
                "#4FC3F7", // Light blue for dark theme
                colorToHex(UIUtil.getTextFieldBackground()),
                "#2D3748", // Dark gray for code blocks
                "#E2E8F0"  // Light gray text for code
            )
        } else {
            arrayOf(
                "#2c3e50", // Dark blue-gray for light theme
                "#1976D2", // Blue for links
                "#f8f9fa", // Light gray background
                "#f8f9fa", // Light background for code blocks
                "#333333"  // Dark text for code
            )
        }
        
        return """
            <!DOCTYPE html>
            <html>
            <head>
                <meta charset="UTF-8">
                <style>
                    body {
                        font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif;
                        line-height: 1.6;
                        color: $textHex;
                        max-width: 100%;
                        margin: 0;
                        padding: 16px;
                        background: $bgHex;
                    }
                    h1, h2, h3, h4, h5, h6 {
                        color: $headingColor;
                        margin-top: 24px;
                        margin-bottom: 12px;
                    }
                    h1 { border-bottom: 2px solid $linkColor; padding-bottom: 8px; }
                    h2 { border-bottom: 1px solid $borderHex; padding-bottom: 4px; }
                    a { color: $linkColor; }
                    code {
                        background: $codeBgHex;
                        padding: 2px 6px;
                        border-radius: 3px;
                        font-family: 'JetBrains Mono', Consolas, 'Courier New', monospace;
                        color: $textHex;
                        border: 1px solid $borderHex;
                    }
                    pre {
                        background: $preBackground;
                        color: $preTextColor;
                        padding: 16px;
                        border-radius: 6px;
                        overflow-x: auto;
                        margin: 16px 0;
                        border: 1px solid $borderHex;
                    }
                    pre code {
                        background: transparent;
                        padding: 0;
                        border: none;
                        color: $preTextColor;
                    }
                    blockquote {
                        border-left: 4px solid $linkColor;
                        padding-left: 16px;
                        margin: 16px 0;
                        background: $blockquoteBg;
                        border-radius: 0 4px 4px 0;
                    }
                    ul, ol {
                        padding-left: 24px;
                    }
                    li {
                        margin-bottom: 4px;
                    }
                    .review-meta {
                        background: $blockquoteBg;
                        border: 1px solid $borderHex;
                        border-radius: 6px;
                        padding: 12px;
                        margin: 16px 0;
                    }
                    table {
                        border-collapse: collapse;
                        width: 100%;
                        margin: 16px 0;
                    }
                    th, td {
                        border: 1px solid $borderHex;
                        padding: 8px 12px;
                        text-align: left;
                    }
                    th {
                        background: $codeBgHex;
                        font-weight: bold;
                    }
                </style>
            </head>
            <body>
                $htmlBody
            </body>
            </html>
        """.trimIndent()
    }
    
    /**
     * Convert AWT Color to hex string
     */
    private fun colorToHex(color: Color): String {
        return String.format("#%02x%02x%02x", color.red, color.green, color.blue)
    }
    
    fun getComponent(): JComponent = mainPanel
}