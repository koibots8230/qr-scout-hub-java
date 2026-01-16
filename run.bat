@ECHO OFF

IF "%MAIN_CLASS%"=="" (
  set MAIN_CLASS=com.koibots.scout.hub.Main
)
java -classpath "target/classes;target/app/*;target/dependency/*;src/main/resources/" "%MAIN_CLASS%" %*
