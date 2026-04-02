#!/bin/bash

SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" &> /dev/null && pwd )"
APP_HOME="${SCRIPT_DIR}/.."
LIB_DIR="${APP_HOME}/lib"
#ETC_DIR="${APP_HOME}/etc"
PLUGINS_DIR="${APP_HOME}/plugins"
SHARED_DIR="${APP_HOME}/shared"

CLASSPATH=""
for filepath in "${LIB_DIR}"/*; do
  CLASSPATH="${CLASSPATH}:${filepath}"
done

# Resolve Java executable: bundled jre > JAVA_HOME > PATH
JAVA_EXE="java"
if [ -x "${APP_HOME}/jre/bin/java" ]; then
  JAVA_EXE="${APP_HOME}/jre/bin/java"
elif [ -n "${JAVA_HOME}" ] && [ -x "${JAVA_HOME}/bin/java" ]; then
  JAVA_EXE="${JAVA_HOME}/bin/java"
fi

"${JAVA_EXE}" -cp "${CLASSPATH}" -Dfile.encoding=UTF-8 -Dplugins="${PLUGINS_DIR}" -Dshared="${SHARED_DIR}" com.queryeer.Main
