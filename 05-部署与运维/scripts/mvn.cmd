@echo off
rem ============================================================
rem  Maven wrapper - does not depend on JAVA_HOME being set
rem  Usage: <project>\05-\scripts\mvn.cmd clean package
rem         <project>\05-\scripts\mvn.cmd -v
rem ============================================================
setlocal
set "JAVA_HOME=D:\Dev\jdk8"
set "MAVEN_HOME=D:\Dev\maven"
set "PATH=%JAVA_HOME%\bin;%MAVEN_HOME%\bin;%PATH%"
call "%MAVEN_HOME%\bin\mvn.cmd" %*
exit /b %ERRORLEVEL%
