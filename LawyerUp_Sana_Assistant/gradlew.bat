@ECHO OFF
SETLOCAL
SET APP_HOME=%~dp0
SET WRAPPER_JAR=%APP_HOME%gradle\wrapper\gradle-wrapper.jar
IF EXIST "%WRAPPER_JAR%" IF DEFINED JAVA_HOME "%JAVA_HOME%\bin\java.exe" -Dorg.gradle.appname=gradlew -classpath "%WRAPPER_JAR%" org.gradle.wrapper.GradleWrapperMain %* & EXIT /B %ERRORLEVEL%
where gradle >NUL 2>&1
IF %ERRORLEVEL% EQU 0 (gradle %* & EXIT /B %ERRORLEVEL%)
ECHO Gradle 8.7 is unavailable. Install Gradle or provide gradle-wrapper.jar. 1>&2
EXIT /B 1
