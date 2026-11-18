package de.tum.cit.aet.artemis.buildagent.service.runner;

/**
 * Supported LocalCI build execution mechanisms.
 */
public enum BuildRunnerType {

    DOCKER, KUBERNETES;

    public String displayName() {
        return switch (this) {
            case DOCKER -> "Docker";
            case KUBERNETES -> "Kubernetes";
        };
    }
}
