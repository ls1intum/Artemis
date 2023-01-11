#!/bin/bash

# Entrypoint file for Docker Images of Artemis. The deployment of the application is set to /opt/artemis

cd /opt/artemis || exit 1

if [ -z "$(ls -A config)" ]; then
   echo "Config is Empty .. copying default ones .."
   cp -n -a /defaults/artemis/. config/
else
   echo "Config is not empty .. not copying default configs .."
fi

# Ensure at least the directories are owned by artemis. "-R" takes too long
chown artemis:artemis config data

wget "https://search.maven.org/remotecontent?filepath=org/jacoco/jacoco/0.8.8/jacoco-0.8.8.zip" -O temp.zip
unzip temp.zip "lib/jacocoagent.jar" -d .
mv lib/jacocoagent.jar .
rm -rf lib temp.zip

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
  -javaagent:jacocoagent.jar=output=tcpserver,address=* \
  -jar Artemis.war
