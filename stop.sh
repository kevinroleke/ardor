#!/bin/sh

DIR=`dirname "$0"`
cd "${DIR}"

# setenv.sh can be locally used to provide environment variables values
if [ -r ./setenv.sh ]; then
  . ./setenv.sh
fi

if [ -z "${ARDOR_PID_FILE}" ]; then
    ARDOR_PID_FILE=~/.ardor/nxt.pid
fi

if [ -e ${ARDOR_PID_FILE} ]; then
    PID=`cat ${ARDOR_PID_FILE}`
    ps -p $PID > /dev/null
    STATUS=$?
    echo "stopping"
    while [ $STATUS -eq 0 ]; do
        kill `cat ${ARDOR_PID_FILE}` > /dev/null
        sleep 5
        ps -p $PID > /dev/null
        STATUS=$?
    done
    rm -f ${ARDOR_PID_FILE}
    echo "Ardor server stopped"
fi

cd - > /dev/null
