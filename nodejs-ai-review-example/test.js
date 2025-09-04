// 测试脚本 - 验证AI代码评审功能

const AICodeReviewer = require('./lib/ai-reviewer');

// 创建AI评审器实例
const reviewer = new AICodeReviewer();

// 测试代码变更
const testCodeChanges = [
    {
        fileName: "utils.js",
        oldContent: `function processData(data) {
    var result = [];
    for (var i = 0; i < data.length; i++) {
        if (data[i].status == 'active') {
            result.push(data[i]);
        }
    }
    return result;
}`,
        newContent: `function processData(data) {
    if (!Array.isArray(data)) {
        throw new Error('Input must be an array');
    }
    
    return data.filter(item => 
        item && item.status === 'active'
    );
}`,
        changeType: "MODIFIED"
    },
    {
        fileName: "api.js",
        oldContent: "",
        newContent: `const express = require('express');
const router = express.Router();

// 用户登录接口
router.post('/login', (req, res) => {
    const { username, password } = req.body;
    
    // 简单的用户验证逻辑
    if (username === 'admin' && password === 'admin123') {
        res.json({ 
            success: true, 
            token: 'fake-jwt-token',
            user: { id: 1, username: 'admin' }
        });
    } else {
        res.status(401).json({ 
            success: false, 
            message: '用户名或密码错误' 
        });
    }
});

module.exports = router;`,
        changeType: "ADDED"
    }
];

async function runTests() {
    console.log('🚀 开始AI代码评审测试\\n');

    // 测试1: 连接测试
    console.log('📡 测试1: AI服务连接测试');
    const isConnected = await reviewer.testConnection();
    console.log(`连接结果: ${isConnected ? '✅ 成功' : '❌ 失败'}\\n`);

    if (!isConnected) {
        console.log('❌ AI服务连接失败，请检查配置');
        console.log(`当前配置:`);
        console.log(`- 模型: ${reviewer.config.modelName}`);
        console.log(`- 端点: ${reviewer.config.endpoint}`);
        console.log(`- API路径: ${reviewer.config.apiPath}`);
        console.log(`- 完整URL: ${reviewer.getFullUrl()}`);
        return;
    }

    // 测试2: 获取可用模板
    console.log('📋 测试2: 获取可用模板');
    const templates = reviewer.getAvailableTemplates();
    console.log('可用模板:');
    templates.forEach(template => {
        console.log(`- ${template.key}: ${template.name} (${template.description})`);
    });
    console.log();

    // 测试3: 使用不同模板进行代码评审
    const testTemplates = ['default', 'detailed', 'backend', 'frontend'];
    
    for (const templateKey of testTemplates) {
        console.log(`🔍 测试3.${testTemplates.indexOf(templateKey) + 1}: 使用 "${templateKey}" 模板评审代码`);
        
        try {
            const result = await reviewer.reviewCode(testCodeChanges, templateKey);
            
            if (result.status === 'SUCCESS') {
                console.log('✅ 评审成功');
                console.log(`评审ID: ${result.id}`);
                console.log(`使用模型: ${result.modelUsed}`);
                console.log(`评审内容长度: ${result.reviewContent.length} 字符`);
                console.log('📝 评审结果预览:');
                console.log('─'.repeat(60));
                console.log(result.reviewContent.substring(0, 300) + (result.reviewContent.length > 300 ? '...' : ''));
                console.log('─'.repeat(60));
            } else {
                console.log('❌ 评审失败');
                console.log(`错误信息: ${result.errorMessage}`);
            }
        } catch (error) {
            console.log('❌ 评审异常');
            console.error(`错误: ${error.message}`);
        }
        
        console.log();
    }

    console.log('🎉 测试完成！');
}

// 运行测试
runTests().catch(error => {
    console.error('测试失败:', error);
    process.exit(1);
});