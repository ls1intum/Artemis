package de.tum.in.www1.artemis.exception;

public class ContinousIntegrationBuildPlanException extends RuntimeException {

    public ContinousIntegrationBuildPlanException() {
    }

    public ContinousIntegrationBuildPlanException(String message) {
        super(message);
    }

    public ContinousIntegrationBuildPlanException(Throwable cause) {
        super(cause);
    }

    public ContinousIntegrationBuildPlanException(String message, Throwable cause) {
        super(message, cause);
    }
}
