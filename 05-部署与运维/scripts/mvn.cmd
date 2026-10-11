@echo off
rem ============================================================
rem  Maven 包装脚本（不依赖当前窗口的 JAVA_HOME）
rem  用途：把后端构建交给 DSH / 脚本调用时，避免因 JAVA_HOME 未生效而失败
rem  用法：scripts\mvn.cmd clean package
rem        scripts\mvn.cmd -v
rem ============================================================
setlocal
set "JAVA_HOME=D:\Dev\jdk8"
set "MAVEN_HOME=D:\Dev\maven"
set "PATH=%JAVA_HOME%\bin;%MAVEN_HOME%\bin;%PATH%"
call "%MAVEN_HOME%\bin\mvn.cmd" %*
exit /b %ERRORLEVEL%
