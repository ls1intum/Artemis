package de.tum.cit.aet.artemis.jenkins.exception;

// REMOVE_IN_ARTEMIS_11 (Jenkins): only thrown by the Jenkins build plan code, remove with the jenkins module
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
