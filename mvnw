#!/bin/sh
export JAVA_HOME="/Library/Java/JavaVirtualMachines/jdk-24.jdk/Contents/Home"
MAVEN_HOME="/Users/personal/.gemini/antigravity/scratch/tools/apache-maven-3.9.9"
exec "$MAVEN_HOME/bin/mvn" "$@"

