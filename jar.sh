#!/bin/sh
if [ -x jdk/bin/java ]; then
    JAVA=./jdk/bin/java
    JAR=./jdk/bin/jar
elif [ -x ../jdk/bin/java ]; then
    JAVA=../jdk/bin/java
    JAR=../jdk/bin/jar
else
    JAVA=java
    JAR=jar
fi
${JAVA} -cp classes nxt.tools.ManifestGenerator
/bin/rm -f ardor.jar
${JAR} cfm ardor.jar resource/ardor.manifest.mf -C classes . || exit 1

echo "jar file generated successfully"