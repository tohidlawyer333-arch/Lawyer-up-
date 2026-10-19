#!/bin/sh

# Gradle Wrapper-compatible bootstrap for Gradle 8.7.
# If the official wrapper JAR is supplied later, this script delegates to it.
set -e
APP_HOME=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
WRAPPER_JAR="$APP_HOME/gradle/wrapper/gradle-wrapper.jar"
if [ -f "$WRAPPER_JAR" ] && command -v java >/dev/null 2>&1; then
  exec java -Dorg.gradle.appname=gradlew -classpath "$WRAPPER_JAR" org.gradle.wrapper.GradleWrapperMain "$@"
fi
if command -v gradle >/dev/null 2>&1; then
  exec gradle "$@"
fi
DIST="$APP_HOME/.gradle-local/gradle-8.7"
if [ ! -x "$DIST/bin/gradle" ]; then
  tmp="$APP_HOME/.gradle-local/gradle-8.7-bin.zip"
  mkdir -p "$APP_HOME/.gradle-local"
  if command -v curl >/dev/null 2>&1; then curl -fsSL -o "$tmp" https://services.gradle.org/distributions/gradle-8.7-bin.zip
  elif command -v wget >/dev/null 2>&1; then wget -q -O "$tmp" https://services.gradle.org/distributions/gradle-8.7-bin.zip
  else echo "Gradle 8.7 is unavailable: install Gradle or provide gradle-wrapper.jar" >&2; exit 1; fi
  if command -v unzip >/dev/null 2>&1; then unzip -q -o "$tmp" -d "$APP_HOME/.gradle-local"; else echo "unzip is required to bootstrap Gradle 8.7" >&2; exit 1; fi
fi
exec "$DIST/bin/gradle" "$@"
