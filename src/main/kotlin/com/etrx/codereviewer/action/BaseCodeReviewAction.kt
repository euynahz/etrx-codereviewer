package com.etrx.codereviewer.action

import com.etrx.codereviewer.model.CodeChange
import com.etrx.codereviewer.model.ReviewResult
import com.etrx.codereviewer.service.CodeReviewerSettingsService
import com.etrx.codereviewer.service.OllamaReviewService
import com.etrx.codereviewer.service.ReviewResultFileService
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DataKey
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.vcs.VcsDataKeys
import com.intellij.openapi.vcs.CheckinProjectPanel
import com.intellij.openapi.vcs.changes.Change
import com.intellij.openapi.vcs.changes.ChangeListManager
import kotlinx.coroutines.runBlocking

/**
 * Base class for code review actions
 */
abstract class BaseCodeReviewAction : AnAction(), DumbAware {
    
    private val logger = Logger.getInstance(BaseCodeReviewAction::class.java)
    
    /**
     * 获取用户选中的变更文件。
     * 兼容不同版本的 IDE：优先读取“包含/勾选”的变更（用于本次提交），
     * 然后读取明确选中的变更，最后从虚拟文件回退到变更。
     */
    protected fun getSelectedChanges(e: AnActionEvent): List<Change> {
        val actionName = this.javaClass.simpleName
        logger.info("[$actionName] === 开始获取用户选中的变更文件 ===")

        // 方法1（优先）：Commit 面板的 CheckinProjectPanel（官方接口，返回“将要提交”的变更）
        // 为兼容缺少常量 DataKey 的平台版本，这里通过名称创建 DataKey
        val checkinPanelKey: DataKey<CheckinProjectPanel> = DataKey.create("CheckinProjectPanel")
        val checkinPanel = e.getData(checkinPanelKey)
        if (checkinPanel != null) {
            // 优先读取“包含到提交”的变更；某些平台版本该属性名为 includedChanges
            val panelIncluded: Collection<Change> = try {
                checkinPanel.selectedChanges
            } catch (ex: Throwable) {
                try {
                    // 通过反射兼容 includedChanges
                    val prop = checkinPanel.javaClass.methods.firstOrNull { it.name == "getIncludedChanges" && it.parameterCount == 0 }
                    @Suppress("UNCHECKED_CAST")
                    (prop?.invoke(checkinPanel) as? Collection<Change>) ?: emptyList()
                } catch (ex2: Throwable) {
                    emptyList()
                }
            }
            if (panelIncluded.isNotEmpty()) {
                val changes = panelIncluded.toList()
                logger.info("[$actionName] 从 CheckinProjectPanel.selectedChanges 获取到 ${changes.size} 个已包含(勾选)的变更")
                changes.forEachIndexed { index, change ->
                    logger.info("[$actionName]   包含变更 ${index + 1}: ${change.virtualFile?.path ?: "未知路径"}")
                }
                logger.info("[$actionName] === 获取选中变更完成(方法1: CheckinProjectPanel) ===")
                return changes
            }
            logger.info("[$actionName] CheckinProjectPanel.selectedChanges 为空或为空集合")
        }

        logger.info("[$actionName] 未从 CheckinProjectPanel 获取到包含变更，尝试回退方法")

        // 方法2：SELECTED_CHANGES —— 变更树里明确选中的（可能未勾选为待提交）或 Commit 面板里按住 Shift 勾选的项
        val selectedChanges: Array<Change>? = e.getData(VcsDataKeys.SELECTED_CHANGES)
        if (selectedChanges != null && selectedChanges.isNotEmpty()) {
            val changes = selectedChanges.toList()
            logger.info("[$actionName] 从 VcsDataKeys.SELECTED_CHANGES 获取到 ${changes.size} 个选中的变更")
            changes.forEachIndexed { index, change ->
                logger.info("[$actionName]   选中变更 ${index + 1}: ${change.virtualFile?.path ?: "未知路径"}")
            }
            logger.info("[$actionName] === 获取选中变更完成(方法2: SELECTED_CHANGES) ===")
            return changes
        }
        logger.info("[$actionName] VcsDataKeys.SELECTED_CHANGES 为空，尝试回退方法")

        // 方法3：CHANGES —— 高亮选中的变更
        val highlightedChanges: Array<Change>? = e.getData(VcsDataKeys.CHANGES)
        if (highlightedChanges != null && highlightedChanges.isNotEmpty()) {
            val changes = highlightedChanges.toList()
            logger.info("[$actionName] 从 VcsDataKeys.CHANGES 获取到 ${changes.size} 个选中的变更")
            changes.forEachIndexed { index, change ->
                logger.info("[$actionName]   选中变更 ${index + 1}: ${change.virtualFile?.path ?: "未知路径"}")
            }
            logger.info("[$actionName] === 获取选中变更完成(方法3: CHANGES) ===")
            return changes
        }
        logger.info("[$actionName] VcsDataKeys.CHANGES 为空，尝试回退方法")

        // 方法3.5：尝试通过 DataKey 名称直接读取 INCLUDED_CHANGES（Commit 面板中被勾选的变更）
        try {
            val includedKey1: DataKey<Array<Change>> = DataKey.create("INCLUDED_CHANGES")
            val includedChanges1 = e.getData(includedKey1)
            if (includedChanges1 != null && includedChanges1.isNotEmpty()) {
                val changes = includedChanges1.toList()
                logger.info("[$actionName] 从 DataKey('INCLUDED_CHANGES') 获取到 ${changes.size} 个被勾选的变更")
                return changes
            }
            // 某些平台可能使用命名空间前缀
            val includedKey2: DataKey<Array<Change>> = DataKey.create("Vcs.IncludedChanges")
            val includedChanges2 = e.getData(includedKey2)
            if (includedChanges2 != null && includedChanges2.isNotEmpty()) {
                val changes = includedChanges2.toList()
                logger.info("[$actionName] 从 DataKey('Vcs.IncludedChanges') 获取到 ${changes.size} 个被勾选的变更")
                return changes
            }
        } catch (t: Throwable) {
            logger.info("[$actionName] 通过 DataKey 名称读取 INCLUDED_CHANGES 失败: ${t.message}")
        }

        // 方法4：Commit 面板当前清单中被包含的变更（不依赖面板引用；需结合包含模型进行过滤）
        val projectRef = e.project
        if (projectRef != null) {
            val clm = ChangeListManager.getInstance(projectRef)
            try {
                // 通过反射尝试获取包含模型，并据此过滤真正被勾选的变更
                val getInclusionMethod = clm.javaClass.methods.firstOrNull { it.name == "getInclusion" && it.parameterCount == 0 }
                val inclusionModel = getInclusionMethod?.invoke(clm)
                if (inclusionModel != null) {
                    val isIncludedMethod = inclusionModel.javaClass.methods.firstOrNull { it.name == "isIncluded" && it.parameterCount == 1 }
                    if (isIncludedMethod != null) {
                        val defaultChanges = clm.defaultChangeList.changes
                        val filtered = defaultChanges.filter { ch ->
                            try {
                                (isIncludedMethod.invoke(inclusionModel, ch) as? Boolean) == true
                            } catch (_: Throwable) { false }
                        }
                        if (filtered.isNotEmpty()) {
                            logger.info("[$actionName] 从 ChangeListManager + InclusionModel 过滤得到 ${filtered.size} 个已包含(勾选)的变更")
                            return filtered
                        }
                        // 若默认清单为空，再尝试所有清单后过滤
                        val all = clm.changeLists.flatMap { it.changes }
                        val allFiltered = all.filter { ch ->
                            try {
                                (isIncludedMethod.invoke(inclusionModel, ch) as? Boolean) == true
                            } catch (_: Throwable) { false }
                        }
                        if (allFiltered.isNotEmpty()) {
                            logger.info("[$actionName] 从所有清单中过滤得到 ${allFiltered.size} 个已包含(勾选)的变更")
                            return allFiltered
                        }
                    }
                } else {
                    logger.info("[$actionName] 未获取到包含模型(getInclusion)，为避免误计数，将跳过默认清单的全量返回")
                }
            } catch (t: Throwable) {
                logger.info("[$actionName] 通过 ChangeListManager 过滤包含变更时发生异常: ${t.message}")
            }
        }

        // 方法5：从虚拟文件回退到 Change（支持从项目视图等位置触发）
        val project = e.project
        val virtualFiles = e.getData(CommonDataKeys.VIRTUAL_FILE_ARRAY)
        if (project != null && virtualFiles != null && virtualFiles.isNotEmpty()) {
            logger.info("[$actionName] 从 CommonDataKeys.VIRTUAL_FILE_ARRAY 获取到 ${virtualFiles.size} 个虚拟文件")
            val changeListManager = ChangeListManager.getInstance(project)
            val changes = virtualFiles.mapNotNull { vf -> changeListManager.getChange(vf) }
            if (changes.isNotEmpty()) {
                logger.info("[$actionName] 通过虚拟文件数组转换得到 ${changes.size} 个变更")
                logger.info("[$actionName] === 获取选中变更完成(方法4: VIRTUAL_FILE_ARRAY) ===")
                return changes
            } else {
                logger.info("[$actionName] 无法从虚拟文件数组中找到对应的变更")
            }
        }
        logger.info("[$actionName] CommonDataKeys.VIRTUAL_FILE_ARRAY 为空或未找到变更")

        logger.info("[$actionName] 无法获取任何选中的文件变更。")
        logger.info("[$actionName] === 获取选中变更结束 ===")
        return emptyList()
    }
    
