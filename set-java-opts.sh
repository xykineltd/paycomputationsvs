#!/bin/bash
# JMX is disabled by default. To enable locally with password auth, set:
#   JMX_ENABLED=true JMX_PORT=9010
# and configure -Dcom.sun.management.jmxremote.password.file / access.file.
JMX_OPTS=""
if [ "${JMX_ENABLED:-false}" = "true" ]; then
  JMX_OPTS="-Dcom.sun.management.jmxremote \
     -Dcom.sun.management.jmxremote.port=${JMX_PORT:-9010} \
     -Dcom.sun.management.jmxremote.authenticate=true \
     -Dcom.sun.management.jmxremote.ssl=true \
     -Dcom.sun.management.jmxremote.local.only=true"
fi

export JAVA_OPTS="-Xms4g -Xmx8g \
     -XX:+UseG1GC -XX:MaxGCPauseMillis=200 -XX:InitiatingHeapOccupancyPercent=45 \
     -XX:+HeapDumpOnOutOfMemoryError -XX:HeapDumpPath=/var/log/heapdumps \
     -Xlog:gc*:file=/var/log/gc.log:time,uptime,level,tags \
     ${JMX_OPTS}"
