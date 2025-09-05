package com.etrx.codereviewer.action

import com.etrx.codereviewer.model.CodeChange
import com.etrx.codereviewer.util.VcsChangeExtractor
import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.PlatformDataKeys
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.VcsDataKeys
import com.intellij.openapi.vcs.changes.Change
import com.intellij.openapi.vcs.changes.ChangeListManager
import com.intellij.openapi.vfs.VirtualFile

/**
 * Action for VCS commit panel to review changes with AI
 */
class VcsCommitReviewAction : BaseCodeReviewAction() {
    
    private val logger = Logger.getInstance(VcsCommitReviewAction::class.java)
    
    init {
        templatePresentation.text = "AI 代码评审；按住 Shift 选中文件可以发起部分文件评审"
        templatePresentation.description = "Review code changes with AI before committing"
        templatePresentation.icon = AllIcons.Actions.IntentionBulb
    }
    
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        
        logger.info("=== VcsCommitReviewAction 被触发 ===")
        logger.info("项目: ${project.name}")
        
        // 增加更多的调试信息
        logEventDataKeys(e)
        
        val selectedChanges = super.getSelectedChanges(e)
        
        // 记录选中的变更数量
        logger.info("获取到的选中变更数量: ${selectedChanges.size}")
        selectedChanges.forEachIndexed { index, change ->
            logger.info("  变更 ${index + 1}: ${change.virtualFile?.path ?: "未知路径"}")
        }
        
        val codeChanges = if (selectedChanges.isNotEmpty()) {
            logger.info("用户选中了 ${selectedChanges.size} 个文件，使用选中的文件进行评审")
            // 用户选中了具体的文件，使用选中的文件
            VcsChangeExtractor.extractCodeChanges(selectedChanges)
        } else {
            logger.info("没有选中任何文件，将评审所有变更")
            // 没有选中文件，评审所有变更
            VcsChangeExtractor.extractCodeChanges(super.extractAllChanges(project))
        }
        
        // 记录提取到的代码变更数量
        logger.info("提取到的代码变更数量: ${codeChanges.size}")
        codeChanges.forEachIndexed { index, change ->
            logger.info("  代码变更 ${index + 1}: ${change.filePath} (${change.changeType})")
        }
        
        if (codeChanges.isNotEmpty()) {
            logger.info("开始执行代码评审，共 ${codeChanges.size} 个代码变更")
            performCodeReview(project, codeChanges)
        } else {
            logger.info("没有代码变更需要评审，结束操作")
        }
        
        logger.info("=== VcsCommitReviewAction 处理完成 ===")
    }
    
    override fun update(e: AnActionEvent) {
        val project = e.project
        val hasVcs = project != null && 
                    ChangeListManager.getInstance(project).areChangeListsEnabled()
        
        val hasChanges = if (hasVcs) {
            val changeListManager = ChangeListManager.getInstance(project!!)
            changeListManager.allChanges.isNotEmpty()
        } else {
            false
        }
        
        e.presentation.isEnabledAndVisible = hasVcs && hasChanges
    }
    
    /**
     * 记录事件中的数据键值，用于调试
     */
    private fun logEventDataKeys(e: AnActionEvent) {
        try {
            logger.info("=== 开始记录事件数据键值 ===")
            
            // 记录一些关键的数据键值
            val selectedChanges = e.getData(VcsDataKeys.SELECTED_CHANGES)
            logger.info("VcsDataKeys.SELECTED_CHANGES: ${if (selectedChanges == null) "null" else "${selectedChanges.size} 个元素"}")
            
            val changes = e.getData(VcsDataKeys.CHANGES)
            logger.info("VcsDataKeys.CHANGES: ${if (changes == null) "null" else "${changes.size} 个元素"}")
            
            val virtualFiles = e.getData(CommonDataKeys.VIRTUAL_FILE_ARRAY)
            logger.info("CommonDataKeys.VIRTUAL_FILE_ARRAY: ${if (virtualFiles == null) "null" else "${virtualFiles.size} 个元素"}")
            
            val editor = e.getData(CommonDataKeys.EDITOR)
            logger.info("CommonDataKeys.EDITOR: ${if (editor == null) "null" else "存在"}")
            
            val psiFile = e.getData(CommonDataKeys.PSI_FILE)
            logger.info("CommonDataKeys.PSI_FILE: ${if (psiFile == null) "null" else "存在"}")
            
            val project = e.project
            logger.info("e.project: ${if (project == null) "null" else project.name}")
            
            logger.info("=== 结束记录事件数据键值 ===")
        } catch (ex: Exception) {
            logger.warn("记录事件数据键值时发生异常: ${ex.message}", ex)
        }
    }
}