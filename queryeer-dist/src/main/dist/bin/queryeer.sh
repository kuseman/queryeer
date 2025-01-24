#!/bin/bash

SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" &> /dev/null && pwd )"
APP_HOME="${SCRIPT_DIR}/.."
LIB_DIR="${APP_HOME}/lib"
#ETC_DIR="${APP_HOME}/etc"
PLUGINS_DIR="${APP_HOME}/plugins"
SHARED_DIR="${APP_HOME}/shared"

#To change look and feel set LAF property to the LAF's class name
#LAF="javax.swing.plaf.metal.MetalLookAndFeel"
LAF=""

CLASSPATH=""
for filepath in "${LIB_DIR}"/*; do
  CLASSPATH="${CLASSPATH}:${filepath}"
done

java -cp "${CLASSPATH}" -Dfile.encoding=UTF-8 -Dplugins="${PLUGINS_DIR}" -Dshared="${SHARED_DIR}" -Dlaf="${LAF}" com.queryeer.Main
