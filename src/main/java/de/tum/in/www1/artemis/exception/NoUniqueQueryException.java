package de.tum.in.www1.artemis.exception;

/**
 * Checked exception in case a query does not return a unique result, so calling methods must handle this case.
 */
public class NoUniqueQueryException extends Exception {

    public NoUniqueQueryException(String message) {
        super(message);
    }
}
