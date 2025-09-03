package com.etrx.codereviewer.model

/**
 * Represents a code change for review
 */
data class CodeChange(
    val filePath: String,
    val oldContent: String?,
    val newContent: String?,
    val changeType: ChangeType,
    val lineRange: IntRange? = null
) {
    enum class ChangeType {
        ADDED, MODIFIED, DELETED
    }
    
    fun getFormattedChange(): String {
        return when (changeType) {
            ChangeType.ADDED -> "New file: $filePath\n\nContent:\n$newContent"
            ChangeType.DELETED -> "Deleted file: $filePath\n\nPrevious content:\n$oldContent"
            ChangeType.MODIFIED -> buildModifiedChangeText()
        }
    }
    
    private fun buildModifiedChangeText(): String {
        val builder = StringBuilder()
        builder.append("Modified file: $filePath\n\n")
        
        if (lineRange != null) {
            builder.append("Lines ${lineRange.first}-${lineRange.last}:\n")
        }
        
        if (oldContent != null) {
            builder.append("Before:\n$oldContent\n\n")
        }
        
        if (newContent != null) {
            builder.append("After:\n$newContent")
        }
        
        return builder.toString()
    }
}