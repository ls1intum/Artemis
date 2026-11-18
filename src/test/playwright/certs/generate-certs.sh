#!/usr/bin/env ash

# pass arguments from the docker run command to mkcert
mkcert $@
# copy the generated CA file in order to generate a new one
cp /root/.local/share/mkcert/rootCA.pem .
