package de.tum.cit.aet.artemis.programming.service.localci.scaparser.exception;

/**
 * Exception thrown if the static code analysis tool which created the report is not supported
 */
public class UnsupportedToolException extends RuntimeException {

    /**
     * Creates a new UnsupportedToolException
     */
    public UnsupportedToolException(String message) {
        super(message);
    }
}
