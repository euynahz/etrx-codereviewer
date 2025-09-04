# Node.js AI代码评审示例项目

这个项目是基于ETRX CodeReviewer插件的默认配置创建的Node.js示例，帮助你快速调试AI代码评审提示词。

## 项目特点

- 🤖 使用ETRX插件的默认AI模型配置 (qwen3:8b)
- 📝 内置所有ETRX插件的默认提示模板
- 🚀 提供完整的REST API接口
- 🔄 支持重试机制和错误处理
- 📊 详细的日志输出，方便调试

## 默认配置

```javascript
{
    modelName: "qwen3:8b",
    endpoint: "http://192.168.66.181:11434",
    apiPath: "/api/generate",
    temperature: 0.7,
    maxTokens: 2048,
    timeout: 30000,
    retryCount: 3
}
```

## 内置提示模板

1. **简洁代码评审** (`default`) - 快速简洁的代码评审，专注关键问题
2. **详细代码评审** (`detailed`) - 全面分析代码质量、安全性、性能等
3. **后端代码评审** (`backend`) - 针对后端代码的规范性、功能性、安全性
4. **前端代码评审** (`frontend`) - 针对前端代码的质量、性能和安全性
5. **开发手册评审** (`doc`) - 针对技术文档的内容、表达和技术规范

## 快速开始

### 方法1: 使用启动脚本（推荐）

1. 双击 `start.bat` 文件，脚本将自动：
   - 安装所有依赖
   - 启动Web服务
   - 打开浏览器到 http://localhost:3000

### 方法2: 手动启动

#### 1. 安装依赖

```bash
npm install
```

#### 2. 启动Web服务

```bash
npm start
```

然后在浏览器中打开 http://localhost:3000

#### 3. 使用Web界面调试提示词

1. **选择或输入提示词**：
   - 从下拉菜单选择预设模板
   - 或选择“自定义模板”手动输入

2. **测试AI连接**：
   - 点击“测试连接”按钮确认AI服务正常

3. **开始评审**：
   - 点击“开始评审示例代码”按钮
   - 在右侧查看Markdown格式的评审结果

4. **复制结果**：
   - 可以复制评审结果或提示词到剪贴板

#### 4. 命令行测试

```bash
npm test
```

## Web界面功能

### 提示词调试界面

- **模板选择**: 从5个预设模板中选择或自定义
- **实时预览**: 显示字符数和模板验证
- **连接测试**: 一键测试AI服务连接状态
- **示例代码**: 内置示例代码变更，方便测试

### 结果展示区域

- **Markdown渲染**: 支持完整的Markdown格式显示
- **详细信息**: 显示评审ID、使用模型、耗时等
- **快捷复制**: 一键复制评审结果或提示词
- **响应式设计**: 适配桌面和移动设备

### 键盘快捷键

- `Ctrl + Enter`: 快速开始评审

## API接口说明

### 测试连接
```http
GET /api/test-connection
```

### 获取可用模板
```http
GET /api/templates
```

### 代码评审
```http
POST /api/review
Content-Type: application/json

{
  "codeChanges": [
    {
      "fileName": "example.js",
      "oldContent": "旧代码内容",
      "newContent": "新代码内容", 
      "changeType": "MODIFIED"
    }
  ],
  "template": "default"
}
```

### 使用示例代码评审
```http
POST /api/review-sample
Content-Type: application/json

{
  "template": "default"
}
```

## 使用示例

### 1. 命令行测试

运行测试脚本查看所有模板的评审效果：

```bash
node test.js
```

### 2. 使用curl测试API

```bash
# 测试连接
curl http://localhost:3000/api/test-connection

# 获取模板列表
curl http://localhost:3000/api/templates

# 使用示例代码评审
curl -X POST http://localhost:3000/api/review-sample \\
  -H "Content-Type: application/json" \\
  -d '{"template": "default"}'
```

### 3. 自定义代码评审

```bash
curl -X POST http://localhost:3000/api/review \\
  -H "Content-Type: application/json" \\
  -d '{
    "codeChanges": [
      {
        "fileName": "user.js",
        "oldContent": "function getUser(id) { return fetch(\"/api/user/\" + id); }",
        "newContent": "async function getUser(id) { if (!id) throw new Error(\"ID required\"); const res = await fetch(\`/api/user/\${id}\`); return res.json(); }",
        "changeType": "MODIFIED"
      }
    ],
    "template": "backend"
  }'
```

## 项目结构

```
nodejs-ai-review-example/
├── public/               # 静态资源文件
│   ├── index.html         # Web界面HTML
│   ├── style.css          # 界面样式
│   └── script.js          # 前端JavaScript逻辑
├── lib/
│   └── ai-reviewer.js     # AI评审器核心类
├── index.js               # Express服务器和API路由
├── test.js                # 测试脚本
├── start.bat              # Windows快速启动脚本
├── package.json           # 项目配置和依赖
└── README.md              # 项目说明
```

## 核心类说明

### AICodeReviewer

主要的AI代码评审器类，提供以下功能：

- `testConnection()` - 测试AI服务连接
- `reviewCode(codeChanges, templateKey)` - 执行代码评审
- `getAvailableTemplates()` - 获取可用模板列表
- `buildCodeContent(codeChanges)` - 构建代码内容字符串

## 调试提示词

1. **查看完整日志**: 运行时会输出详细的请求/响应日志
2. **修改模板**: 直接编辑 `lib/ai-reviewer.js` 中的模板内容
3. **调整参数**: 修改温度、最大token数等参数观察效果
4. **测试不同场景**: 使用不同的代码变更类型 (ADDED/MODIFIED/DELETED)

## 故障排除

### 连接问题
- 检查AI服务是否启动 (默认: http://192.168.66.181:11434)
- 确认模型是否已下载 (qwen3:8b)
- 检查网络连接和防火墙设置

### 评审问题
- 查看控制台日志获取详细错误信息
- 尝试减少代码内容长度
- 调整超时时间配置
- 使用不同的提示模板

## 配置修改

要修改AI服务配置，编辑 `index.js` 或 `lib/ai-reviewer.js` 中的配置对象：

```javascript
const reviewer = new AICodeReviewer({
    modelName: "your-model-name",
    endpoint: "http://your-ai-service:port",
    temperature: 0.5,
    maxTokens: 4096,
    timeout: 60000
});
```

## 开发建议

1. 先运行 `npm test` 确认基本功能正常
2. 使用示例代码测试不同模板的效果
3. 根据需要调整提示词内容和格式
4. 观察AI响应内容，优化提示词指令
5. 测试边界情况（空代码、大文件等）