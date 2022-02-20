FROM jenkins/jenkins:lts

LABEL description="Jenkins with plugins pre-installed for Artemis"

USER root

RUN apt update

# Install third-party plugins required by Artemis
COPY plugins.yml /usr/share/jenkins/ref/plugins.yml
RUN jenkins-plugin-cli --plugin-file /usr/share/jenkins/ref/plugins.yml

# Install docker client (if you want a local build agent)
# (Uncomment this line if you want to use a local docker build agent e.g for development purposes)
# RUN curl https://download.docker.com/linux/static/stable/x86_64/docker-19.03.8.tgz | tar xvz --directory /tmp && mv -v /tmp/docker/docker /usr/local/bin/docker && chmod +x /usr/local/bin/docker && rm -rf /tmp/docker

# Disables the first-time setup wizard of Jenkins
# (Uncomment this line if you're using jenknis-casc-config.yml for auto-configuration)
# ENV JAVA_OPTS -Djenkins.install.runSetupWizard=false

USER jenkins
