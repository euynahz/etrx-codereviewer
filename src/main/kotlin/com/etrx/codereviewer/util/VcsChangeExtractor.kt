package com.etrx.codereviewer.util

import com.etrx.codereviewer.model.CodeChange
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.vcs.changes.Change
import com.intellij.openapi.vcs.changes.ContentRevision

/**
 * Utility class for extracting code changes from VCS
 */
object VcsChangeExtractor {
    
    private val logger = Logger.getInstance(VcsChangeExtractor::class.java)
    
    /**
     * Extract CodeChange objects from VCS Change objects
     */
    fun extractCodeChanges(changes: List<Change>): List<CodeChange> {
        return changes.mapNotNull { change ->
            try {
                extractSingleCodeChange(change)
            } catch (e: Exception) {
                logger.warn("Failed to extract change for: ${change.virtualFile?.path}", e)
                null
            }
        }.filter { it.filePath.isNotBlank() }
    }
    
    private fun extractSingleCodeChange(change: Change): CodeChange? {
        val beforeRevision = change.beforeRevision
        val afterRevision = change.afterRevision
        
        return when {
            beforeRevision == null && afterRevision != null -> {
                // New file
                createCodeChange(
                    filePath = afterRevision.file.path,
                    oldContent = null,
                    newContent = getRevisionContent(afterRevision),
                    changeType = CodeChange.ChangeType.ADDED
                )
            }
            
            beforeRevision != null && afterRevision == null -> {
                // Deleted file
                createCodeChange(
                    filePath = beforeRevision.file.path,
                    oldContent = getRevisionContent(beforeRevision),
                    newContent = null,
                    changeType = CodeChange.ChangeType.DELETED
                )
            }
            
            beforeRevision != null && afterRevision != null -> {
                // Modified file
                createCodeChange(
                    filePath = afterRevision.file.path,
                    oldContent = getRevisionContent(beforeRevision),
                    newContent = getRevisionContent(afterRevision),
                    changeType = CodeChange.ChangeType.MODIFIED
                )
            }
            
            else -> null
        }
    }
    
    private fun createCodeChange(
        filePath: String,
        oldContent: String?,
        newContent: String?,
        changeType: CodeChange.ChangeType
    ): CodeChange? {
        
        // Filter out binary files and non-code files
        if (!isCodeFile(filePath)) {
            return null
        }
        
        // Limit content size to prevent overwhelming the AI
        val maxContentSize = 10000 // characters
        
        val trimmedOldContent = oldContent?.let { 
            if (it.length > maxContentSize) {
                it.take(maxContentSize) + "\n... [content truncated]"
            } else it
        }
        
        val trimmedNewContent = newContent?.let {
            if (it.length > maxContentSize) {
                it.take(maxContentSize) + "\n... [content truncated]"
            } else it
        }
        
        return CodeChange(
            filePath = filePath,
            oldContent = trimmedOldContent,
            newContent = trimmedNewContent,
            changeType = changeType
        )
    }
    
    private fun getRevisionContent(revision: ContentRevision): String? {
        return try {
            revision.content
        } catch (e: Exception) {
            logger.warn("Failed to get content for revision: ${revision.file.path}", e)
            null
        }
    }
    
    private fun isCodeFile(filePath: String): Boolean {
        val codeExtensions = setOf(
            "java", "kt", "scala", "groovy", "py", "js", "ts", "jsx", "tsx",
            "cs", "vb", "cpp", "c", "h", "hpp", "php", "rb", "go", "rs",
            "swift", "m", "mm", "sql", "xml", "html", "css", "scss", "sass",
            "json", "yaml", "yml", "properties", "gradle", "maven", "pom",
            "sh", "bat", "ps1", "dockerfile", "md", "txt", "ini", "cfg", "conf"
        )
        
        val extension = filePath.substringAfterLast('.', "").lowercase()
        return extension in codeExtensions
    }
}