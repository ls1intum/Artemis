#!/bin/bash

set -e

SOURCE_DIR="/git_source"
DIST_DIR="/usr"

: ${BITBUCKET_VERSION=}
BITBUCKET_MINOR_VERSION=$(echo "${BITBUCKET_VERSION}" | cut -d. -f-2)


case ${BITBUCKET_MINOR_VERSION} in
    6.0)  SUPPORTED_GIT_VERSION="2.20" ;;
    6.1)  SUPPORTED_GIT_VERSION="2.20" ;;
    6.1)  SUPPORTED_GIT_VERSION="2.20" ;;
    6.2)  SUPPORTED_GIT_VERSION="2.21" ;;
    6.3)  SUPPORTED_GIT_VERSION="2.21" ;;
    6.4)  SUPPORTED_GIT_VERSION="2.22" ;;
    6.5)  SUPPORTED_GIT_VERSION="2.22" ;;
    6.6)  SUPPORTED_GIT_VERSION="2.23" ;;
    6.7)  SUPPORTED_GIT_VERSION="2.23" ;;
    6.8)  SUPPORTED_GIT_VERSION="2.24" ;;
    6.9)  SUPPORTED_GIT_VERSION="2.24" ;;
    6.10) SUPPORTED_GIT_VERSION="2.24" ;;
    7.0)  SUPPORTED_GIT_VERSION="2.25" ;;
    7.1)  SUPPORTED_GIT_VERSION="2.25" ;;
    7.2)  SUPPORTED_GIT_VERSION="2.26" ;;
    7.3)  SUPPORTED_GIT_VERSION="2.27" ;;
    7.4)  SUPPORTED_GIT_VERSION="2.27" ;;
    7.5)  SUPPORTED_GIT_VERSION="2.28" ;;
    7.6)  SUPPORTED_GIT_VERSION="2.28" ;;
    7.7)  SUPPORTED_GIT_VERSION="2.29" ;;
    7.8)  SUPPORTED_GIT_VERSION="2.29" ;;
    7.9)  SUPPORTED_GIT_VERSION="2.30" ;;
    7.10) SUPPORTED_GIT_VERSION="2.30" ;;
    7.11) SUPPORTED_GIT_VERSION="2.30" ;;
    7.12) SUPPORTED_GIT_VERSION="2.31" ;;
    7.13) SUPPORTED_GIT_VERSION="2.31" ;;
    7.14) SUPPORTED_GIT_VERSION="2.32" ;;
    7.15) SUPPORTED_GIT_VERSION="2.32" ;;
    7.16) SUPPORTED_GIT_VERSION="2.33" ;;
      *)  SUPPORTED_GIT_VERSION="2.33" ;;
esac


mkdir -p ${DIST_DIR}

# Install build dependencies
echo "Installing git build dependencies"
apt-get update
apt-get install -y --no-install-recommends git dh-autoreconf libcurl4-gnutls-dev libexpat1-dev libz-dev libssl-dev

# cut -c53- here drops the SHA (40), tab (1) and "refs/tags/v" (11), because some things, like the
# snapshot URL and tarball root directory, don't have the leading "v" from the tag in them
# Thanks to Bryan Turner for this improved method of retrieving the appropriate git version
GIT_VERSION=$(git ls-remote git://git.kernel.org/pub/scm/git/git.git | cut -c53- | grep "^${SUPPORTED_GIT_VERSION}\.[0-9\.]\+$" | sort -V | tail -n 1)
curl -s -o - "https://git.kernel.org/pub/scm/git/git.git/snapshot/git-${GIT_VERSION}.tar.gz" | tar -xz --strip-components=1 --one-top-level="${SOURCE_DIR}"
cd "${SOURCE_DIR}"

# Uninstall pkg git
apt-get purge -y git

# Install git from source
make configure
./configure --prefix=${DIST_DIR}
make -j`nproc` NO_TCLTK=1 NO_GETTEXT=1 install

# Remove and clean up dependencies
cd /
rm -rf ${SOURCE_DIR}
apt-get purge -y dh-autoreconf
apt-get clean autoclean
apt-get autoremove -y
rm -rf /var/lib/apt/lists/*
