FROM jenkins/inbound-agent

USER root

RUN apt-get update && apt-get -y install curl

RUN curl https://download.docker.com/linux/static/stable/x86_64/docker-20.10.11.tgz | tar xvz --directory /tmp && mv -v /tmp/docker/docker /usr/local/bin/docker && chmod +x /usr/local/bin/docker && rm -rf /tmp/docker

USER jenkins
