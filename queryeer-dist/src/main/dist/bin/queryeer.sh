#!/bin/bash

SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" &> /dev/null && pwd )"
APP_HOME="${SCRIPT_DIR}/.."
LIB_DIR="${APP_HOME}/lib"
ETC_DIR="${APP_HOME}/etc"
PLUGINS_DIR="${APP_HOME}/plugins"
SHARED_DIR="${APP_HOME}/shared"

CLASSPATH=""
for filepath in "${LIB_DIR}"/*; do
  CLASSPATH="${CLASSPATH}:${filepath}"
done

java -cp "${CLASSPATH}" -Detc="${ETC_DIR}" -Dplugins="${PLUGINS_DIR}" -Dshared="${SHARED_DIR}" com.queryeer.Main
