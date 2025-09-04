const axios = require('axios');
const { v4: uuidv4 } = require('uuid');

// AI代码评审器类
class AICodeReviewer {
    constructor(config = {}) {
        // 使用ETRX插件的默认配置
        this.config = {
            modelName: config.modelName || "qwen3:8b",
            endpoint: config.endpoint || "http://192.168.66.181:11434",
            apiPath: config.apiPath || "/api/generate",
            temperature: config.temperature || 0.7,
            maxTokens: config.maxTokens || 2048,
            timeout: config.timeout || 30000,
            retryCount: config.retryCount || 3,
            ...config
        };

        // ETRX插件的默认提示模板
        this.templates = {
            default: {
                name: "简洁代码评审",
                template: `对以下代码变更进行快速评审，重点关注关键问题。

要求：
1. 简洁明了，只指出重要问题
2. 每个问题控制在3-4句话内
3. 优化建议要具体可执行
4. 如果代码质量良好，简单说明即可
5. 避免过度解释和冗长描述
6. 返回结果过滤思考过程

输出格式（Markdown），不要出现代码包裹：
\`\`\`
## 📝 评审总结
[一句话简短总结代码质量（100字以内），关注重点问题]

## 🔍 发现的问题
[如果有问题，用简短条目列出，没有问题则写"未发现明显问题"]

## 💡 优化建议
[针对问题的具体建议，没有则写"代码质量良好"]
\`\`\`
代码变更：
{code}`,
                description: "快速简洁的代码评审，专注关键问题"
            },
            detailed: {
                name: "详细代码评审",
                template: `请对以下代码变更进行详细的代码审查，关注代码质量、安全性、性能、最佳实践等方面。请提供具体的改进建议。

输出格式：
请以Markdown格式输出代码审查报告，不要附带额外信息，不要使用markdown包裹，包含以下内容：
- 问题描述和优化建议(如果有)：列出代码中存在的问题，简要说明其影响，并给出优化建议；
- 没有问题就不要赘述；
- 直接返回Markdown内容，不要出现代码包裹；

代码变更：
{code}`,
                description: "传统详细的代码评审，适合需要全面分析的场景"
            },
            backend: {
                name: "后端代码评审",
                template: `你是一位资深的软件开发工程师，专注于代码的规范性、功能性、安全性和稳定性。本次任务是对员工的代码进行审查，具体要求如下：
1. 检查是否遵循统一的代码规范（如阿里Java开发手册），类、方法、变量命名是否规范、语义清晰，缩进、空格、注释等格式是否统一。
2. 检查包结构是否合理，分层是否清晰，是否存在单体过大、职责不清的类或方法，是否有重复代码，是否合理抽象和复用。
3. 检查Controller层接口参数、返回值是否规范，是否有统一的响应结构，是否有必要的接口文档注释（如Swagger），且不做业务逻辑处理。
4. 检查Service层是否只处理业务逻辑，事务管理是否合理，是否有事务边界，业务异常是否有统一处理。
5. 检查SQL语句是否安全、性能合理，是否防止SQL注入。
6. 分析代码的性能表现，评估是否存在资源浪费或性能瓶颈。
7. 检查是否防止常见安全漏洞（如XSS、CSRF、SQL注入等），日志中是否避免敏感信息泄露。
8. 检查是否有缓存机制，热点数据是否合理缓存，是否有异步处理、限流、降级等措施，是否有批量处理、分页查询等优化。
9. 检查代码是否易读、易维护，是否有必要的注释，日志记录是否规范，便于问题追踪。
10. 检查Commits信息的清晰性与准确性：检查提交信息是否清晰、准确，是否便于后续维护和协作。

要求：
1. 简洁明了，只指出重要问题
2. 每个问题控制在3-4句话内
3. 优化建议要具体可执行
4. 如果代码质量良好，简单说明即可
5. 避免过度解释和冗长描述
6. 返回结果过滤思考过程

输出格式（Markdown），不要出现代码包裹：
\`\`\`
## 📝 评审总结
[一句话简短总结代码质量（100字以内），关注重点问题]

## 🔍 发现的问题
[如果有问题，用简短条目列出，没有问题则写"未发现明显问题"]

## 💡 优化建议
[针对问题的具体建议，没有则写"代码质量良好"]
\`\`\`
代码变更如下：
{code}`,
                description: "后端代码评审"
            },
            frontend: {
                name: "前端代码评审",
                template: `你是一位前端开发工程师，负责审查前端代码的质量、性能和安全性。本次任务是对员工的代码进行审查，具体要求如下：
1. 检查代码是否符合前端开发规范，包括HTML、CSS、JavaScript等。
2. 检查代码是否存在性能问题，如页面加载速度、资源占用等。
3. 检查代码是否存在安全问题，如XSS、CSRF、SQL注入等。
4. 检查代码是否存在可维护性问题，如代码重复、冗余、注释不足等。
5. 检查代码是否存在可扩展性问题，如代码结构是否清晰、模块是否合理划分等。
6. 检查 Props、data、computed、methods 等属性的使用是否合理，是否避免在 data 中声明函数或复杂对象。

要求：
1. 简洁明了，只指出重要问题
2. 每个问题控制在3-4句话内
3. 优化建议要具体可执行
4. 如果代码质量良好，简单说明即可
5. 避免过度解释和冗长描述
6. 返回结果过滤思考过程

输出格式（Markdown），不要出现代码包裹：
\`\`\`
## 📝 评审总结
[一句话简短总结代码质量（100字以内），关注重点问题]

## 🔍 发现的问题
[如果有问题，用简短条目列出，没有问题则写"未发现明显问题"]

## 💡 优化建议
[针对问题的具体建议，没有则写"代码质量良好"]
\`\`\`
代码变更如下：
{code}`,
                description: "前端代码评审"
            },
            doc: {
                name: "开发手册评审",
                template: `你是一位资深的软件开发文档撰写专家，专注于文档审查。本次任务是对员工的文档进行审查，具体要求如下：
1. 内容规范
     术语统一：专业术语、缩写、命名风格保持一致。
     背景说明：每个模块或功能前有简要背景和作用说明。
     详细描述：对功能、接口、参数、返回值、异常、边界情况等进行详细说明。
     示例丰富：提供典型的代码示例、输入输出样例、场景说明。
     注意事项：列出易错点、限制条件、最佳实践等。
     统一格式：标题、正文、代码块、表格、图片等格式统一，层级分明。
2. 表达规范
     语言简洁准确：避免歧义，表达清晰。
     中英文规范：中文文档用简体中文，英文缩写需首次出现时注明全称。
     代码注释：代码块内注释简明，复杂说明单独列出。
     图文并茂：适当配图辅助说明，图片需有标题和说明。
3. 技术规范
    接口文档：包括接口路径、方法、参数、返回值、异常、示例等。
    配置说明：涉及配置项需详细说明含义、默认值、可选项。
    依赖说明：列出依赖的第三方库、服务、环境等。
    安全与性能：涉及安全、性能的地方需特别说明。

要求：
1. 简洁明了，只指出重要问题
2. 每个问题控制在3-4句话内
3. 优化建议要具体可执行
4. 如果代码质量良好，简单说明即可
5. 避免过度解释和冗长描述
6. 返回结果过滤思考过程

输出格式（Markdown），不要出现代码包裹：
\`\`\`
## 📝 评审总结
[一句话简短总结代码质量（100字以内），关注重点问题]

## 🔍 发现的问题
[如果有问题，用简短条目列出，没有问题则写"未发现明显问题"]

## 💡 优化建议
[针对问题的具体建议，没有则写"代码质量良好"]
\`\`\`

代码变更如下：
{code}`,
                description: "开发手册评审"
            }
        };
    }

