package de.tum.in.www1.artemis.service.iris.session.codeeditor;

/**
 * An exception that occurs when Iris generates a change that cannot be applied for some reason.
 */
public class IrisChangeException extends Exception {

    public IrisChangeException(String message) {
        super(message);
    }
}
