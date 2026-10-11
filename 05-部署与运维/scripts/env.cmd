@echo off
rem ============================================================
rem  Set JAVA_HOME / MAVEN_HOME / MYSQL_HOME for the current shell
rem  Usage: call <project>\05-\scripts\env.cmd
rem  Note : User-level environment variables are already stored in the
rem         registry, but an already-open console does not pick them up.
rem         Sourcing this file applies them immediately.
rem ============================================================
set "JAVA_HOME=D:\Dev\jdk8"
set "MAVEN_HOME=D:\Dev\maven"
set "MYSQL_HOME=D:\Dev\mysql8"
set "PATH=%JAVA_HOME%\bin;%MAVEN_HOME%\bin;%MYSQL_HOME%\bin;%PATH%"

echo [env] JAVA_HOME  = %JAVA_HOME%
echo [env] MAVEN_HOME = %MAVEN_HOME%
echo [env] MYSQL_HOME = %MYSQL_HOME%
echo [env] PATH updated with the JDK / Maven / MySQL bin directories
echo.
echo You can now run: java -version / mvn -v / mysql --version
