package de.tum.in.www1.artemis.exception;

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
