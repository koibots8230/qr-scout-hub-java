#!/bin/sh

MAIN_CLASS="${MAIN_CLASS:-com.koibots.scout.hub.Main}"
java -classpath 'target/classes':'target/app/*':'target/dependency/*':src/main/resources/ "$MAIN_CLASS" "$@"
