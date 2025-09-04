// AI代码评审调试工具 - 前端JavaScript

class ReviewDebugger {
    constructor() {
        this.templates = {};
        this.currentReview = null;
        this.initializeElements();
        this.bindEvents();
        this.loadTemplates();
        this.testConnectionOnLoad();
    }

    // 初始化DOM元素引用
    initializeElements() {
        this.elements = {
            // 输入控件
            templateSelect: document.getElementById('template-select'),
            promptInput: document.getElementById('prompt-input'),
            charCount: document.getElementById('char-count'),
            
            // 按钮
            testConnectionBtn: document.getElementById('test-connection'),
            reviewBtn: document.getElementById('review-btn'),
            clearBtn: document.getElementById('clear-btn'),
            copyResultBtn: document.getElementById('copy-result'),
            copyPromptBtn: document.getElementById('copy-prompt'),
            
            // 状态显示
            connectionStatus: document.getElementById('connection-status'),
            resultStatus: document.getElementById('result-status'),
            resultTime: document.getElementById('result-time'),
            
            // 结果展示
            loading: document.getElementById('loading'),
            errorDisplay: document.getElementById('error-display'),
            errorMessage: document.getElementById('error-message'),
            resultDisplay: document.getElementById('result-display'),
            emptyState: document.getElementById('empty-state'),
            
            // 结果内容
            reviewId: document.getElementById('review-id'),
            modelUsed: document.getElementById('model-used'),
            reviewDuration: document.getElementById('review-duration'),
            markdownContent: document.getElementById('markdown-content')
        };
    }

    // 绑定事件处理器
    bindEvents() {
        // 模板选择变化
        this.elements.templateSelect.addEventListener('change', () => {
            this.onTemplateChange();
        });

        // 提示词输入变化
        this.elements.promptInput.addEventListener('input', () => {
            this.updateCharCount();
            this.validatePrompt();
        });

        // 按钮点击事件
        this.elements.testConnectionBtn.addEventListener('click', () => {
            this.testConnection();
        });

        this.elements.reviewBtn.addEventListener('click', () => {
            this.startReview();
        });

        this.elements.clearBtn.addEventListener('click', () => {
            this.clearResults();
        });

        this.elements.copyResultBtn.addEventListener('click', () => {
            this.copyResult();
        });

        this.elements.copyPromptBtn.addEventListener('click', () => {
            this.copyPrompt();
        });

        // 键盘快捷键
        document.addEventListener('keydown', (e) => {
            if (e.ctrlKey && e.key === 'Enter') {
                e.preventDefault();
                this.startReview();
            }
        });
    }

    // 页面加载时测试连接
    async testConnectionOnLoad() {
        try {
            await this.testConnection();
        } catch (error) {
            console.warn('初始连接测试失败:', error);
        }
    }

    // 加载可用模板
    async loadTemplates() {
        try {
            const response = await fetch('/api/templates');
            const data = await response.json();
            
            this.templates = {};
            data.templates.forEach(template => {
                this.templates[template.key] = template;
            });

            // 设置默认模板
            this.onTemplateChange();
        } catch (error) {
            console.error('加载模板失败:', error);
            this.showError('无法加载模板列表');
        }
    }

    // 模板选择变化处理
    async onTemplateChange() {
        const selectedKey = this.elements.templateSelect.value;
        
        if (selectedKey === 'custom') {
            // 自定义模板，清空输入框
            this.elements.promptInput.value = '';
            this.elements.promptInput.placeholder = '请输入自定义评审提示词，必须包含 {code} 占位符...';
        } else {
            // 预设模板，从服务器获取模板内容
            try {
                const response = await fetch(`/api/templates`);
                const data = await response.json();
                const template = data.templates.find(t => t.key === selectedKey);
                
                if (template) {
                    // 这里我们需要从后端获取完整的模板内容
                    // 暂时使用一个简化的默认模板
                    const defaultPrompt = this.getDefaultPromptForTemplate(selectedKey);
                    this.elements.promptInput.value = defaultPrompt;
                    this.elements.promptInput.placeholder = `${template.name} - ${template.description}`;
                }
            } catch (error) {
                console.error('获取模板内容失败:', error);
            }
        }
        
        this.updateCharCount();
        this.validatePrompt();
    }

