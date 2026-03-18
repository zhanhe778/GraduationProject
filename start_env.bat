@echo off
chcp 65001 >nul

echo ============================================
echo 启动温室项目基础服务（MySQL / Redis / EMQX）
echo 请确保以【管理员身份】运行此脚本
echo ============================================
echo.

:: ---------- 1. 启动 MySQL 服务 ----------
echo [1/3] 启动 MySQL 服务...
net start mysql
echo.

:: ---------- 2. 启动 Redis ----------
echo [2/3] 启动 Redis...

:: 检查 redis 可执行文件是否存在
if not exist "D:\Code\Redis-x64-3.2.100\redis-server.exe" (
    echo   找不到 D:\Code\Redis-x64-3.2.100\redis-server.exe
    echo   请确认 Redis 安装路径是否正确。
) else (
    start "Redis" cmd /k ^
        "cd /d D:\Code\Redis-x64-3.2.100 && redis-server.exe redis.windows.conf"
    echo   已在新窗口中启动 Redis（请查看该窗口确认是否正常运行）。
)
echo.

:: ---------- 3. 启动 EMQX ----------
echo [3/3] 启动 EMQX...

:: EMQX Windows 版 bin 目录下通常有 emqx.cmd
if not exist "D:\Code\emqx-5.3.2-windows-amd64\bin\emqx.cmd" (
    echo   找不到 D:\Code\emqx-5.3.2-windows-amd64\bin\emqx.cmd
    echo   请确认 EMQX 安装路径是否正确。
) else (
    start "EMQX" cmd /k ^
        "cd /d D:\Code\emqx-5.3.2-windows-amd64\bin && emqx.cmd start"
    echo   已在新窗口中启动 EMQX（请查看该窗口确认是否正常运行）。
)
echo.

echo ============================================
echo 所有启动命令已执行，请查看各新窗口输出确认是否成功。
echo ============================================
pause