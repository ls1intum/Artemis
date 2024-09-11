package de.tum.cit.aet.artemis.service.connectors.localci.scaparser.exception;

/**
 * Exception thrown when an error occurs during parsing.
 */
public class ParserException extends Exception {

    /**
     * Creates a new ParserException.
     *
     * @param message the detail message.
     */
    public ParserException(String message, Throwable cause) {
        super(message, cause);
    }
}
