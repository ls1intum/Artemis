FROM docker.io/jenkins/jenkins:2.580.1-lts@sha256:a660310e39ade10631f774bacd5219de767dfd08947ea5c32a739b5e5bb382c1

LABEL description="Jenkins with plugins pre-installed for Artemis"

USER root

# Install third-party plugins required by Artemis
COPY plugins.yml /usr/share/jenkins/ref/plugins.yml
RUN jenkins-plugin-cli --plugin-file /usr/share/jenkins/ref/plugins.yml

# Disables the first-time setup wizard of Jenkins
# (Uncomment this line if you're using jenknis-casc-config.yml for auto-configuration)
# ENV JAVA_OPTS -Djenkins.install.runSetupWizard=false

# setup docker
# Docker's apt repository is added with a keyring file: Debian 13 (the base of the Jenkins 2.580 LTS image) has no apt-key,
# add-apt-repository or software-properties-common any more.
# Not pinning apt versions (DL3008): these are base-image system packages whose pinned versions age
# out of the Debian repos and would break the image build over time.
# hadolint ignore=DL3008
RUN apt-get update -y \
    && apt-get install -qqy --no-install-recommends ca-certificates curl gnupg2 \
    && install -m 0755 -d /etc/apt/keyrings \
    && curl -fsSL https://download.docker.com/linux/debian/gpg -o /etc/apt/keyrings/docker.asc \
    && chmod a+r /etc/apt/keyrings/docker.asc \
    && echo "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.asc] https://download.docker.com/linux/debian $(. /etc/os-release && echo "$VERSION_CODENAME") stable" > /etc/apt/sources.list.d/docker.list \
    && apt-get update -y \
    && apt-get -y install --no-install-recommends docker-ce \
    && rm -rf /var/lib/apt/lists/*
RUN usermod -aG docker jenkins

USER jenkins
