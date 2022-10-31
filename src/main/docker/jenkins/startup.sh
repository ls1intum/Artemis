#! /usr/bin/env bash

chgrp docker /var/run/docker.sock
/sbin/tini -s -- /usr/local/bin/jenkins.sh
