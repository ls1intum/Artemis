package de.tum.in.www1.artemis.exception;

/**
 * Created by muenchdo on 22/06/16.
 */
@Deprecated(forRemoval = true) // will be removed in 7.0.0
public class BambooException extends ContinuousIntegrationException {

    public BambooException() {
    }

    public BambooException(String message) {
        super(message);
    }

    public BambooException(Throwable cause) {
        super(cause);
    }

    public BambooException(String message, Throwable cause) {
        super(message, cause);
    }

}
