package de.tum.cit.aet.artemis.iris.service.pyris;

/**
 * Exception thrown when an error occurs during Pyris event processing.
 */
public class PyrisEventProcessingException extends RuntimeException {

    public PyrisEventProcessingException(String message) {
        super(message);
    }
}
