package de.tum.in.www1.artemis.service.connectors.localci.scaparser.exception;

/**
 * Exception thrown when a static code analysis report could not be parsed
 */
public class ParserException extends Exception {

    public ParserException(String message, Throwable cause) {
        super(message, cause);
    }
}
