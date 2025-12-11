#!/bin/sh

MAIN_CLASS="${MAIN_CLASS:-com.koibots.scout.hub.Main}"
java -classpath 'target/classes':'target/dependency/*':src/main/resources/ "$MAIN_CLASS" "$@"
