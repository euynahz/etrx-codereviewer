// AI代码评审示例项目
// 使用ETRX插件的默认配置进行代码评审

const AICodeReviewer = require('./lib/ai-reviewer');
const express = require('express');

// 创建Express应用
const app = express();
app.use(express.json());
app.use(express.static('public')); // 提供静态文件服务

// AI评审器实例
const reviewer = new AICodeReviewer({
    // 使用ETRX插件的默认配置
    modelName: "qwen3:8b",
    endpoint: "http://192.168.66.181:11434",
    apiPath: "/api/generate",
    temperature: 0.7,
    maxTokens: 2048,
    timeout: 30000,
    retryCount: 3
});

// 示例代码变更（用于测试评审）
const sampleCodeChanges = [
    {
        fileName: "user.js",
        oldContent: `function getUser(id) {
    return fetch('/api/user/' + id).then(r => r.json());
}`,
        newContent: `async function getUser(id) {
    if (!id) {
        throw new Error('User ID is required');
    }
    
    try {
        const response = await fetch(\`/api/user/\${id}\`);
        if (!response.ok) {
            throw new Error(\`HTTP error! status: \${response.status}\`);
        }
        return await response.json();
    } catch (error) {
        console.error('Failed to fetch user:', error);
        throw error;
    }
}`,
        changeType: "MODIFIED"
    },
    {
        fileName: "auth.js",
        oldContent: "",
        newContent: `const jwt = require('jsonwebtoken');

function authenticateToken(req, res, next) {
    const authHeader = req.headers['authorization'];
    const token = authHeader && authHeader.split(' ')[1];
    
    if (token == null) return res.sendStatus(401);
    
    jwt.verify(token, process.env.ACCESS_TOKEN_SECRET, (err, user) => {
        if (err) return res.sendStatus(403);
        req.user = user;
        next();
    });
}

module.exports = { authenticateToken };`,
        changeType: "ADDED"
    }
];

// API路由：测试连接
app.get('/api/test-connection', async (req, res) => {
    try {
        console.log('测试AI服务连接...');
        const isConnected = await reviewer.testConnection();
        res.json({ 
            success: isConnected,
            message: isConnected ? 'AI服务连接成功' : 'AI服务连接失败'
        });
    } catch (error) {
        console.error('连接测试失败:', error);
        res.status(500).json({ 
            success: false, 
            error: error.message 
        });
    }
});

// API路由：代码评审
app.post('/api/review', async (req, res) => {
    try {
        const { codeChanges, template = 'default' } = req.body;
        
        console.log(`开始代码评审，使用模板: ${template}`);
        console.log(`代码变更数量: ${codeChanges ? codeChanges.length : 0}`);
        
        const changes = codeChanges || sampleCodeChanges;
        const result = await reviewer.reviewCode(changes, template);
        
        res.json({
            success: true,
            reviewId: result.id,
            content: result.reviewContent,
            model: result.modelUsed,
            status: result.status
        });
    } catch (error) {
        console.error('代码评审失败:', error);
        res.status(500).json({ 
            success: false, 
            error: error.message 
        });
    }
});

// API路由：获取可用的提示模板
app.get('/api/templates', (req, res) => {
    const templates = reviewer.getAvailableTemplates();
    res.json({ templates });
});

// API路由：使用示例代码进行评审
app.post('/api/review-sample', async (req, res) => {
    try {
        const { template = 'default', customPrompt } = req.body;
        
        console.log(`使用示例代码进行评审，模板: ${template}`);
        
        let result;
        if (customPrompt) {
            console.log('使用自定义提示词进行评审');
            // 使用自定义提示词
            result = await reviewer.reviewCodeWithCustomPrompt(sampleCodeChanges, customPrompt);
        } else {
            // 使用预设模板
            result = await reviewer.reviewCode(sampleCodeChanges, template);
        }
        
        res.json({
            success: true,
            reviewId: result.id,
            content: result.reviewContent,
            model: result.modelUsed,
            codeChanges: sampleCodeChanges.map(c => ({
                fileName: c.fileName,
                changeType: c.changeType
            }))
        });
    } catch (error) {
        console.error('示例代码评审失败:', error);
        res.status(500).json({ 
            success: false, 
            error: error.message 
        });
    }
});

// 启动服务器
const PORT = process.env.PORT || 3000;
app.listen(PORT, () => {
    console.log(`🚀 AI代码评审服务已启动`);
    console.log(`📡 服务地址: http://localhost:${PORT}`);
    console.log(`🤖 AI模型: ${reviewer.config.modelName}`);
    console.log(`🔗 AI服务端点: ${reviewer.config.endpoint}`);
    console.log(`\n可用的API端点:`);
    console.log(`  GET  /api/test-connection  - 测试AI服务连接`);
    console.log(`  GET  /api/templates        - 获取可用模板`);
    console.log(`  POST /api/review           - 代码评审`);
    console.log(`  POST /api/review-sample    - 使用示例代码评审`);
});

module.exports = app;