    // 获取默认提示词（简化版本）
    getDefaultPromptForTemplate(templateKey) {
        const prompts = {
            'default': `对以下代码变更进行快速评审，重点关注关键问题。

要求：
1. 简洁明了，只指出重要问题
2. 每个问题控制在3-4句话内
3. 优化建议要具体可执行
4. 如果代码质量良好，简单说明即可
5. 避免过度解释和冗长描述
6. 返回结果过滤思考过程

输出格式（Markdown），不要出现代码包裹：

## 📝 评审总结
[一句话简短总结代码质量（100字以内），关注重点问题]

## 🔍 发现的问题
[如果有问题，用简短条目列出，没有问题则写"未发现明显问题"]

## 💡 优化建议
[针对问题的具体建议，没有则写"代码质量良好"]

代码变更：
{code}`,
            'detailed': `请对以下代码变更进行详细的代码审查，关注代码质量、安全性、性能、最佳实践等方面。请提供具体的改进建议。

输出格式：
请以Markdown格式输出代码审查报告，不要附带额外信息，不要使用markdown包裹，包含以下内容：
- 问题描述和优化建议(如果有)：列出代码中存在的问题，简要说明其影响，并给出优化建议；
- 没有问题就不要赘述；
- 直接返回Markdown内容，不要出现代码包裹；

代码变更：
{code}`,
            'backend': `你是一位资深的软件开发工程师，专注于代码的规范性、功能性、安全性和稳定性。本次任务是对员工的代码进行审查。

重点检查：
1. 代码规范和命名是否清晰
2. 架构分层是否合理
3. 安全性问题（SQL注入、XSS等）
4. 性能优化空间
5. 错误处理机制

输出格式（Markdown）：

## 📝 评审总结
[简短总结]

## 🔍 发现的问题
[问题列表或"未发现明显问题"]

## 💡 优化建议
[具体建议或"代码质量良好"]

代码变更：
{code}`,
            'frontend': `你是一位前端开发工程师，负责审查前端代码的质量、性能和安全性。

重点检查：
1. 前端开发规范（HTML、CSS、JavaScript）
2. 性能问题（页面加载、资源占用）
3. 安全问题（XSS、CSRF等）
4. 可维护性和可扩展性
5. 组件设计合理性

输出格式（Markdown）：

## 📝 评审总结
[简短总结]

## 🔍 发现的问题
[问题列表或"未发现明显问题"]

## 💡 优化建议
[具体建议或"代码质量良好"]

代码变更：
{code}`,
            'doc': `你是一位资深的软件开发文档撰写专家，专注于文档审查。

重点检查：
1. 内容规范（术语统一、描述详细）
2. 表达规范（语言准确、格式统一）
3. 技术规范（接口文档、配置说明）
4. 示例完整性

输出格式（Markdown）：

## 📝 评审总结
[简短总结]

## 🔍 发现的问题
[问题列表或"未发现明显问题"]

## 💡 优化建议
[具体建议或"代码质量良好"]

代码变更：
{code}`
        };
        
        return prompts[templateKey] || prompts['default'];
    }

    // 更新字符计数
    updateCharCount() {
        const text = this.elements.promptInput.value;
        this.elements.charCount.textContent = text.length.toLocaleString();
    }

    // 验证提示词
    validatePrompt() {
        const prompt = this.elements.promptInput.value.trim();
        const hasCodePlaceholder = prompt.includes('{code}');
        
        if (!prompt) {
            this.elements.reviewBtn.disabled = true;
            this.elements.reviewBtn.textContent = '🔍 请输入提示词';
            return false;
        }
        
        if (!hasCodePlaceholder) {
            this.elements.reviewBtn.disabled = true;
            this.elements.reviewBtn.textContent = '🔍 提示词需包含 {code}';
            return false;
        }
        
        this.elements.reviewBtn.disabled = false;
        this.elements.reviewBtn.textContent = '🔍 开始评审示例代码';
        return true;
    }

