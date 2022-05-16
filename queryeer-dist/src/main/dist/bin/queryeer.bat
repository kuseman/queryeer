@ECHO OFF
setlocal enabledelayedexpansion
SET SCRIPT_DIR=%~dp0
SET "APP_HOME=%SCRIPT_DIR%.."
SET "LIB_DIR=%APP_HOME%\lib"
SET "ETC_DIR=%APP_HOME%\etc"
SET "PLUGINS_DIR=%APP_HOME%\plugins"
SET "SHARED_DIR=%APP_HOME%\shared"

SET "CLASSPATH="
PushD "%LIB_DIR%"
for %%A in (*.jar) do (
   SET "CLASSPATH=!CLASSPATH!;!LIB_DIR!\%%A"
)

java -cp "%CLASSPATH%" -Dfile.encoding=UTF-8 -Detc="%ETC_DIR%" -Dplugins="%PLUGINS_DIR%" -Dshared="%SHARED_DIR%" com.queryeer.Main
