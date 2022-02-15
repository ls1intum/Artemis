FROM ghcr.io/ls1intum/artemis-bamboo:7.2.5

LABEL description="Bamboo pre-configured for Artemis and C"

USER root
ENV DEBIAN_FRONTEND noninteractive
SHELL ["/bin/bash", "-o", "pipefail", "-c"]

# Install dependencies for C static analysis
RUN apt-get update && apt-get install -y --no-install-recommends \
    gcc-10=10.3* \
    libc6-dev=2.31* \
    python3=3.8* \
    python3-pip=20.0* \
    sudo=1.8* \
    && apt-get clean \
    && rm -rf /var/lib/apt/lists/*
RUN update-alternatives --install /usr/bin/gcc gcc /usr/bin/gcc-10 20
RUN update-alternatives --config gcc

# Add the artemis_user
COPY add_user.sh .
RUN bash ./add_user.sh

# Give the artemis_user sudo rights without a password by default
RUN echo "artemis_user     ALL=(ALL) NOPASSWD:ALL" > /etc/sudoers
RUN echo "${BAMBOO_USER}     ALL=(ALL) NOPASSWD:ALL" > /etc/sudoers

# We have to use the Bamboo User, otherwise it won't work, since Bamboo requires the user to be present.
# We can modify the tasks for C locally, by removing sudo, as BAMBOO_USER is already root.
# The part where the Makefile is being replaced can also be left out for local testing.
USER ${BAMBOO_USER}