    /**
     * 提取项目中的所有变更
     */
    protected fun extractAllChanges(project: Project): List<Change> {
        val actionName = this.javaClass.simpleName
        logger.info("[$actionName] === 提取所有变更开始 ===")
        
        val changeListManager = ChangeListManager.getInstance(project)
        val allChanges = changeListManager.allChanges.toList()
        
        logger.info("[$actionName] 从变更列表管理器中获取到 ${allChanges.size} 个所有变更")
        allChanges.forEachIndexed { index, change ->
            logger.info("[$actionName]   所有变更 ${index + 1}: ${change.virtualFile?.path ?: "未知路径"}")
        }
        
        logger.info("[$actionName] === 提取所有变更完成 ===")
        
        return allChanges
    }
    
    protected fun performCodeReview(project: Project, codeChanges: List<CodeChange>) {
        if (codeChanges.isEmpty()) {
            logger.info("没有找到可评审的代码变更")
            Messages.showInfoMessage(
                project,
                "No code changes found to review.",
                "Code Review"
            )
            return
        }
        
        val actionId = this.javaClass.simpleName
        logger.info("=== Action $actionId 发起代码评审 ===")
        logger.info("项目: ${project.name}")
        logger.info("代码变更数量: ${codeChanges.size}")
        
        codeChanges.forEachIndexed { index, change ->
            logger.info("变更 ${index + 1}: ${change.filePath} (${change.changeType})")
        }
        
        val settingsService = CodeReviewerSettingsService.getInstance()
        val reviewService = project.getService(OllamaReviewService::class.java)
        val promptTemplate = settingsService.getSelectedPromptTemplate()
        
        logger.info("使用提示模板: ${promptTemplate.name}")
        logger.info("模板描述: ${promptTemplate.description}")
        
        ProgressManager.getInstance().run(object : Task.Backgroundable(
            project, "AI Code Review (${promptTemplate.name})", true
        ) {
            override fun run(indicator: ProgressIndicator) {
                val taskStartTime = System.currentTimeMillis()
                
                try {
                    logger.info("开始执行代码评审任务...")
                    // 修复进度指示器警告：在设置fraction之前设置Indeterminate为false
                    indicator.isIndeterminate = false
                    indicator.text = "Starting code review with template: ${promptTemplate.name}..."
                    indicator.fraction = 0.0
                    
                    // 注意：runBlocking是阻塞的，无法在等待时检查取消状态
                    // 但我们在reviewService.reviewCode内部已经添加了取消检查
                    val result = runBlocking {
                        reviewService.reviewCode(
                            codeChanges = codeChanges,
                            prompt = promptTemplate.template,
                            templateName = promptTemplate.name,
                            progressIndicator = indicator
                        )
                    }
                    
                    val taskDuration = System.currentTimeMillis() - taskStartTime
                    
                    ApplicationManager.getApplication().invokeLater {
                        logger.info("代码评审任务完成 - 状态: ${result.status}, 耗时: ${taskDuration}ms")
                        logger.info("评审内容长度: ${result.reviewContent.length} 字符")
                        
                        // 根据状态显示不同的结果
                        if (result.status == ReviewResult.ReviewStatus.CANCELLED) {
                            logger.info("评审已取消，不显示结果")
                        } else {
                            showReviewResult(project, result)
                        }
                        
                        logger.info("=== Action $actionId 代码评审完成 ===\n")
                    }
                    
                } catch (e: Exception) {
                    val taskDuration = System.currentTimeMillis() - taskStartTime
                    
                    logger.error("代码评审任务异常 - 类型: ${e.javaClass.simpleName}, 消息: ${e.message}", e)
                    logger.info("任务耗时: ${taskDuration}ms")
                    
                    ApplicationManager.getApplication().invokeLater {
                        Messages.showErrorDialog(
                            project,
                            "Code review failed: ${e.message}",
                            "Review Error"
                        )
                        
                        logger.info("=== Action $actionId 代码评审异常结束 ===\n")
                    }
                }
            }
        })
    }
    
    private fun showReviewResult(project: Project, result: ReviewResult) {
        logger.info("显示评审结果 - ID: ${result.id}, 状态: ${result.status}")
        
        // Save result to file and open it
        val fileService = project.getService(ReviewResultFileService::class.java)
        val saveSuccess = fileService.saveAndOpenReviewResult(project, result)
        
        if (saveSuccess) {
            logger.info("评审结果已保存到文件并打开")
        } else {
            logger.error("保存评审结果失败，降级到弹窗显示")
            // Fallback to dialog if file save fails
            showErrorDialog(project, "无法保存评审结果到文件，请检查文件路径配置。")
        }
        
        // Only show error notifications for failed reviews
        if (!result.isSuccessful()) {
            logger.warn("评审结果显示错误通知: ${result.errorMessage}")
            showErrorDialog(project, result.errorMessage ?: "Review failed with unknown error.")
        }
    }
    
    private fun showErrorDialog(project: Project, message: String) {
        Messages.showErrorDialog(
            project,
            message,
            "Review Error"
        )
    }
    
    override fun getActionUpdateThread(): ActionUpdateThread {
        return ActionUpdateThread.BGT
    }
}