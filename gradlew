#!/bin/sh
# Bootstrap script for the source-only distribution. The official wrapper JAR
# could not be bundled in this offline workspace. See README to generate it.
if [ -f "$(dirname "$0")/gradle/wrapper/gradle-wrapper.jar" ]; then
  exec java -classpath "$(dirname "$0")/gradle/wrapper/gradle-wrapper.jar" org.gradle.wrapper.GradleWrapperMain "$@"
fi
if command -v gradle >/dev/null 2>&1; then exec gradle "$@"; fi
printf '%s\n' 'Gradle not installed. Install Gradle 8.10.2 or run the included GitHub Actions workflow.' >&2
exit 127
