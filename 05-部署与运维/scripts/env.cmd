@echo off
rem ============================================================
rem  开发环境变量设置脚本（命令行用）
rem  用途：在任意新的命令行窗口中执行  env.cmd  即可设置好
rem        JAVA_HOME / MAVEN_HOME / Path，然后就能直接用 java、mvn
rem  说明：用户级环境变量已写入注册表，但已打开的窗口不会自动生效；
rem        双击或 call 本脚本可立即生效。
rem ============================================================
set "JAVA_HOME=D:\Dev\jdk8"
set "MAVEN_HOME=D:\Dev\maven"
set "MYSQL_HOME=D:\Dev\mysql8"
set "PATH=%JAVA_HOME%\bin;%MAVEN_HOME%\bin;%MYSQL_HOME%\bin;%PATH%"

echo [env] JAVA_HOME  = %JAVA_HOME%
echo [env] MAVEN_HOME = %MAVEN_HOME%
echo [env] MYSQL_HOME = %MYSQL_HOME%
echo [env] Path 已追加 JDK / Maven / MySQL 的 bin 目录
echo.
echo 现在可以直接使用：java -version / mvn -v / mysql --version