    // 获取完整的API URL
    getFullUrl() {
        return `${this.config.endpoint.replace(/\/$/, '')}${this.config.apiPath}`;
    }

    // 构建代码内容字符串
    buildCodeContent(codeChanges) {
        return codeChanges.map(change => {
            let content = `文件: ${change.fileName}\n变更类型: ${change.changeType}\n\n`;
            
            if (change.changeType === 'ADDED') {
                content += `新增内容:\n${change.newContent}`;
            } else if (change.changeType === 'DELETED') {
                content += `删除内容:\n${change.oldContent}`;
            } else if (change.changeType === 'MODIFIED') {
                content += `原内容:\n${change.oldContent}\n\n新内容:\n${change.newContent}`;
            }
            
            return content;
        }).join('\n\n' + '='.repeat(50) + '\n\n');
    }

    // 获取可用的提示模板
    getAvailableTemplates() {
        return Object.keys(this.templates).map(key => ({
            key,
            name: this.templates[key].name,
            description: this.templates[key].description
        }));
    }

    // 测试AI服务连接
    async testConnection() {
        const startTime = Date.now();
        
        try {
            console.log('=== AI服务连接测试开始 ===');
            console.log(`测试配置 - Endpoint: ${this.config.endpoint}, Model: ${this.config.modelName}`);
            console.log(`测试URL: ${this.getFullUrl()}`);
            
            const testRequest = {
                model: this.config.modelName,
                prompt: "Hello, please respond with 'OK' if you can see this message.",
                stream: false,
                options: {
                    num_predict: 10
                }
            };
            
            console.log(`发送测试请求 - 提示词: '${testRequest.prompt}'`);
            
            const response = await axios.post(this.getFullUrl(), testRequest, {
                timeout: Math.min(this.config.timeout, 120000), // 最多2分钟
                headers: {
                    'Content-Type': 'application/json'
                }
            });
            
            const totalTime = Date.now() - startTime;
            
            if (response.status === 200) {
                console.log(`连接测试成功 - HTTP ${response.status}`);
                console.log(`测试响应体长度: ${JSON.stringify(response.data).length} 字符`);
                console.log(`测试耗时: ${totalTime}ms`);
                
                const responseData = response.data;
                if (responseData.response) {
                    const testContent = responseData.response;
                    console.log(`AI响应内容: '${testContent.substring(0, 100)}${testContent.length > 100 ? "..." : ""}'`);
                }
                
                console.log('=== AI服务连接测试成功 ===\\n');
                return true;
            } else {
                console.error(`连接测试失败 - HTTP ${response.status}: ${response.statusText}`);
                console.log(`失败耗时: ${totalTime}ms`);
                console.log('=== AI服务连接测试失败 ===\\n');
                return false;
            }
        } catch (error) {
            const totalTime = Date.now() - startTime;
            console.error(`连接测试异常 - 类型: ${error.constructor.name}, 消息: ${error.message}`);
            console.log(`请求配置 - URL: ${this.getFullUrl()}, Model: ${this.config.modelName}`);
            console.log(`异常耗时: ${totalTime}ms`);
            console.log('=== AI服务连接测试异常结束 ===\\n');
            return false;
        }
    }

