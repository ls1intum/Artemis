package de.tum.in.www1.artemis.service.connectors.localci.scaparser.exception;

/**
 * Exception thrown if the static code analysis tool which created the report is not supported
 */
public class UnsupportedToolException extends RuntimeException {

    public UnsupportedToolException(String message) {
        super(message);
    }
}
