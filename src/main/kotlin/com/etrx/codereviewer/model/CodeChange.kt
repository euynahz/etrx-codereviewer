package com.etrx.codereviewer.model

import com.github.difflib.DiffUtils
import com.github.difflib.UnifiedDiffUtils
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
     * unified diff 输出，含 @@ hunk 头 和 +++/--- 文件头
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

        if (oldContent != null && newContent != null) {
            // 统一使用标准 unified diff，包含 ---/+++ 文件头和 @@ hunk 头
            builder.append(generateUnifiedDiff(oldContent, newContent))
        } else if (oldContent != null) {
            val lines = oldContent.lines()
            builder.append("--- $filePath\n")
            for (line in lines) {
                builder.append("- ").append(line).append("\n")
            }
        } else if (newContent != null) {
            val lines = newContent.lines()
            builder.append("+++ $filePath\n")
            for (line in lines) {
                builder.append("+ ").append(line).append("\n")
            }
        }

        builder.append("```\n")
        return builder.toString()
    }

    /**
     * 生成符合标准统一差异格式(unified diff)的代码差异
     * 说明：
     * - 使用经过充分验证的 java-diff-utils 库，避免自实现算法的边界问题
     * - 自动加入 ---/+++ 文件头与 @@ hunk
     * - 使用“全文上下文”（context size = Int.MAX_VALUE），把整份文件带给 AI
     */
    private fun generateUnifiedDiff(oldContent: String, newContent: String): String {
        // 统一换行，避免不同平台 CRLF/LF 导致的误判
        val oldLines = oldContent.replace("\r\n", "\n").split("\n")
        val newLines = newContent.replace("\r\n", "\n").split("\n")

        val patch = DiffUtils.diff(oldLines, newLines)
        val diffLines = UnifiedDiffUtils.generateUnifiedDiff(
            filePath,            // old file path
            filePath,            // new file path
            oldLines,            // original
            patch,               // computed patch
            Int.MAX_VALUE        // context size: 全文
        )
        // 追加一个换行以便在 ```diff 代码块中渲染更稳定
        return diffLines.joinToString("\n") + "\n"
    }
}