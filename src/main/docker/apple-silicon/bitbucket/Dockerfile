ARG BASE_IMAGE=adoptopenjdk/openjdk11
FROM $BASE_IMAGE

LABEL maintainer="dc-deployments@atlassian.com"
LABEL securitytxt="https://www.atlassian.com/.well-known/security.txt"

ARG BITBUCKET_VERSION=7.17.1

ENV APP_NAME                                        bitbucket
ENV RUN_USER                                        bitbucket
ENV RUN_GROUP                                       bitbucket
ENV RUN_UID                                         2003
ENV RUN_GID                                         2003

# https://confluence.atlassian.com/display/BitbucketServer/Bitbucket+Server+home+directory
ENV BITBUCKET_HOME                                  /var/atlassian/application-data/bitbucket
ENV BITBUCKET_INSTALL_DIR                           /opt/atlassian/bitbucket
ENV ELASTICSEARCH_ENABLED                           true
ENV APPLICATION_MODE                                default
ENV JRE_HOME                                        /opt/java/openjdk
ENV JAVA_BINARY                                     ${JRE_HOME}/bin/java

WORKDIR $BITBUCKET_HOME

# Expose HTTP and SSH ports
EXPOSE 7990
EXPOSE 7999

CMD ["/entrypoint.py", "--log=INFO"]
ENTRYPOINT ["/usr/bin/tini", "--"]

RUN apt-get update
RUN apt-get upgrade -y
RUN apt-get install -y --no-install-recommends fontconfig openssh-client perl python3 python3-jinja2 tini
RUN apt-get clean autoclean
RUN apt-get autoremove -y

COPY make-git.sh make-git.sh
RUN chmod 700 make-git.sh
RUN ./make-git.sh

RUN git clone https://bitbucket.org/atlassian-docker/docker-atlassian-bitbucket-server/src/master/ ./git-repo
RUN git clone https://bitbucket.org/atlassian-docker/docker-shared-components.git ./shared-components

ARG DOWNLOAD_URL=https://product-downloads.atlassian.com/software/stash/downloads/atlassian-bitbucket-${BITBUCKET_VERSION}.tar.gz

RUN groupadd --gid ${RUN_GID} ${RUN_GROUP} \
    && useradd --uid ${RUN_UID} --gid ${RUN_GID} --home-dir ${BITBUCKET_HOME} --shell /bin/bash ${RUN_USER} \
    && echo PATH=$PATH > /etc/environment \
    \
    && mkdir -p                                     ${BITBUCKET_INSTALL_DIR} \
    && curl -L --silent                             ${DOWNLOAD_URL} | tar -xz --strip-components=1 -C "${BITBUCKET_INSTALL_DIR}" \
    && chmod -R "u=rwX,g=rX,o=rX"                   ${BITBUCKET_INSTALL_DIR}/ \
    && chown -R root.                               ${BITBUCKET_INSTALL_DIR}/ \
    && chown -R ${RUN_USER}:${RUN_GROUP}            ${BITBUCKET_INSTALL_DIR}/elasticsearch/logs \
    && chown -R ${RUN_USER}:${RUN_GROUP}            ${BITBUCKET_HOME}

VOLUME ["${BITBUCKET_HOME}"]

RUN cp ./git-repo/exec-bitbucket-node.sh ${BITBUCKET_INSTALL_DIR}/bin/
RUN cp ./git-repo/_exec-webapp.sh ${BITBUCKET_INSTALL_DIR}/bin/

RUN cp ./git-repo/entrypoint.py /
RUN cp -r ./shared-components/image/entrypoint_helpers.py /
RUN cp -r ./shared-components/support /opt/atlassian/support
RUN cp ./git-repo/shutdown-wait.sh /

RUN rm -rf ./git-repo
RUN rm -rf ./shared-components
