#!/bin/sh

echo "************************************************"
echo "**             Running tax report             **"
echo "************************************************"
sleep 1

if [ -x jdk/bin/java ]; then
    JAVA=./jdk/bin/java
else
    JAVA=java
fi
${JAVA} -cp classes:lib/*:conf:addons/classes:addons/lib/*:javafx-sdk/lib/* -Dnxt.runtime.mode=desktop nxt.tools.RunTaxReport $@
exit $?
