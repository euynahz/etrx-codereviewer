package com.etrx.codereviewer.model

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*

class CodeChangeTest {
    
    @Test
    fun testAddedFileFormatting() {
        val codeChange = CodeChange(
            filePath = "test.txt",
            oldContent = null,
            newContent = "Hello\nWorld",
            changeType = CodeChange.ChangeType.ADDED
        )
        
        val formatted = codeChange.getFormattedChange()
        println(formatted)
        
        assertTrue(formatted.contains("变更类型：新增"))
        assertTrue(formatted.contains("+++ test.txt"))
        assertTrue(formatted.contains("+ Hello"))
        assertTrue(formatted.contains("+ World"))
    }
    
    @Test
    fun testDeletedFileFormatting() {
        val codeChange = CodeChange(
            filePath = "test.txt",
            oldContent = "Hello\nWorld",
            newContent = null,
            changeType = CodeChange.ChangeType.DELETED
        )
        
        val formatted = codeChange.getFormattedChange()
        println(formatted)
        
        assertTrue(formatted.contains("变更类型：删除"))
        assertTrue(formatted.contains("--- test.txt"))
        assertTrue(formatted.contains("- Hello"))
        assertTrue(formatted.contains("- World"))
    }
    
    @Test
    fun testModifiedFileFormatting() {
        val codeChange = CodeChange(
            filePath = "test.txt",
            oldContent = "Hello\nWorld\nTest",
            newContent = "Hello\nUniverse\nTest",
            changeType = CodeChange.ChangeType.MODIFIED
        )
        
        val formatted = codeChange.getFormattedChange()
        println(formatted)
        
        assertTrue(formatted.contains("变更类型：变更"))
        assertTrue(formatted.contains("--- test.txt"))
        assertTrue(formatted.contains("+++ test.txt"))
    }
}