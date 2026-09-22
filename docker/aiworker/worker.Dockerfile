# Build from the repository root after :aiworker:bootJar.
FROM docker.io/library/eclipse-temurin:25.0.3_9-jdk@sha256:32861ec22e54af9597a3875c69001f57c0954648f5e3fcb6be601b4e35290ab5
WORKDIR /opt/aiworker
COPY --chown=1000:1000 modules/aiworker/build/libs/aiworker.jar worker.jar
USER 1000:1000
ENTRYPOINT ["java", "-jar", "/opt/aiworker/worker.jar"]
