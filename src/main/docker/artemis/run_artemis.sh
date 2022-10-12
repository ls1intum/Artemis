#!/bin/bash

# Entrypoint file for Docker Images of Artemis. The deployment of the application is set to /opt/artemis
# TODO: maybe think about setting this not just after the environment as you mit want to also debug a prod environment?
if [[ "$SPRING_PROFILES_ACTIVE" =~ .*"dev".* ]]; then
    RemoteDebuggingOption="-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=*:5005"
else
    RemoteDebuggingOption=""
fi

echo "Starting application..."
exec java \
  ${RemoteDebuggingOption} \
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
  -jar /opt/artemis/Artemis.war
