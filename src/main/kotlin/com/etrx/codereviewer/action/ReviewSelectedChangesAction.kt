package com.etrx.codereviewer.action

import com.etrx.codereviewer.model.CodeChange
import com.etrx.codereviewer.util.VcsChangeExtractor
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.vcs.VcsDataKeys
import com.intellij.openapi.vcs.changes.Change
import com.intellij.openapi.vcs.changes.ChangeListManager
import com.intellij.openapi.vfs.VirtualFile

/**
 * Action to review selected changes in VCS
 */
class ReviewSelectedChangesAction : BaseCodeReviewAction() {
    
    private val logger = Logger.getInstance(ReviewSelectedChangesAction::class.java)
    
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        
        logger.info("=== ReviewSelectedChangesAction 被触发 ===")
        logger.info("项目: ${project.name}")
        
        val selectedChanges = getSelectedChanges(e)
        
        val codeChanges = if (selectedChanges.isNotEmpty()) {
            logger.info("用户选中了 ${selectedChanges.size} 个文件，使用选中的文件进行评审")
            // 用户选中了具体的文件，使用选中的文件
            val extractedChanges = VcsChangeExtractor.extractCodeChanges(selectedChanges)
            logger.info("从 ${selectedChanges.size} 个选中变更中提取到 ${extractedChanges.size} 个代码变更")
            extractedChanges.forEachIndexed { index, change ->
                logger.info("  提取的代码变更 ${index + 1}: ${change.filePath} (${change.changeType})")
            }
            extractedChanges
        } else {
            logger.info("没有选中任何文件，询问用户是否评审所有变更")
            // 没有选中文件，询问用户是否评审所有变更
            val result = Messages.showYesNoDialog(
                project,
                "无法检测到选中的文件。\n\n请确保在VCS变更窗口中勾选了要评审的文件。\n\n是否要评审变更区中的所有变更？",
                "代码评审",
                "评审所有变更",
                "取消",
                Messages.getQuestionIcon()
            )
            
            if (result == Messages.YES) {
                logger.info("用户选择评审所有变更")
                val allChanges = extractAllChanges(project)
                logger.info("提取到 ${allChanges.size} 个所有变更")
                allChanges
            } else {
                logger.info("用户取消了评审操作")
                emptyList()
            }
        }
        
        if (codeChanges.isNotEmpty()) {
            logger.info("开始执行代码评审，共 ${codeChanges.size} 个代码变更")
            performCodeReview(project, codeChanges)
        } else {
            logger.info("没有代码变更需要评审，结束操作")
        }
        
        logger.info("=== ReviewSelectedChangesAction 处理完成 ===")
    }
    
    override fun update(e: AnActionEvent) {
        val project = e.project
        val hasVcs = project != null && 
                    ChangeListManager.getInstance(project).areChangeListsEnabled()
        
        e.presentation.isEnabledAndVisible = hasVcs
    }
    
    private fun getSelectedChanges(e: AnActionEvent): List<Change> {
        logger.info("=== 开始获取用户选中的变更文件 ===")
        
        // 方法1: 尝试从 VirtualFile 获取当前选中的文件
        val selectedFiles = e.getData(CommonDataKeys.VIRTUAL_FILE_ARRAY)
        if (selectedFiles != null && selectedFiles.isNotEmpty()) {
            logger.info("方法1: 从 CommonDataKeys.VIRTUAL_FILE_ARRAY 获取到 ${selectedFiles.size} 个选中文件")
            selectedFiles.forEachIndexed { index, file ->
                logger.info("  选中文件 ${index + 1}: ${file.path}")
            }
            
            // 将选中的文件转换为 Change 对象
            val project = e.project!!
            val changeListManager = ChangeListManager.getInstance(project)
            val allChanges = changeListManager.allChanges.toList()
            
            // 根据选中的文件路径过滤变更
            val selectedChanges = allChanges.filter { change ->
                val changeFile = change.virtualFile
                if (changeFile != null) {
                    selectedFiles.any { selectedFile -> 
                        selectedFile.path == changeFile.path
                    }
                } else {
                    false
                }
            }
            
            if (selectedChanges.isNotEmpty()) {
                logger.info("方法1成功: 从 ${selectedFiles.size} 个选中文件中过滤到 ${selectedChanges.size} 个变更")
                selectedChanges.forEachIndexed { index, change ->
                    logger.info("  过滤后的变更 ${index + 1}: ${change.virtualFile?.path ?: "未知路径"}")
                }
                logger.info("=== 获取选中变更完成(方法1) ===")
                return selectedChanges
            } else {
                logger.info("方法1失败: 选中的文件中没有找到对应的变更")
            }
        } else {
            logger.info("方法1失败: CommonDataKeys.VIRTUAL_FILE_ARRAY 返回 null 或空数组")
        }
        
        // 方法2: 尝试从VcsDataKeys.SELECTED_CHANGES获取
        val selectedChanges = e.getData(VcsDataKeys.SELECTED_CHANGES)
        if (selectedChanges != null && selectedChanges.isNotEmpty()) {
            val changes = selectedChanges.toList()
            logger.info("方法2成功: 从VcsDataKeys.SELECTED_CHANGES获取到 ${changes.size} 个选中变更")
            changes.forEachIndexed { index, change ->
                logger.info("  选中变更 ${index + 1}: ${change.virtualFile?.path ?: "未知路径"}")
            }
            logger.info("=== 获取选中变更完成(方法2) ===")
            return changes
        } else {
            logger.info("方法2失败: VcsDataKeys.SELECTED_CHANGES 返回 null 或空列表")
        }
        
        // 方法3: 尝试从VcsDataKeys.CHANGES获取，但这时候就不是“选中”了
        val changes = e.getData(VcsDataKeys.CHANGES)
        if (changes != null && changes.isNotEmpty()) {
            val changesList = changes.toList()
            logger.warn("方法3警告: 从VcsDataKeys.CHANGES获取到 ${changesList.size} 个变更，但这可能不是用户选中的")
            changesList.forEachIndexed { index, change ->
                logger.info("  所有变更 ${index + 1}: ${change.virtualFile?.path ?: "未知路径"}")
            }
            logger.info("=== 获取变更完成(方法3-全部) ===")
            return changesList
        } else {
            logger.info("方法3失败: VcsDataKeys.CHANGES 返回 null 或空列表")
        }
        
        logger.warn("所有方法均失败: 无法获取选中的变更，返回空列表")
        logger.info("=== 获取选中变更结束(失败) ===")
        return emptyList()
    }
    
    private fun extractAllChanges(project: Project): List<CodeChange> {
        logger.info("=== 提取所有变更开始 ===")
        
        val changeListManager = ChangeListManager.getInstance(project)
        val allChanges = changeListManager.allChanges.toList()
        
        logger.info("从变更列表管理器中获取到 ${allChanges.size} 个所有变更")
        allChanges.forEachIndexed { index, change ->
            logger.info("  所有变更 ${index + 1}: ${change.virtualFile?.path ?: "未知路径"}")
        }
        
        val codeChanges = VcsChangeExtractor.extractCodeChanges(allChanges)
        logger.info("从 ${allChanges.size} 个所有变更中提取到 ${codeChanges.size} 个代码变更")
        logger.info("=== 提取所有变更完成 ===")
        
        return codeChanges
    }
}