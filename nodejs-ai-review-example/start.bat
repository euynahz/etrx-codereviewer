@echo off
echo 正在安装Node.js依赖...
npm install

if %ERRORLEVEL% NEQ 0 (
    echo 依赖安装失败，请检查网络连接和npm配置
    pause
    exit /b 1
)

echo.
echo 依赖安装完成！
echo.
echo 正在启动AI代码评审调试工具...
echo 请稍候，浏览器将自动打开...
echo.

timeout /t 2 /nobreak > nul
start http://localhost:3000
npm start