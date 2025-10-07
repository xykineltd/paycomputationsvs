#!/bin/bash
export JAVA_OPTS="-Xms4g -Xmx8g \
     -XX:+UseG1GC -XX:MaxGCPauseMillis=200 -XX:InitiatingHeapOccupancyPercent=45 \
     -XX:+HeapDumpOnOutOfMemoryError -XX:HeapDumpPath=/var/log/heapdumps \
     -Xlog:gc*:file=/var/log/gc.log:time,uptime,level,tags \
     -Dcom.sun.management.jmxremote \
     -Dcom.sun.management.jmxremote.port=9010 \
     -Dcom.sun.management.jmxremote.authenticate=false \
     -Dcom.sun.management.jmxremote.ssl=false"

