#!/bin/bash

cd /opt/Artemis

if [ -z "$(ls -A config)" ]; then
   echo "Config is Empty .. copying default ones .."
   cp -n -a /defaults/Artemis/. config/
else
   echo "Config is not empty .. not copying default configs .."
fi

# Create sync file that indicates waiting for services
mkdir -p sync
touch sync/waiting-backend

# Ensure at least the directories are owned by artemis. "-R" takes too long
chown artemis:artemis config data sync

# Allow waiting for other services
if [ -n "${WAIT_FOR}" ]; then
  hosts_ports=$(echo $WAIT_FOR | tr "," "\n" | tr -d "\"")
  for host_port in $hosts_ports
  do
    until [[ "$(curl -s -o /dev/null -L -w ''%{http_code}'' http://$host_port)" == "200" ]]
    do
      echo "Waiting for $host_port"
      sleep 5
    done
  done
fi

# Release indicator of startup after 90 seconds (may be used by nginx proxy to determine startup)
bash -c "sleep 90 && rm -f sync/waiting-backend" &

echo "Starting application..."
exec gosu artemis java \
  -Djdk.tls.ephemeralDHKeySize=2048 \
  -DLC_CTYPE=UTF-8 \
  -Dfile.encoding=UTF-8 \
  -Dsun.jnu.encoding=UTF-8 \
  -Djava.security.egd=file:/dev/./urandom \
  -Xmx2048m \
  --add-modules java.se \
  --add-exports java.base/jdk.internal.ref=ALL-UNNAMED \
  --add-exports java.naming/com.sun.jndi.ldap=ALL-UNNAMED \
  --add-opens java.base/java.lang=ALL-UNNAMED \
  --add-opens java.base/java.nio=ALL-UNNAMED \
  --add-opens java.base/sun.nio.ch=ALL-UNNAMED \
  --add-opens java.management/sun.management=ALL-UNNAMED \
  --add-opens jdk.management/com.sun.management.internal=ALL-UNNAMED \
  -jar Artemis.war \
  --spring.profiles.active=$PROFILES
