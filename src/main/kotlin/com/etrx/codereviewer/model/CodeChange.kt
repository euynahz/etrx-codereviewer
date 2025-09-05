package com.etrx.codereviewer.model

import java.io.File

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
    
    /**
     * 获取文件扩展名
     */
    fun getFileExtension(): String {
        return File(filePath).extension.ifEmpty { "unknown" }
    }
    
    /**
     * 获取格式化的变更内容，为AI提供全面的上下文信息
     * 格式要求：
     * 变更类型：变更/新增/删除
     * ```diff
     * +++ fileName
     * --- fileName
     * 文件全部内容，使用diff语法标记变化的地方
     * ```
     */
    fun getFormattedChange(): String {
        return when (changeType) {
            ChangeType.ADDED -> buildAddedChangeText()
            ChangeType.DELETED -> buildDeletedChangeText()
            ChangeType.MODIFIED -> buildModifiedChangeText()
        }
    }
    
    /**
     * 构建新增文件的格式化文本
     */
    private fun buildAddedChangeText(): String {
        val builder = StringBuilder()
        builder.append("变更类型：新增\n")
        builder.append("```diff\n")
        builder.append("+++ $filePath\n")
        if (newContent != null) {
            val lines = newContent.lines()
            for (line in lines) {
                builder.append("+ ").append(line).append("\n")
            }
        }
        builder.append("```\n")
        return builder.toString()
    }
    
    /**
     * 构建删除文件的格式化文本
     */
    private fun buildDeletedChangeText(): String {
        val builder = StringBuilder()
        builder.append("变更类型：删除\n")
        builder.append("```diff\n")
        builder.append("--- $filePath\n")
        if (oldContent != null) {
            val lines = oldContent.lines()
            for (line in lines) {
                builder.append("- ").append(line).append("\n")
            }
        }
        builder.append("```\n")
        return builder.toString()
    }
    
    /**
     * 构建修改文件的格式化文本
     */
    private fun buildModifiedChangeText(): String {
        val builder = StringBuilder()
        builder.append("变更类型：变更\n")
        builder.append("```diff\n")
        builder.append("--- $filePath\n")
        builder.append("+++ $filePath\n")
        
        if (oldContent != null && newContent != null) {
            // 生成标准diff格式
            builder.append(generateUnifiedDiff(oldContent, newContent))
        } else if (oldContent != null) {
            val lines = oldContent.lines()
            for (line in lines) {
                builder.append("- ").append(line).append("\n")
            }
        } else if (newContent != null) {
            val lines = newContent.lines()
            for (line in lines) {
                builder.append("+ ").append(line).append("\n")
            }
        }
        
        builder.append("```\n")
        return builder.toString()
    }
    
    /**
     * 生成符合标准统一差异格式(unified diff)的代码差异
     */
    private fun generateUnifiedDiff(oldContent: String, newContent: String): String {
        val oldLines = oldContent.lines()
        val newLines = newContent.lines()
        
        // 使用标准的Myers差异算法生成差异
        val diffResult = MyersDiff.compute(oldLines, newLines)
        
        val builder = StringBuilder()
        
        // 按照标准diff格式输出
        var i = 0
        var j = 0
        
        for (entry in diffResult) {
            when (entry.type) {
                DiffType.DELETE -> {
                    builder.append("- ").append(entry.content).append("\n")
                    i++
                }
                DiffType.INSERT -> {
                    builder.append("+ ").append(entry.content).append("\n")
                    j++
                }
                DiffType.EQUAL -> {
                    builder.append("  ").append(entry.content).append("\n")
                    i++
                    j++
                }
            }
        }
        
        return builder.toString()
    }
    
    /**
     * 表示单个行的差异类型
     */
    enum class DiffType {
        DELETE,
        INSERT,
        EQUAL
    }
    
    /**
     * 表示一个差异行
     */
    private data class DiffEntry(val type: DiffType, val content: String)
}

/**
 * Myers差异算法实现
 * 使用标准的Myers差异算法，能够更准确地计算文件差异
 */
private object MyersDiff {
    data class DiffEntry(val type: CodeChange.DiffType, val content: String)
    
    /**
     * 使用标准Myers差异算法计算两个文本列表之间的差异
     */
    fun compute(oldLines: List<String>, newLines: List<String>): List<DiffEntry> {
        val diffEntries = mutableListOf<DiffEntry>()
        
        // 使用标准Myers差异算法
        val path = shortestEditScript(oldLines, newLines)
        
        // 将路径转换为差异列表
        var x = 0
        var y = 0
        
        for (operation in path) {
            when (operation) {
                is Operation.Delete -> {
                    diffEntries.add(DiffEntry(CodeChange.DiffType.DELETE, oldLines[x]))
                    x++
                }
                is Operation.Insert -> {
                    diffEntries.add(DiffEntry(CodeChange.DiffType.INSERT, newLines[y]))
                    y++
                }
                is Operation.Match -> {
                    diffEntries.add(DiffEntry(CodeChange.DiffType.EQUAL, oldLines[x]))
                    x++
                    y++
                }
            }
        }
        
        return diffEntries
    }
    
    /**
     * 表示编辑操作
     */
    sealed class Operation {
        object Delete : Operation()
        object Insert : Operation()
        object Match : Operation()
    }
    
    /**
     * 计算最短编辑脚本
     */
    private fun shortestEditScript(oldLines: List<String>, newLines: List<String>): List<Operation> {
        val n = oldLines.size
        val m = newLines.size
        val max = n + m
        val v = mutableMapOf<Int, Int>()
        val paths = mutableMapOf<Int, List<Operation>>()
        
        v[1] = 0
        paths[1] = emptyList()
        
        for (d in 0..max) {
            for (k in -d..d step 2) {
                var x: Int
                val down = k == -d || (k != d && v[k - 1]!! < v[k + 1]!!)
                val prevK = if (down) k + 1 else k - 1
                val prevPath = paths[prevK]!!
                
                if (down) {
                    x = v[k + 1]!!
                    paths[k] = prevPath + Operation.Insert
                } else {
                    x = v[k - 1]!! + 1
                    paths[k] = prevPath + Operation.Delete
                }
                
                var y = x - k
                
                // 添加匹配操作
                val currentPath = paths[k]!!.toMutableList()
                while (x < n && y < m && oldLines[x] == newLines[y]) {
                    currentPath.add(Operation.Match)
                    x++
                    y++
                }
                
                v[k] = x
                paths[k] = currentPath
                
                if (x >= n && y >= m) {
                    return currentPath
                }
            }
        }
        
        return emptyList()
    }
}