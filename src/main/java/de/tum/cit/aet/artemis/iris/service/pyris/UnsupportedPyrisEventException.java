package de.tum.cit.aet.artemis.iris.service.pyris;

/**
 * Exception thrown when an unsupported Pyris event is encountered.
 */
public class UnsupportedPyrisEventException extends RuntimeException {

    public UnsupportedPyrisEventException(String message) {
        super(message);
    }
}
