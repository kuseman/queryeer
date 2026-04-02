@ECHO OFF
setlocal enabledelayedexpansion
SET SCRIPT_DIR=%~dp0
SET "APP_HOME=%SCRIPT_DIR%.."
SET "LIB_DIR=%APP_HOME%\lib"
REM SET "ETC_DIR=%APP_HOME%\.queryeer"
SET "PLUGINS_DIR=%APP_HOME%\plugins"
SET "SHARED_DIR=%APP_HOME%\shared"

REM Resolve Java executable: bundled jre > JAVA_HOME > PATH
SET "JAVA_EXE=java"
IF EXIST "%APP_HOME%\jre\bin\java.exe" (
    SET "JAVA_EXE=%APP_HOME%\jre\bin\java.exe"
) ELSE IF DEFINED JAVA_HOME (
    IF EXIST "%JAVA_HOME%\bin\java.exe" SET "JAVA_EXE=%JAVA_HOME%\bin\java.exe"
)

SET "CLASSPATH="
PushD "%LIB_DIR%"
for %%A in (*.jar) do (
   SET "CLASSPATH=!CLASSPATH!;!LIB_DIR!\%%A"
)

"%JAVA_EXE%" -cp "%CLASSPATH%" -Dfile.encoding=UTF-8 -Dplugins="%PLUGINS_DIR%" -Dshared="%SHARED_DIR%" com.queryeer.Main
