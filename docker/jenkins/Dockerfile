FROM docker.io/jenkins/jenkins:lts@sha256:a660310e39ade10631f774bacd5219de767dfd08947ea5c32a739b5e5bb382c1

LABEL description="Jenkins with plugins pre-installed for Artemis"

USER root

# Install third-party plugins required by Artemis
COPY plugins.yml /usr/share/jenkins/ref/plugins.yml
RUN jenkins-plugin-cli --plugin-file /usr/share/jenkins/ref/plugins.yml

# Disables the first-time setup wizard of Jenkins
# (Uncomment this line if you're using jenknis-casc-config.yml for auto-configuration)
# ENV JAVA_OPTS -Djenkins.install.runSetupWizard=false

# setup docker
# Not pinning apt versions (DL3008): these are base-image system packages whose pinned versions age
# out of the Debian repos and would break the image build over time.
# hadolint ignore=DL3008
RUN apt-get update -y \
    && apt-get install -qqy --no-install-recommends apt-transport-https ca-certificates curl gnupg2 software-properties-common \
    && rm -rf /var/lib/apt/lists/*
RUN curl -fsSL https://download.docker.com/linux/debian/gpg | apt-key add -
RUN add-apt-repository \
   "deb [arch=$(dpkg --print-architecture)] https://download.docker.com/linux/debian \
   $(lsb_release -cs) \
   stable"
# hadolint ignore=DL3008
RUN apt-get update -y \
    && apt-get -y install --no-install-recommends docker-ce && rm -rf /var/lib/apt/lists/*
RUN usermod -aG docker jenkins

USER jenkins
