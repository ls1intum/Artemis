package de.tum.in.www1.artemis.service.connectors.lti;

public class LtiVerificationException extends Exception {

    public LtiVerificationException(String message, Exception exception) {
        super(message, exception);
    }
}
