ARG BASE_IMAGE=adoptopenjdk/openjdk11
FROM $BASE_IMAGE

LABEL maintainer="dc-deployments@atlassian.com"
LABEL securitytxt="https://www.atlassian.com/.well-known/security.txt"

ENV APP_NAME                                        jira
ENV RUN_USER                                        jira
ENV RUN_GROUP                                       jira
ENV RUN_UID                                         2001
ENV RUN_GID                                         2001

# https://confluence.atlassian.com/display/JSERVERM/Important+directories+and+files
ENV JIRA_HOME                                       /var/atlassian/application-data/jira
ENV JIRA_INSTALL_DIR                                /opt/atlassian/jira

WORKDIR $JIRA_HOME

# Expose HTTP port
EXPOSE 8080

CMD ["/entrypoint.py"]
ENTRYPOINT ["/usr/bin/tini", "--"]

RUN apt-get update \
    && apt-get upgrade -y \
    && apt-get install -y --no-install-recommends fontconfig python3 python3-jinja2 tini git \
    && apt-get clean autoclean && apt-get autoremove -y && rm -rf /var/lib/apt/lists/*

ARG JIRA_VERSION=8.15.0
ARG ARTEFACT_NAME=atlassian-jira-software
ARG DOWNLOAD_URL=https://product-downloads.atlassian.com/software/jira/downloads/${ARTEFACT_NAME}-${JIRA_VERSION}.tar.gz

RUN groupadd --gid ${RUN_GID} ${RUN_GROUP} \
    && useradd --uid ${RUN_UID} --gid ${RUN_GID} --home-dir ${JIRA_HOME} --shell /bin/bash ${RUN_USER} \
    && echo PATH=$PATH > /etc/environment \
    && mkdir -p ${JIRA_INSTALL_DIR} \
    \
    && curl -Ls ${DOWNLOAD_URL} | tar -xz --strip-components=1 -C "${JIRA_INSTALL_DIR}" \
    \
    && chmod -R "u=rwX,g=rX,o=rX"                      ${JIRA_INSTALL_DIR}/ \
    && chown -R root.                               ${JIRA_INSTALL_DIR}/ \
    && chown -R ${RUN_USER}:${RUN_GROUP}            ${JIRA_INSTALL_DIR}/logs \
    && chown -R ${RUN_USER}:${RUN_GROUP}            ${JIRA_INSTALL_DIR}/temp \
    && chown -R ${RUN_USER}:${RUN_GROUP}            ${JIRA_INSTALL_DIR}/work \
    \
    && sed -i -e 's/^JVM_SUPPORT_RECOMMENDED_ARGS=""$/: \${JVM_SUPPORT_RECOMMENDED_ARGS:=""}/g' ${JIRA_INSTALL_DIR}/bin/setenv.sh \
    && sed -i -e 's/^JVM_\(.*\)_MEMORY="\(.*\)"$/: \${JVM_\1_MEMORY:=\2}/g' ${JIRA_INSTALL_DIR}/bin/setenv.sh \
    && sed -i -e 's/-XX:ReservedCodeCacheSize=\([0-9]\+[kmg]\)/-XX:ReservedCodeCacheSize=${JVM_RESERVED_CODE_CACHE_SIZE:=\1}/g' ${JIRA_INSTALL_DIR}/bin/setenv.sh \
    \
    && touch /etc/container_id \
    && chown ${RUN_USER}:${RUN_GROUP}               /etc/container_id \
    && chown -R ${RUN_USER}:${RUN_GROUP}            ${JIRA_HOME}

VOLUME ["${JIRA_HOME}"]

RUN git clone https://bitbucket.org/atlassian-docker/docker-atlassian-jira/src/master/ ./git-repo
RUN git clone https://bitbucket.org/atlassian-docker/docker-shared-components.git ./shared-components

RUN cp ./git-repo/entrypoint.py /
RUN cp ./git-repo/shutdown-wait.sh /
RUN cp ./shared-components/image/entrypoint_helpers.py  /
RUN mkdir /opt/atlassian/support
RUN cp -r ./shared-components/support /opt/atlassian/support
RUN mkdir /opt/atlassian/etc
RUN cp -r ./git-repo/config/* /opt/atlassian/etc/
