package de.tum.cit.aet.artemis.jenkins.exception;

import de.tum.cit.aet.artemis.programming.exception.ContinuousIntegrationException;

// REMOVE_IN_ARTEMIS_11 (Jenkins): Jenkins support is removed in Artemis 11, delete with the whole jenkins module
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
