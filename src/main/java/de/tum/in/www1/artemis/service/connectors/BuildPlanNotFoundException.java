package de.tum.in.www1.artemis.service.connectors;

public class BuildPlanNotFoundException extends Exception {

    public BuildPlanNotFoundException() {
    }

    public BuildPlanNotFoundException(String message) {
        super(message);
    }

    public BuildPlanNotFoundException(Throwable cause) {
        super(cause);
    }

    public BuildPlanNotFoundException(String message, Throwable cause) {
        super(message, cause);
    }
}