    // 使用自定义提示词执行代码评审
    async reviewCodeWithCustomPrompt(codeChanges, customPrompt) {
        const reviewId = this.generateUUID();
        const startTime = Date.now();
        
        try {
            console.log('=== AI代码评审请求开始（自定义提示词）===');
            console.log(`Review ID: ${reviewId}`);
            console.log(`配置信息 - Model: ${this.config.modelName}, Endpoint: ${this.config.endpoint}, Timeout: ${this.config.timeout}ms`);
            console.log(`请求参数 - Temperature: ${this.config.temperature}, MaxTokens: ${this.config.maxTokens}`);
            console.log(`代码变更数量: ${codeChanges.length}`);
            console.log('使用自定义提示词');
            
            const codeContent = this.buildCodeContent(codeChanges);
            const fullPrompt = customPrompt.replace('{code}', codeContent);
            
            console.log(`代码内容长度: ${codeContent.length} 字符`);
            console.log(`完整提示词长度: ${fullPrompt.length} 字符`);
            console.log(`AI请求URL: ${this.getFullUrl()}`);
            
            const ollamaRequest = {
                model: this.config.modelName,
                prompt: fullPrompt,
                stream: false,
                options: {
                    temperature: this.config.temperature,
                    top_p: 0.9,
                    top_k: 40,
                    num_predict: this.config.maxTokens
                }
            };
            
            console.log(`请求选项 - TopP: 0.9, TopK: 40, NumPredict: ${this.config.maxTokens}`);
            
            const response = await this.sendOllamaRequest(ollamaRequest);
            const totalTime = Date.now() - startTime;
            
            if (response.status === 200) {
                const responseData = response.data;
                const reviewContent = responseData.response || '';
                
                console.log(`评审响应成功 - HTTP ${response.status}, 响应体长度: ${JSON.stringify(responseData).length} 字符`);
                console.log(`总耗时: ${totalTime}ms`);
                console.log(`AI评审内容长度: ${reviewContent.length} 字符`);
                console.log(`AI模型: ${responseData.model || "未知"}`);
                console.log(`处理完成 - Done: ${responseData.done}`);
                console.log('=== AI代码评审请求完成（自定义提示词）===\n');
                
                return {
                    id: reviewId,
                    reviewContent: reviewContent,
                    modelUsed: this.config.modelName,
                    promptTemplate: customPrompt,
                    codeChanges: codeChanges,
                    status: 'SUCCESS'
                };
            } else {
                const errorMessage = `HTTP ${response.status}: ${response.statusText}`;
                console.error(`评审请求失败 - ${errorMessage}`);
                console.log(`请求URL: ${this.getFullUrl()}`);
                console.log(`失败耗时: ${totalTime}ms`);
                console.log('=== AI代码评审请求失败（自定义提示词）===\n');
                throw new Error(errorMessage);
            }
            
        } catch (error) {
            const totalTime = Date.now() - startTime;
            
            console.error(`代码评审异常 - 类型: ${error.constructor.name}, 消息: ${error.message}`);
            console.log(`请求配置 - URL: ${this.getFullUrl()}, Model: ${this.config.modelName}`);
            console.log(`异常耗时: ${totalTime}ms`);
            console.log('=== AI代码评审请求异常结束（自定义提示词）===\n');
            
            return {
                id: reviewId,
                reviewContent: '',
                modelUsed: this.config.modelName,
                promptTemplate: customPrompt,
                codeChanges: codeChanges,
                status: 'ERROR',
                errorMessage: error.message || 'Unknown error occurred'
            };
        }
    }
    // 执行代码评审
    async reviewCode(codeChanges, templateKey = 'default') {
        const reviewId = this.generateUUID();
        const startTime = Date.now();
        
        try {
            console.log('=== AI代码评审请求开始 ===');
            console.log(`Review ID: ${reviewId}`);
            console.log(`配置信息 - Model: ${this.config.modelName}, Endpoint: ${this.config.endpoint}, Timeout: ${this.config.timeout}ms`);
            console.log(`请求参数 - Temperature: ${this.config.temperature}, MaxTokens: ${this.config.maxTokens}`);
            console.log(`代码变更数量: ${codeChanges.length}`);
            console.log(`使用模板: ${templateKey}`);
            
            const template = this.templates[templateKey];
            if (!template) {
                throw new Error(`未找到模板: ${templateKey}`);
            }
            
            const codeContent = this.buildCodeContent(codeChanges);
            const fullPrompt = template.template.replace('{code}', codeContent);
            
            console.log(`代码内容长度: ${codeContent.length} 字符`);
            console.log(`完整提示词长度: ${fullPrompt.length} 字符`);
            console.log(`AI请求URL: ${this.getFullUrl()}`);
            
            const ollamaRequest = {
                model: this.config.modelName,
                prompt: fullPrompt,
                stream: false,
                options: {
                    temperature: this.config.temperature,
                    top_p: 0.9,
                    top_k: 40,
                    num_predict: this.config.maxTokens
                }
            };
            
            console.log(`请求选项 - TopP: 0.9, TopK: 40, NumPredict: ${this.config.maxTokens}`);
            
            const response = await this.sendOllamaRequest(ollamaRequest);
            const totalTime = Date.now() - startTime;
            
            if (response.status === 200) {
                const responseData = response.data;
                const reviewContent = responseData.response || '';
                
                console.log(`评审响应成功 - HTTP ${response.status}, 响应体长度: ${JSON.stringify(responseData).length} 字符`);
                console.log(`总耗时: ${totalTime}ms`);
                console.log(`AI评审内容长度: ${reviewContent.length} 字符`);
                console.log(`AI模型: ${responseData.model || "未知"}`);
                console.log(`处理完成 - Done: ${responseData.done}`);
                console.log('=== AI代码评审请求完成 ===\\n');
                
                return {
                    id: reviewId,
                    reviewContent: reviewContent,
                    modelUsed: this.config.modelName,
                    promptTemplate: template.template,
                    codeChanges: codeChanges,
                    status: 'SUCCESS'
                };
            } else {
                const errorMessage = `HTTP ${response.status}: ${response.statusText}`;
                console.error(`评审请求失败 - ${errorMessage}`);
                console.log(`请求URL: ${this.getFullUrl()}`);
                console.log(`失败耗时: ${totalTime}ms`);
                console.log('=== AI代码评审请求失败 ===\\n');
                throw new Error(errorMessage);
            }
            
        } catch (error) {
            const totalTime = Date.now() - startTime;
            
            console.error(`代码评审异常 - 类型: ${error.constructor.name}, 消息: ${error.message}`);
            console.log(`请求配置 - URL: ${this.getFullUrl()}, Model: ${this.config.modelName}`);
            console.log(`异常耗时: ${totalTime}ms`);
            console.log('=== AI代码评审请求异常结束 ===\\n');
            
            return {
                id: reviewId,
                reviewContent: '',
                modelUsed: this.config.modelName,
                promptTemplate: this.templates[templateKey]?.template || '',
                codeChanges: codeChanges,
                status: 'ERROR',
                errorMessage: error.message || 'Unknown error occurred'
            };
        }
    }

    // 发送Ollama请求（带重试机制）
    async sendOllamaRequest(request) {
        for (let attempt = 1; attempt <= this.config.retryCount; attempt++) {
            try {
                console.log(`发送请求尝试 ${attempt}/${this.config.retryCount}`);
                
                const response = await axios.post(this.getFullUrl(), request, {
                    timeout: this.config.timeout,
                    headers: {
                        'Content-Type': 'application/json'
                    }
                });
                
                return response;
            } catch (error) {
                console.warn(`请求尝试 ${attempt} 失败: ${error.message}`);
                
                if (attempt === this.config.retryCount) {
                    throw error;
                }
                
                // 等待一段时间后重试
                const delay = attempt * 1000;
                console.log(`等待 ${delay}ms 后重试...`);
                await new Promise(resolve => setTimeout(resolve, delay));
            }
        }
    }

    // 生成UUID
    generateUUID() {
        return 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g, function(c) {
            const r = Math.random() * 16 | 0;
            const v = c === 'x' ? r : (r & 0x3 | 0x8);
            return v.toString(16);
        });
    }
}

module.exports = AICodeReviewer;