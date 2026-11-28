package de.tum.cit.aet.artemis.iris.service.pyris;

/**
 * Pyris could not be reached or did not answer in time. Unlike other {@link PyrisConnectorException}s, which can come from an answer Artemis cannot use,
 * this one is an outage of another service that callers may answer as such.
 */
public class PyrisUnavailableException extends PyrisConnectorException {

    public PyrisUnavailableException(String message) {
        super(message);
    }
}
