# Build the normal Artemis WAR first, then pass its exact path as ARTEMIS_WAR.
FROM docker.io/library/eclipse-temurin:25.0.3_9-jdk@sha256:32861ec22e54af9597a3875c69001f57c0954648f5e3fcb6be601b4e35290ab5
ARG ARTEMIS_WAR
WORKDIR /opt/aiworker
COPY --chown=1000:1000 ${ARTEMIS_WAR} worker.war
USER 1000:1000
ENTRYPOINT ["java", "-Dloader.main=de.tum.cit.aet.artemis.aiworker.config.AiWorkerApplication", "-Dloader.path=WEB-INF/classes,WEB-INF/lib,WEB-INF/lib-provided", "-cp", "/opt/aiworker/worker.war", "org.springframework.boot.loader.launch.PropertiesLauncher"]
