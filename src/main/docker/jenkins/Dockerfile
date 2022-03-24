FROM jenkins/jenkins:lts

LABEL description="Jenkins with plugins pre-installed for Artemis"

USER root

RUN apt-get update

# Install third-party plugins required by Artemis
COPY plugins.yml /usr/share/jenkins/ref/plugins.yml
RUN jenkins-plugin-cli --plugin-file /usr/share/jenkins/ref/plugins.yml

# Disables the first-time setup wizard of Jenkins
# (Uncomment this line if you're using jenknis-casc-config.yml for auto-configuration)
# ENV JAVA_OPTS -Djenkins.install.runSetupWizard=false

USER jenkins
