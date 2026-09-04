#!/bin/sh
set -e
if [ -n "$JAVA_HOME" ]; then java_cmd="$JAVA_HOME/bin/java"; else java_cmd="java"; fi
exec "$java_cmd" -jar "$(dirname "$0")/gradle/wrapper/gradle-wrapper.jar" "$@"
