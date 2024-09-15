package de.tum.cit.aet.artemis.core.exception;

public class JenkinsException extends ContinuousIntegrationException {

    public JenkinsException() {
    }

    public JenkinsException(String message) {
        super(message);
    }

    public JenkinsException(Throwable cause) {
        super(cause);
    }

    public JenkinsException(String message, Throwable cause) {
        super(message, cause);
    }
}
