package de.tum.cit.aet.artemis.exception;

public class ContinuousIntegrationBuildPlanException extends RuntimeException {

    public ContinuousIntegrationBuildPlanException() {
    }

    public ContinuousIntegrationBuildPlanException(String message) {
        super(message);
    }

    public ContinuousIntegrationBuildPlanException(Throwable cause) {
        super(cause);
    }

    public ContinuousIntegrationBuildPlanException(String message, Throwable cause) {
        super(message, cause);
    }
}
