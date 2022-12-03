package de.tum.in.www1.artemis.service.connectors.lti;

public class LtiError {

    private final String label;

    public static final LtiError BAD_REQUEST = new LtiError("bad_request");

    private LtiError(String label) {
        this.label = label;
    }

    public String toString() {
        return label;
    }

}
