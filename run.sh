#!/bin/sh

MAIN_CLASS="${MAIN_CLASS:-com.koibots.scout.hub.Main}"
MAIN_JAR=$( echo target/qr-scout-*.jar )
java -classpath "$MAIN_JAR":'target/classes':'target/app/*':'target/dependency/*':src/main/resources/ $JAVA_OPTS "$MAIN_CLASS" "$@"
