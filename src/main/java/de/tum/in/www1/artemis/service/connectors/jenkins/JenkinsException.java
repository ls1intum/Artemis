package de.tum.in.www1.artemis.service.connectors.jenkins;

import de.tum.in.www1.artemis.exception.ContinuousIntegrationException;

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