    // 测试AI服务连接
    async testConnection() {
        const originalText = this.elements.testConnectionBtn.textContent;
        const originalStatus = this.elements.connectionStatus.textContent;
        
        try {
            this.elements.testConnectionBtn.textContent = '测试中...';
            this.elements.testConnectionBtn.disabled = true;
            this.elements.connectionStatus.textContent = '连接中...';
            this.elements.connectionStatus.className = 'status-connecting';
            
            const response = await fetch('/api/test-connection');
            const data = await response.json();
            
            if (data.success) {
                this.elements.connectionStatus.textContent = '已连接';
                this.elements.connectionStatus.className = 'status-connected';
                this.showToast('AI服务连接成功', 'success');
            } else {
                this.elements.connectionStatus.textContent = '连接失败';
                this.elements.connectionStatus.className = 'status-disconnected';
                this.showToast(`连接失败: ${data.message}`, 'error');
            }
        } catch (error) {
            this.elements.connectionStatus.textContent = '连接错误';
            this.elements.connectionStatus.className = 'status-disconnected';
            this.showToast(`连接错误: ${error.message}`, 'error');
        } finally {
            this.elements.testConnectionBtn.textContent = originalText;
            this.elements.testConnectionBtn.disabled = false;
        }
    }

    // 开始代码评审
    async startReview() {
        if (!this.validatePrompt()) {
            return;
        }

        const startTime = Date.now();
        
        try {
            // 显示加载状态
            this.showLoading();
            this.elements.reviewBtn.disabled = true;
            this.elements.reviewBtn.textContent = '🔄 评审中...';
            this.elements.resultStatus.textContent = '评审中';
            this.elements.resultStatus.className = 'result-pending';

            // 构建请求
            const customPrompt = this.elements.promptInput.value.trim();
            
            const requestBody = {
                customPrompt: customPrompt
            };

            // 发送评审请求
            const response = await fetch('/api/review-sample', {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/json'
                },
                body: JSON.stringify(requestBody)
            });

            const data = await response.json();
            const duration = Date.now() - startTime;

            if (data.success) {
                // 显示成功结果
                this.showResult(data, duration);
                this.showToast('代码评审完成', 'success');
            } else {
                // 显示错误
                this.showError(data.error || '评审失败');
                this.showToast('评审失败', 'error');
            }

        } catch (error) {
            const duration = Date.now() - startTime;
            this.showError(`网络错误: ${error.message}`);
            this.showToast('网络请求失败', 'error');
        } finally {
            this.elements.reviewBtn.disabled = false;
            this.elements.reviewBtn.textContent = '🔍 开始评审示例代码';
        }
    }

    // 显示加载状态
    showLoading() {
        this.hideAllStates();
        this.elements.loading.classList.remove('hidden');
    }

    // 显示错误
    showError(message) {
        this.hideAllStates();
        this.elements.errorMessage.textContent = message;
        this.elements.errorDisplay.classList.remove('hidden');
        this.elements.resultStatus.textContent = '评审失败';
        this.elements.resultStatus.className = 'result-error';
        this.elements.resultTime.textContent = '';
    }

    // 显示评审结果
    showResult(data, duration) {
        this.hideAllStates();
        
        // 设置元数据
        this.elements.reviewId.textContent = data.reviewId || 'N/A';
        this.elements.modelUsed.textContent = data.model || 'N/A';
        this.elements.reviewDuration.textContent = `${duration}ms`;
        
        // 渲染Markdown内容
        this.renderMarkdown(data.content || '无评审内容');
        
        // 显示结果区域
        this.elements.resultDisplay.classList.remove('hidden');
        this.elements.resultStatus.textContent = '评审完成';
        this.elements.resultStatus.className = 'result-success';
        this.elements.resultTime.textContent = new Date().toLocaleTimeString();
        
        // 保存当前评审结果
        this.currentReview = {
            content: data.content,
            prompt: this.elements.promptInput.value,
            duration: duration,
            timestamp: new Date().toISOString()
        };
    }

    // 渲染Markdown内容
    renderMarkdown(content) {
        try {
            // 使用marked.js库渲染Markdown
            if (typeof marked !== 'undefined') {
                const html = marked.parse(content);
                this.elements.markdownContent.innerHTML = html;
            } else {
                // 如果marked.js未加载，使用简单的文本显示
                this.elements.markdownContent.innerHTML = `<pre>${this.escapeHtml(content)}</pre>`;
            }
        } catch (error) {
            console.error('Markdown渲染失败:', error);
            this.elements.markdownContent.innerHTML = `<pre>${this.escapeHtml(content)}</pre>`;
        }
    }

    // HTML转义
    escapeHtml(text) {
        const div = document.createElement('div');
        div.textContent = text;
        return div.innerHTML;
    }

    // 隐藏所有状态
    hideAllStates() {
        this.elements.loading.classList.add('hidden');
        this.elements.errorDisplay.classList.add('hidden');
        this.elements.resultDisplay.classList.add('hidden');
        this.elements.emptyState.classList.add('hidden');
    }

    // 清除结果
    clearResults() {
        this.hideAllStates();
        this.elements.emptyState.classList.remove('hidden');
        this.elements.resultStatus.textContent = '等待评审';
        this.elements.resultStatus.className = 'result-pending';
        this.elements.resultTime.textContent = '';
        this.currentReview = null;
        this.showToast('结果已清除', 'info');
    }

    // 复制评审结果
    async copyResult() {
        if (!this.currentReview) {
            this.showToast('没有可复制的结果', 'warning');
            return;
        }

        try {
            await navigator.clipboard.writeText(this.currentReview.content);
            this.showToast('评审结果已复制到剪贴板', 'success');
        } catch (error) {
            console.error('复制失败:', error);
            this.showToast('复制失败', 'error');
        }
    }

    // 复制提示词
    async copyPrompt() {
        const prompt = this.elements.promptInput.value.trim();
        
        if (!prompt) {
            this.showToast('没有可复制的提示词', 'warning');
            return;
        }

        try {
            await navigator.clipboard.writeText(prompt);
            this.showToast('提示词已复制到剪贴板', 'success');
        } catch (error) {
            console.error('复制失败:', error);
            this.showToast('复制失败', 'error');
        }
    }

    // 显示提示消息
    showToast(message, type = 'info') {
        // 创建toast元素
        const toast = document.createElement('div');
        toast.className = `toast toast-${type}`;
        toast.textContent = message;
        
        // 添加样式
        Object.assign(toast.style, {
            position: 'fixed',
            top: '20px',
            right: '20px',
            padding: '12px 20px',
            borderRadius: '6px',
            color: 'white',
            fontWeight: '600',
            zIndex: '10000',
            transition: 'all 0.3s ease',
            transform: 'translateX(100%)',
            opacity: '0'
        });

        // 设置颜色
        const colors = {
            success: '#28a745',
            error: '#dc3545',
            warning: '#ffc107',
            info: '#17a2b8'
        };
        toast.style.backgroundColor = colors[type] || colors.info;

        // 添加到页面
        document.body.appendChild(toast);

        // 显示动画
        setTimeout(() => {
            toast.style.transform = 'translateX(0)';
            toast.style.opacity = '1';
        }, 100);

        // 自动隐藏
        setTimeout(() => {
            toast.style.transform = 'translateX(100%)';
            toast.style.opacity = '0';
            setTimeout(() => {
                if (toast.parentNode) {
                    toast.parentNode.removeChild(toast);
                }
            }, 300);
        }, 3000);
    }
}

// 页面加载完成后初始化
document.addEventListener('DOMContentLoaded', () => {
    new ReviewDebugger();
});